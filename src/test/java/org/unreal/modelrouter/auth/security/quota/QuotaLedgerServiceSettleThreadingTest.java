package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配额结算的线程语义回归测试（issue #105）。
 *
 * <p>缺陷背景：{@code settle} 的调用方位于 WebClient / Netty 的信号回调
 * （{@code doOnError} / {@code doOnCancel} / {@code doOnComplete}），即 Reactor 非阻塞线程；
 * 分布式路径在该线程上执行 {@code Mono.block()} 会被 Reactor 直接拒绝，异常被“绝不抛出”契约吞掉，
 * 结果是 <b>Redis 冲正量永不写入</b>、分布式计数持续漂移。修复后 {@code settle} 在非阻塞线程上
 * 把工作整体交给 {@code boundedElastic}（fire-and-forget），在普通线程上仍内联执行。</p>
 *
 * <p>被测对象是真实 {@link QuotaLedgerService} + 真实 {@link RedisCounterBackend} + 手写假 Redis
 * （{@link FakeReactiveRedisTemplate}）+ 内存仓库（{@link InMemoryQuotaLedgerRepository}），
 * 不使用 Mockito；等待异步补写使用有界轮询（仓库未引入 awaitility 等依赖）。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaLedgerService 结算线程语义（issue #105）")
class QuotaLedgerServiceSettleThreadingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 异步补写的等待上限（毫秒） */
    private static final long AWAIT_TIMEOUT_MILLIS = 5000L;

    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties properties;
    private FakeReactiveRedisTemplate template;
    private RedisCounterBackend redisBackend;

    @BeforeEach
    void setUp() {
        repository = InMemoryQuotaLedgerRepository.create();
        properties = new QuotaProperties();
        properties.setEnabled(true);
        properties.getDistributed().setEnabled(true);
        properties.getDistributed().setTimeout(Duration.ofMillis(200));
        template = new FakeReactiveRedisTemplate();
        redisBackend = new RedisCounterBackend(template, properties);
    }

    @Test
    @DisplayName("非阻塞线程上调用 settle：立即返回、不抛出，冲正量最终补写到 Redis")
    void settleOnNonBlockingThread_shouldPushAdjustmentEventually() throws Exception {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 100));
        final String redisKey = redisKey(dimension, QuotaWindow.MINUTE);
        assertArrayEquals(new long[]{1L, 100L}, template.values(redisKey), "预留先写入估算值");

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean nonBlocking = new AtomicBoolean();
        final CountDownLatch done = new CountDownLatch(1);
        Schedulers.parallel().schedule(() -> {
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            try {
                service.settle(new QuotaSettlement(dimension, 100, 60, false));
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            "settle 在非阻塞线程上必须立即返回（不得在事件循环上 block 等待 Redis）");
        assertTrue(nonBlocking.get(), "前置条件：parallel 调度器线程属 Reactor 非阻塞线程");
        assertNull(error.get(), "settle 的“绝不抛出”契约在非阻塞线程路径上同样成立");

        // 移交后在 boundedElastic 上补写：有界等待最终一致性
        awaitCounters(redisKey, 1L, 60L);
        assertEquals(60L, usageOf(service, dimension, QuotaWindow.MINUTE).tokenCount());
    }

    @Test
    @DisplayName("普通（阻塞）线程上调用 settle：同步完成，返回时冲正量已写入 Redis")
    void settleOnBlockingThread_shouldCompleteSynchronously() throws Exception {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 100));
        final String redisKey = redisKey(dimension, QuotaWindow.MINUTE);

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean nonBlocking = new AtomicBoolean(true);
        final Thread worker = new Thread(() -> {
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            try {
                service.settle(new QuotaSettlement(dimension, 100, 60, false));
            } catch (Throwable t) {
                error.set(t);
            }
        }, "quota-settle-plain-thread");
        worker.start();
        worker.join(AWAIT_TIMEOUT_MILLIS);

        assertFalse(worker.isAlive(), "普通线程上的 settle 必须同步完成");
        assertFalse(nonBlocking.get(), "前置条件：普通线程不是 Reactor 非阻塞线程");
        assertNull(error.get());
        assertArrayEquals(new long[]{1L, 60L}, template.values(redisKey),
            "内联路径：settle 返回即冲正已落 Redis（流式 / 非流式结算测试依赖该确定性）");
    }

    @Test
    @DisplayName("守卫：非阻塞线程上的账本读抛出指明线程的 ISE，并被 classify 归类为 REASON_REDIS_UNAVAILABLE")
    void executeRedisOnNonBlockingThread_shouldFailFastWithThreadName() throws Exception {
        // degrade-to-local=false：让 usageStrict 把守卫异常抛出来（默认会折叠成降级读数）
        properties.getDistributed().setDegradeToLocal(false);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<String> threadName = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Schedulers.parallel().schedule(() -> {
            threadName.set(Thread.currentThread().getName());
            try {
                service.usageStrict(dimension, QuotaWindow.DAY);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), "守卫必须立即失败，不得真的去 block");
        final Throwable thrown = error.get();
        assertNotNull(thrown, "非阻塞线程上的阻塞调用必须显式失败");
        assertTrue(thrown instanceof IllegalStateException, "异常类型: " + thrown.getClass());
        assertTrue(thrown.getMessage().contains(QuotaLedgerService.REASON_REDIS_UNAVAILABLE),
            "守卫异常应被 classify 归类为 Redis 不可用: " + thrown.getMessage());

        final Throwable cause = thrown.getCause();
        assertNotNull(cause, "usageStrict 应保留守卫异常作为原因");
        assertTrue(cause.getMessage().contains(threadName.get()),
            "守卫异常应指明当前线程: " + cause.getMessage());
        assertTrue(cause.getMessage().contains("非阻塞线程"), "守卫异常应说明失败原因: " + cause.getMessage());
    }

    @Test
    @DisplayName("守卫异常走既有降级契约：usage 不抛出、折叠为本地视图并标记降级原因")
    void guardFailure_shouldDegradeWithActionableReason() throws Exception {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 30));

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Optional<QuotaUsage>> usage = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Schedulers.parallel().schedule(() -> {
            try {
                usage.set(service.usage(dimension, QuotaWindow.DAY));
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        assertNull(error.get(), "usage 沿用“绝不抛出”契约");
        assertTrue(service.isDegraded());
        assertEquals(QuotaLedgerService.REASON_REDIS_UNAVAILABLE, service.degradedReason(),
            "守卫异常必须给出可定位的降级原因，而不是静默丢失");
        assertEquals(1L, usage.get().orElseThrow().requestCount(), "降级读数回退本地视图");
    }

    // ==================== 辅助 ====================

    /**
     * 构造账本服务（分布式模式，共用假 Redis 与内存仓库，时钟固定）.
     *
     * @return 账本服务
     */
    private QuotaLedgerService service() {
        return new QuotaLedgerService(repository.proxy(), properties,
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), redisBackend, QuotaCounterMetrics.noop());
    }

    /**
     * 计算指定维度的 Redis 计数键.
     *
     * @param dimension 维度
     * @param window    窗口
     * @return Redis key
     */
    private String redisKey(final QuotaDimension dimension, final QuotaWindow window) {
        return redisBackend.keyOf(new QuotaCounterKey(dimension, window, window.windowStart(NOW)));
    }

    /**
     * 取指定窗口的用量（无数据直接失败，便于断言）.
     *
     * @param service   账本服务
     * @param dimension 维度
     * @param window    窗口
     * @return 用量
     */
    private static QuotaUsage usageOf(final QuotaLedgerService service,
                                      final QuotaDimension dimension,
                                      final QuotaWindow window) {
        return service.usage(dimension, window)
            .orElseThrow(() -> new IllegalStateException("账本无数据: " + dimension + " / " + window));
    }

    /**
     * 有界等待 Redis 计数桶达到期望值（仓库未引入 awaitility，按既有做法轮询）.
     *
     * @param redisKey      Redis key
     * @param requests      期望请求数
     * @param tokens        期望 token 数
     * @throws InterruptedException 等待被中断
     */
    private void awaitCounters(final String redisKey,
                               final long requests,
                               final long tokens) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_TIMEOUT_MILLIS);
        long[] current = template.values(redisKey);
        while ((current == null || current[0] != requests || current[1] != tokens)
                && System.nanoTime() < deadline) {
            Thread.sleep(5L);
            current = template.values(redisKey);
        }
        assertArrayEquals(new long[]{requests, tokens}, current,
            "结算增量必须在 " + AWAIT_TIMEOUT_MILLIS + "ms 内补写到 Redis，实际 "
                + (current == null ? "null" : Arrays.toString(current)));
    }
}

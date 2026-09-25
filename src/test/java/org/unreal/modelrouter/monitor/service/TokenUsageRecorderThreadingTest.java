package org.unreal.modelrouter.monitor.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token 落库的线程语义回归测试（issue #115）。
 *
 * <p>缺陷背景：{@code recordTokenUsageNoAuth} 的调用方位于流式 {@code doOnComplete} 等
 * Reactor 非阻塞线程（Netty 事件循环），而内部 {@code tokenUsageService.recordTokenUsage}
 * 是 {@code @Transactional} + JPA {@code save} 的阻塞写入，会把 IO 线程钉死在数据库往返上。
 * 修复后在非阻塞线程上把工作整体交给 {@code boundedElastic}（fire-and-forget），
 * 在普通线程上仍内联执行（返回即已落库）。</p>
 *
 * <p>被测对象是真实 {@link TokenUsageRecorder} + 手写存根 {@link CapturingTokenUsageService}
 * （仅捕获执行线程），不使用 Mockito；异步补写使用有界 {@link CountDownLatch} 等待。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("TokenUsageRecorder 落库线程语义（issue #115）")
class TokenUsageRecorderThreadingTest {

    /** 异步落库的等待上限（毫秒） */
    private static final long AWAIT_TIMEOUT_MILLIS = 5000L;

    /**
     * 手写存根：捕获 {@code recordTokenUsage} 的执行线程与是否处于 Reactor 非阻塞线程。
     */
    private static final class CapturingTokenUsageService extends TokenUsageService {

        private final AtomicReference<String> threadName = new AtomicReference<>();
        private final AtomicBoolean nonBlocking = new AtomicBoolean();
        private final AtomicReference<TokenUsageRecordDTO> captured = new AtomicReference<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        CapturingTokenUsageService() {
            super(null);
        }

        @Override
        public void recordTokenUsage(final TokenUsageRecordDTO record) {
            threadName.set(Thread.currentThread().getName());
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            captured.set(record);
            latch.countDown();
        }
    }

    @Test
    @DisplayName("非阻塞线程上调用 recordTokenUsageNoAuth：JPA 落库不得在该线程上执行")
    void recordTokenUsageNoAuthOnNonBlockingThread_shouldRecordOffEventLoop() throws Exception {
        final CapturingTokenUsageService service = new CapturingTokenUsageService();
        final TokenUsageRecorder recorder = new TokenUsageRecorder(service);

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean callerNonBlocking = new AtomicBoolean();
        final CountDownLatch returned = new CountDownLatch(1);
        Mono.fromRunnable(() -> {
            callerNonBlocking.set(Schedulers.isInNonBlockingThread());
            try {
                recorder.recordTokenUsageNoAuth(
                        "CHAT", "gpt-4", "openai", "instance-1", null,
                        10L, 20L, 30L, "trace-1", null, true, null, null, null);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                returned.countDown();
            }
        }).subscribeOn(Schedulers.parallel()).subscribe();

        assertTrue(returned.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "recordTokenUsageNoAuth 在非阻塞线程上必须立即返回（不得同步等待 JPA）");
        assertTrue(callerNonBlocking.get(), "前置条件：parallel 调度器线程属 Reactor 非阻塞线程");
        assertNull(error.get(), "“绝不抛出”契约在非阻塞线程路径上同样成立");

        assertTrue(service.latch.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "落库必须最终在 boundedElastic 上完成");
        assertFalse(service.nonBlocking.get(),
                "JPA 写入不得在 Reactor 非阻塞线程（事件循环）上执行");
        assertTrue(service.threadName.get().contains("boundedElastic"),
                "落库应在 boundedElastic 线程上执行，实际: " + service.threadName.get());
        assertEquals("gpt-4", service.captured.get().getModelName(), "载荷与同步实现一致");
    }

    @Test
    @DisplayName("普通线程上调用 recordTokenUsageNoAuth：同步完成，返回时已落库")
    void recordTokenUsageNoAuthOnPlainThread_shouldRecordInline() throws Exception {
        final CapturingTokenUsageService service = new CapturingTokenUsageService();
        final TokenUsageRecorder recorder = new TokenUsageRecorder(service);

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean nonBlocking = new AtomicBoolean(true);
        final AtomicReference<String> callerThread = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            callerThread.set(Thread.currentThread().getName());
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            try {
                recorder.recordTokenUsageNoAuth(
                        "CHAT", "gpt-4", "openai", "instance-1", null,
                        10L, 20L, 30L, "trace-1", null, true, null, null, null);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "token-usage-plain-thread");
        worker.start();
        worker.join(AWAIT_TIMEOUT_MILLIS);

        assertFalse(worker.isAlive(), "普通线程上的 recordTokenUsageNoAuth 必须同步完成");
        assertFalse(nonBlocking.get(), "前置条件：普通线程不是 Reactor 非阻塞线程");
        assertNull(error.get());
        assertEquals(0L, service.latch.getCount(),
                "内联路径：方法返回即已落库（既有测试依赖该确定性）");
        assertEquals(callerThread.get(), service.threadName.get(),
                "内联路径：落库应在调用线程上执行");
    }
}

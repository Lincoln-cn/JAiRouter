package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多窗口限额预留的冲正语义（issue #120）。
 *
 * <p>缺陷背景：{@code reserveWithLimits} 逐窗口 CAS 累加，后续窗口（如 MINUTE）超限被拒时
 * 直接返回违规结果，前面窗口（如 DAY）已提交的增量无人冲正——因为
 * {@code tryReserve} 对违规结果不挂载预留凭据，下游 {@code settleFailure} 不会触发。
 * 被拒请求因此永久消耗 DAY 配额直到窗口过期。修复后在拒绝前对已提交窗口逐个撤销本笔增量。</p>
 *
 * <p>被测对象是真实 {@link QuotaLedgerService}（本地槽位锁 + 假 Redis Lua）+
 * 内存仓库（{@link InMemoryQuotaLedgerRepository}），不使用 Mockito。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaLedgerService 多窗口预留冲正（issue #120）")
class QuotaLedgerServiceMultiWindowReserveTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Duration WAIT = Duration.ofSeconds(10);

    private static final QuotaDimension DIMENSION = QuotaDimension.ofApiKey("key-multi");

    /** 预留的估算 token 数（冲正时必须按同一数值回退） */
    private static final long TOKENS = 7L;

    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties properties;

    @BeforeEach
    void setUp() {
        repository = InMemoryQuotaLedgerRepository.create();
        properties = new QuotaProperties();
        properties.setEnabled(true);
        // 用例需要“DAY 先提交、MINUTE 后被拒”的顺序，按配置顺序显式声明
        properties.setWindows(List.of(QuotaWindow.DAY, QuotaWindow.MINUTE));
    }

    @Test
    @DisplayName("MINUTE 超限拒绝：已提交的 DAY 计数必须回到预留前（请求与 token 均不残留）")
    void laterWindowRejection_shouldRollbackEarlierWindow() {
        final QuotaLedgerService service = localService();
        // 前置状态：DAY 已有 3 笔历史用量（只开 DAY 窗口写入，避免污染 MINUTE）
        properties.setWindows(List.of(QuotaWindow.DAY));
        for (int i = 0; i < 3; i++) {
            service.reserve(QuotaRequest.of(DIMENSION, TOKENS));
        }
        properties.setWindows(List.of(QuotaWindow.DAY, QuotaWindow.MINUTE));
        final long dayRequestsBefore = usageOf(service, QuotaWindow.DAY).requestCount();
        final long dayTokensBefore = usageOf(service, QuotaWindow.DAY).tokenCount();
        assertEquals(3L, dayRequestsBefore);

        // MINUTE 限额=1：第一笔占用后，第二笔 DAY 先提交、MINUTE 被拒
        assertFalse(service.reserveWithLimits(QuotaRequest.of(DIMENSION, TOKENS), 0L, 0L, 1L).isPresent());
        final long dayRequestsAfterAllow = usageOf(service, QuotaWindow.DAY).requestCount();
        final long dayTokensAfterAllow = usageOf(service, QuotaWindow.DAY).tokenCount();
        assertEquals(dayRequestsBefore + 1L, dayRequestsAfterAllow, "放行的一笔应记入 DAY");
        assertEquals(dayTokensBefore + TOKENS, dayTokensAfterAllow, "放行的一笔应记入 DAY token");

        final Optional<QuotaLimitViolation> rejected =
                service.reserveWithLimits(QuotaRequest.of(DIMENSION, TOKENS), 0L, 0L, 1L);
        assertTrue(rejected.isPresent(), "MINUTE 已满必须拒绝");
        assertEquals(QuotaWindow.MINUTE, rejected.get().window());

        assertEquals(dayRequestsAfterAllow, usageOf(service, QuotaWindow.DAY).requestCount(),
                "被拒请求不得残留 DAY 请求数");
        assertEquals(dayTokensAfterAllow, usageOf(service, QuotaWindow.DAY).tokenCount(),
                "被拒请求不得残留 DAY token");
        assertEquals(1L, usageOf(service, QuotaWindow.MINUTE).requestCount(),
                "MINUTE 只保留被放行的 1 笔");
    }

    @Test
    @DisplayName("分布式：MINUTE 超限拒绝后 Redis DAY 计数同样回到预留前")
    void laterWindowRejection_shouldRollbackRedisCounters() {
        final FakeReactiveRedisTemplate template = new FakeReactiveRedisTemplate();
        properties.getDistributed().setEnabled(true);
        final RedisCounterBackend redisBackend = new RedisCounterBackend(template, properties);
        final QuotaLedgerService service = new QuotaLedgerService(repository.proxy(), properties,
                fixedClock(), redisBackend, QuotaCounterMetrics.noop());

        assertFalse(service.reserveWithLimits(QuotaRequest.of(DIMENSION, TOKENS), 0L, 0L, 1L).isPresent());
        final Optional<QuotaLimitViolation> rejected =
                service.reserveWithLimits(QuotaRequest.of(DIMENSION, TOKENS), 0L, 0L, 1L);
        assertTrue(rejected.isPresent(), "MINUTE 已满必须拒绝");

        final long[] dayValues = template.values(redisBackend.keyOf(keyOf(QuotaWindow.DAY)));
        assertEquals(1L, dayValues[0], "Redis DAY 请求数只保留放行的 1 笔，不得残留被拒请求");
        assertEquals(TOKENS, dayValues[1], "Redis DAY token 不得残留被拒请求的估算量");
        final long[] minuteValues = template.values(redisBackend.keyOf(keyOf(QuotaWindow.MINUTE)));
        assertEquals(1L, minuteValues[0]);
    }

    @Test
    @DisplayName("并发突发：MINUTE 限额=3 时放行数恰为 3，且 DAY 无残量、不超放")
    void concurrentBurst_shouldNotLeaveResidueNorOverAdmit() {
        final QuotaLedgerService service = localService();
        final int burst = 24;
        final int minuteLimit = 3;

        StepVerifier.create(Flux.range(0, burst)
                        .flatMap(index -> Mono.fromCallable(() ->
                                        service.reserveWithLimits(QuotaRequest.of(DIMENSION, TOKENS),
                                                0L, 0L, minuteLimit).isEmpty())
                                .subscribeOn(Schedulers.boundedElastic()), burst)
                        .collectList())
                .assertNext(results -> {
                    final long allowed = results.stream().filter(Boolean::booleanValue).count();
                    assertTrue(allowed <= minuteLimit,
                            "放行数不得越过 MINUTE 限额，实际放行 " + allowed);
                    assertEquals(minuteLimit, allowed,
                            "限额未耗尽前应全部放行，实际放行 " + allowed);
                    assertEquals(allowed, usageOf(service, QuotaWindow.MINUTE).requestCount(),
                            "MINUTE 不得超放");
                    assertEquals(allowed, usageOf(service, QuotaWindow.DAY).requestCount(),
                            "DAY 不得残留被拒请求的请求数");
                    assertEquals(allowed * TOKENS, usageOf(service, QuotaWindow.DAY).tokenCount(),
                            "DAY 不得残留被拒请求的 token 数");
                })
                .expectComplete()
                .verify(WAIT);
    }

    // ==================== 辅助 ====================

    /**
     * 构造本地模式账本服务（固定时钟）。
     *
     * @return 账本服务
     */
    private QuotaLedgerService localService() {
        return new QuotaLedgerService(repository.proxy(), properties, fixedClock());
    }

    /**
     * 固定时钟（与既有用例同一时刻）。
     *
     * @return 时钟
     */
    private static Clock fixedClock() {
        return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    }

    /**
     * 计算指定窗口的计数键。
     *
     * @param window 窗口
     * @return 计数键
     */
    private static QuotaCounterKey keyOf(final QuotaWindow window) {
        return new QuotaCounterKey(DIMENSION, window, window.windowStart(NOW));
    }

    /**
     * 取指定窗口用量（无数据直接失败）。
     *
     * @param service 账本服务
     * @param window  窗口
     * @return 用量
     */
    private static QuotaUsage usageOf(final QuotaLedgerService service, final QuotaWindow window) {
        return service.usage(DIMENSION, window)
                .orElseThrow(() -> new IllegalStateException("账本无数据: " + window));
    }
}

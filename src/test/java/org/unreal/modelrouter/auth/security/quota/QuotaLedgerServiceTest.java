package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuotaLedgerService} 单元测试（v3.1 PR-1）。
 *
 * <p>使用真实服务实现 + 手写内存仓库（{@link InMemoryQuotaLedgerRepository}），
 * 不使用 Mockito mock；时钟可控（{@link MutableClock}）以便验证窗口边界与保留期清理。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaLedgerService 配额账本测试")
class QuotaLedgerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties properties;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        repository = InMemoryQuotaLedgerRepository.create();
        properties = new QuotaProperties();
        properties.setEnabled(true);
        clock = new MutableClock(NOW, ZONE);
    }

    @Test
    @DisplayName("默认关闭：enabled=false 时全部接口零副作用、零数据库访问")
    void disabled_shouldBehaveAsNoOp() {
        properties.setEnabled(false);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        assertFalse(service.isEnabled());
        final QuotaDecision decision = service.reserve(QuotaRequest.of(dimension, 10));
        assertTrue(decision.allowed());
        assertFalse(decision.degraded());
        assertEquals(QuotaDecision.REASON_DISABLED, decision.reason());

        service.settle(new QuotaSettlement(dimension, 10, 5, true));
        assertEquals(Optional.empty(), service.usage(dimension, QuotaWindow.DAY));
        assertEquals(List.of(), service.usageAll("key-1"));
        assertEquals(0, service.flush());
        assertEquals(0, service.cleanupExpired());
        assertEquals(0, service.reset("key-1"));
        service.shutdown();

        assertEquals(0, repository.invocationCount(), "未启用时不得访问数据库");
        assertTrue(repository.rows().isEmpty());
    }

    @Test
    @DisplayName("reserve：对四级窗口同时累加请求数与 token")
    void reserve_shouldAccumulateAllEnabledWindows() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        final QuotaDecision decision = service.reserve(QuotaRequest.of(dimension, 120));

        assertTrue(decision.allowed());
        assertFalse(decision.degraded());
        assertEquals(QuotaDecision.REASON_OK, decision.reason());
        for (final QuotaWindow window : QuotaWindow.values()) {
            final QuotaUsage usage = usageOf(service, dimension, window);
            assertEquals(1L, usage.requestCount(), "窗口 " + window + " 请求数");
            assertEquals(120L, usage.tokenCount(), "窗口 " + window + " token 数");
            assertEquals(window.windowStart(NOW), usage.windowStart());
        }
    }

    @Test
    @DisplayName("维度隔离：不同维度独立计数，不互相污染")
    void reserve_shouldIsolateDimensions() {
        final QuotaLedgerService service = service();
        final QuotaDimension byModelA = new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-a");
        final QuotaDimension byModelB = new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-b");
        final QuotaDimension byService = new QuotaDimension("t1", "key-1", "u1", "embedding", "gpt-a");
        final QuotaDimension byUser = new QuotaDimension("t1", "key-1", "u2", "chat", "gpt-a");
        final QuotaDimension byTenant = new QuotaDimension("t2", "key-1", "u1", "chat", "gpt-a");

        service.reserve(QuotaRequest.of(byModelA, 100));
        service.reserve(QuotaRequest.of(byModelA, 50));
        service.reserve(QuotaRequest.of(byModelB, 7));
        service.reserve(QuotaRequest.of(byService));
        service.reserve(QuotaRequest.of(byUser, 1));
        service.reserve(QuotaRequest.of(byTenant, 1));

        assertEquals(2L, usageOf(service, byModelA, QuotaWindow.DAY).requestCount());
        assertEquals(150L, usageOf(service, byModelA, QuotaWindow.DAY).tokenCount());
        assertEquals(1L, usageOf(service, byModelB, QuotaWindow.DAY).requestCount());
        assertEquals(7L, usageOf(service, byModelB, QuotaWindow.DAY).tokenCount());
        assertEquals(1L, usageOf(service, byService, QuotaWindow.DAY).requestCount());
        assertEquals(0L, usageOf(service, byService, QuotaWindow.DAY).tokenCount());
        assertEquals(1L, usageOf(service, byUser, QuotaWindow.DAY).requestCount());
        assertEquals(1L, usageOf(service, byTenant, QuotaWindow.DAY).requestCount());
        // 5 个维度（同一 API Key）× 4 个窗口，各维度互不干扰
        assertEquals(20, service.usageAll("key-1").size());
    }

    @Test
    @DisplayName("并发 reserve：LongAdder 累加结果精确无丢失")
    void concurrentReserve_shouldCountExactly() throws Exception {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-concurrent");
        final int threads = 8;
        final int perThread = 250;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        service.reserve(QuotaRequest.of(dimension, 3));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        final long expectedRequests = (long) threads * perThread;
        for (final QuotaWindow window : QuotaWindow.values()) {
            final QuotaUsage usage = usageOf(service, dimension, window);
            assertEquals(expectedRequests, usage.requestCount(), "窗口 " + window + " 请求数");
            assertEquals(expectedRequests * 3L, usage.tokenCount(), "窗口 " + window + " token 数");
        }
    }

    @Test
    @DisplayName("fail-open：仓库抛错时 reserve 不抛出、放行并标记降级")
    void reserve_whenRepositoryFails_shouldFailOpen() {
        repository.setFailing(true);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        final QuotaDecision decision = assertDoesNotThrow(
            () -> service.reserve(QuotaRequest.of(dimension, 12)));

        assertTrue(decision.allowed(), "默认 fail-open：必须放行");
        assertTrue(decision.degraded());
        assertEquals(QuotaDecision.REASON_LEDGER_UNAVAILABLE, decision.reason());
        // 降级时无内存数据，查询同样不抛出
        assertEquals(Optional.empty(), assertDoesNotThrow(() -> service.usage(dimension, QuotaWindow.DAY)));
        assertEquals(List.of(), assertDoesNotThrow(() -> service.usageAll("key-1")));
        assertEquals(0, assertDoesNotThrow(service::flush));
        assertEquals(0, assertDoesNotThrow(service::cleanupExpired));
        assertEquals(0, assertDoesNotThrow(() -> service.reset("key-1")));
        assertDoesNotThrow(() -> service.settle(new QuotaSettlement(dimension, 10, 1, true)));
        assertDoesNotThrow(service::shutdown);
    }

    @Test
    @DisplayName("fail-closed：failOpen=false 且仓库抛错时拒绝请求")
    void reserve_whenRepositoryFailsAndFailClosed_shouldDeny() {
        properties.setFailOpen(false);
        repository.setFailing(true);
        final QuotaLedgerService service = service();

        final QuotaDecision decision = service.reserve(QuotaRequest.of(QuotaDimension.ofApiKey("key-1"), 12));

        assertFalse(decision.allowed());
        assertTrue(decision.degraded());
        assertEquals(QuotaDecision.REASON_LEDGER_UNAVAILABLE, decision.reason());
    }

    @Test
    @DisplayName("非法请求：null 请求按降级放行处理")
    void reserve_withNullRequest_shouldFailOpen() {
        final QuotaLedgerService service = service();

        final QuotaDecision decision = assertDoesNotThrow(() -> service.reserve(null));

        assertTrue(decision.allowed());
        assertTrue(decision.degraded());
        assertEquals(QuotaDecision.REASON_INVALID_REQUEST, decision.reason());
    }

    @Test
    @DisplayName("settle：按 actual-estimated 冲正 token")
    void settle_shouldAdjustTokensByActualMinusEstimated() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 100));

        service.settle(new QuotaSettlement(dimension, 100, 150, false));
        QuotaUsage usage = usageOf(service, dimension, QuotaWindow.HOUR);
        assertEquals(1L, usage.requestCount());
        assertEquals(150L, usage.tokenCount());

        // 实际用量小于预估：继续往下冲正（相对本次预估 -60 → 150 - 60 = 90）
        service.settle(new QuotaSettlement(dimension, 100, 40, false));
        usage = usageOf(service, dimension, QuotaWindow.HOUR);
        assertEquals(1L, usage.requestCount());
        assertEquals(90L, usage.tokenCount());
    }

    @Test
    @DisplayName("settle：failed=true 回滚整笔预留（请求与 token 归零，不出现负数）")
    void settle_whenFailed_shouldRollbackReservation() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 100));

        service.settle(new QuotaSettlement(dimension, 100, 0, true));

        for (final QuotaWindow window : QuotaWindow.values()) {
            final QuotaUsage usage = usageOf(service, dimension, window);
            assertEquals(0L, usage.requestCount(), "窗口 " + window + " 请求数");
            assertEquals(0L, usage.tokenCount(), "窗口 " + window + " token 数");
        }
    }

    @Test
    @DisplayName("settle：无对应预留时不新建槽位、不落库")
    void settle_withoutReservation_shouldNotCreateSlot() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        service.settle(new QuotaSettlement(dimension, 100, 0, true));

        assertTrue(service.usageAll("key-1").isEmpty());
        assertEquals(0, service.flush());
        assertTrue(repository.rows().isEmpty());
    }

    @Test
    @DisplayName("flush：增量落库一次即幂等，重复 flush 不重复计数")
    void flush_shouldPersistDeltasIdempotently() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 10));
        service.reserve(QuotaRequest.of(dimension, 20));

        assertEquals(4, service.flush());
        assertEquals(0, service.flush(), "无未落库增量时不应再写入");
        assertEquals(4, repository.rows().size());
        final QuotaLedgerEntity dayRow =
            repository.row("key-1", QuotaWindow.DAY.name(), QuotaWindow.DAY.windowStart(NOW));
        assertNotNull(dayRow);
        assertEquals(2L, dayRow.getRequestCount());
        assertEquals(30L, dayRow.getTokenCount());
        assertEquals(NOW, dayRow.getUpdatedAt());

        // 继续累加：第二次 flush 只写增量
        service.reserve(QuotaRequest.of(dimension, 5));
        assertEquals(4, service.flush());
        assertEquals(3L, repository.row("key-1", QuotaWindow.DAY.name(), QuotaWindow.DAY.windowStart(NOW))
            .getRequestCount());
        assertEquals(35L, repository.row("key-1", QuotaWindow.DAY.name(), QuotaWindow.DAY.windowStart(NOW))
            .getTokenCount());
    }

    @Test
    @DisplayName("冷启动续算：新实例从数据库基线继续累加")
    void reserve_afterRestart_shouldResumeFromDatabaseBaseline() {
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        final QuotaLedgerService first = service();
        first.reserve(QuotaRequest.of(dimension, 100));
        first.reserve(QuotaRequest.of(dimension, 100));
        first.flush();
        first.shutdown();

        // 模拟进程重启：内存清空，仅剩数据库快照
        final QuotaLedgerService restarted = service();
        restarted.reserve(QuotaRequest.of(dimension, 50));

        assertEquals(3L, usageOf(restarted, dimension, QuotaWindow.DAY).requestCount());
        assertEquals(250L, usageOf(restarted, dimension, QuotaWindow.DAY).tokenCount());
        // 重启后的 flush 只写本次会话增量
        restarted.flush();
        assertEquals(3L, repository.row("key-1", QuotaWindow.DAY.name(), QuotaWindow.DAY.windowStart(NOW))
            .getRequestCount());
    }

    @Test
    @DisplayName("usage：内存无数据时回退数据库快照，均无数据返回 empty")
    void usage_shouldFallBackToDatabaseSnapshot() {
        final LocalDateTime dayStart = QuotaWindow.DAY.windowStart(NOW);
        repository.seed("key-db", QuotaWindow.DAY.name(), dayStart, 5, 500);
        final QuotaLedgerService service = service();

        final QuotaUsage usage = usageOf(service, QuotaDimension.ofApiKey("key-db"), QuotaWindow.DAY);

        assertEquals(5L, usage.requestCount());
        assertEquals(500L, usage.tokenCount());
        assertEquals(dayStart, usage.windowStart());
        assertEquals(Optional.empty(), service.usage(QuotaDimension.ofApiKey("key-unknown"), QuotaWindow.DAY));
        assertEquals(Optional.empty(), service.usage(QuotaDimension.ofApiKey("key-db"), QuotaWindow.HOUR));
        assertEquals(Optional.empty(), service.usage(null, QuotaWindow.DAY));
        assertEquals(Optional.empty(), service.usage(QuotaDimension.ofApiKey("key-db"), null));
    }

    @Test
    @DisplayName("usageAll：内存与数据库合并，同键以内存为准避免重复计数")
    void usageAll_shouldMergeWithoutDoubleCounting() {
        final LocalDateTime dayStart = QuotaWindow.DAY.windowStart(NOW);
        // 数据库已有：今日陈旧快照（冷启动时会成为内存槽位基线）+ 一个历史小时窗口
        repository.seed("key-1", QuotaWindow.HOUR.name(), NOW.minusDays(1).truncatedTo(ChronoUnit.HOURS), 9, 90);
        repository.seed("key-1", QuotaWindow.DAY.name(), dayStart, 1, 1);
        repository.seed("other-key", QuotaWindow.DAY.name(), dayStart, 7, 70);
        final QuotaLedgerService service = service();
        service.reserve(QuotaRequest.of(QuotaDimension.ofApiKey("key-1"), 30));

        final List<QuotaUsage> usages = service.usageAll("key-1");

        // 4 个内存窗口 + 1 个历史小时窗口；今日窗口与内存槽位同键，不重复计入
        assertEquals(5, usages.size());
        assertEquals(1, usages.stream()
            .filter(item -> item.window() == QuotaWindow.DAY && dayStart.equals(item.windowStart()))
            .count(), "同一窗口只能有一条读数");
        final QuotaUsage today = usages.stream()
            .filter(item -> item.window() == QuotaWindow.DAY && dayStart.equals(item.windowStart()))
            .findFirst()
            .orElseThrow();
        // 内存槽位 = 数据库基线(1 请求 / 1 token) + 本次预留(1 请求 / 30 token)
        assertEquals(2L, today.requestCount());
        assertEquals(31L, today.tokenCount());
        assertEquals(List.of(), service.usageAll(null));
        assertEquals(List.of(), service.usageAll(""));
        assertEquals(1, service.usageAll("other-key").size());
    }

    @Test
    @DisplayName("reset：清空内存槽位与数据库快照，且不影响其它 API Key")
    void reset_shouldClearMemoryAndDatabase() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 10));
        service.flush();
        repository.seed("other-key", QuotaWindow.DAY.name(), QuotaWindow.DAY.windowStart(NOW), 1, 1);

        final int cleared = service.reset("key-1");

        assertEquals(8, cleared, "4 个内存槽位 + 4 行数据库快照");
        assertTrue(service.usageAll("key-1").isEmpty());
        assertEquals(1, repository.rows().size());
        assertEquals("other-key", repository.rows().get(0).getApiKeyId());
        assertEquals(0, service.reset(null));
        assertEquals(0, service.reset(""));
    }

    @Test
    @DisplayName("cleanupExpired：按各级保留期删除过期行并清理已落库的内存槽位")
    void cleanupExpired_shouldDeleteExpiredRowsAndPurgeMemory() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 10));
        assertEquals(4, service.flush());
        assertEquals(4, repository.rows().size());

        // 40 天后：minute(1d) / hour(2d) / day(35d) 过期，month(13mo) 未过期
        clock.advance(Duration.ofDays(40));
        assertEquals(3, service.cleanupExpired());

        assertEquals(1, repository.rows().size());
        assertEquals(QuotaWindow.MONTH.name(), repository.rows().get(0).getWindowType());
        final List<QuotaUsage> remaining = service.usageAll("key-1");
        assertEquals(1, remaining.size(), "过期的内存槽位也应被清理，仅剩月窗口");
        assertEquals(QuotaWindow.MONTH, remaining.get(0).window());
    }

    @Test
    @DisplayName("cleanupExpired：未过期数据与未落库增量不会被清理")
    void cleanupExpired_shouldKeepFreshAndUnpublishedSlots() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 10));

        // 未落库（未 flush）：即使时间推进到过期点也必须保留增量
        clock.advance(Duration.ofDays(10));
        assertEquals(0, service.cleanupExpired());
        assertFalse(service.usageAll("key-1").isEmpty());
        assertEquals(4, service.flush());
        assertEquals(4, repository.rows().size());
    }

    /**
     * 取指定窗口的用量（无数据直接失败，便于断言）。
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
     * 构造账本服务（共用内存仓库与配置，时钟可控）。
     *
     * @return 账本服务
     */
    private QuotaLedgerService service() {
        return new QuotaLedgerService(repository.proxy(), properties, clock);
    }

    /**
     * 可手动推进的测试时钟。
     */
    private static final class MutableClock extends Clock {

        private volatile Instant current;
        private final ZoneId zone;

        private MutableClock(final LocalDateTime start, final ZoneId zone) {
            this.current = start.atZone(zone).toInstant();
            this.zone = zone;
        }

        private void advance(final Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(final ZoneId newZone) {
            return new MutableClock(LocalDateTime.ofInstant(current, newZone), newZone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}

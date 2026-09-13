package org.unreal.modelrouter.auth.security.quota;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配额账本分布式计数与降级测试（v3.1 PR-3）。
 *
 * <p>被测对象是真实 {@link QuotaLedgerService} + 真实 {@link RedisCounterBackend} + 手写假 Redis
 * 模板（{@link FakeReactiveRedisTemplate}）+ 内存仓库（{@link InMemoryQuotaLedgerRepository}），
 * 不使用 Mockito。覆盖四类场景：</p>
 * <ul>
 *   <li><b>开关不变性</b>：{@code quota.enabled=false} 与 {@code distributed.enabled=false} 各自零副作用；</li>
 *   <li><b>降级</b>：Redis 断连 / 超时 → 回退本地计数、计数不丢、{@code degraded} 标记可见、无异常上抛；</li>
 *   <li><b>恢复</b>：降级期间累积的增量在 Redis 恢复后合并补写，且不重复计数；</li>
 *   <li><b>分布式读写与维护</b>：读数以 Redis 为准（并计入未补写增量）、快照仍落库、reset/清理同时作用于 Redis。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaLedgerService 分布式计数与降级测试")
class QuotaLedgerServiceDistributedTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties properties;
    private MutableClock clock;
    private FakeReactiveRedisTemplate template;
    private RedisCounterBackend redisBackend;
    private SimpleMeterRegistry meterRegistry;
    private QuotaCounterMetrics metrics;

    @BeforeEach
    void setUp() {
        repository = InMemoryQuotaLedgerRepository.create();
        properties = new QuotaProperties();
        properties.setEnabled(true);
        properties.getDistributed().setEnabled(true);
        properties.getDistributed().setTimeout(Duration.ofMillis(200));
        clock = new MutableClock(NOW, ZONE);
        template = new FakeReactiveRedisTemplate();
        redisBackend = new RedisCounterBackend(template, properties);
        meterRegistry = new SimpleMeterRegistry();
        metrics = new QuotaCounterMetrics(meterRegistry);
    }

    // ==================== 开关不变性 ====================

    @Test
    @DisplayName("quota.enabled=false：全部入口零副作用，不访问 Redis 与数据库")
    void quotaDisabled_shouldBeNoOp() {
        properties.setEnabled(false);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        assertFalse(service.isEnabled());
        assertFalse(service.isDistributed(), "账本未启用时不得声称使用 Redis 后端");
        assertEquals(LocalCounterBackend.NAME, service.backendName());
        final QuotaDecision disabled = service.reserve(QuotaRequest.of(dimension, 10));
        assertFalse(disabled.degraded());
        assertEquals(QuotaDecision.REASON_DISABLED, disabled.reason());

        service.settle(new QuotaSettlement(dimension, 10, 5, true));
        assertEquals(Optional.empty(), service.usage(dimension, QuotaWindow.DAY));
        assertEquals(0, service.flush());
        assertEquals(0, service.cleanupExpired());
        assertEquals(0, service.reset("key-1"));
        service.shutdown();

        assertEquals(0, template.commandCount(), "未启用时不得访问 Redis");
        assertEquals(0, repository.invocationCount(), "未启用时不得访问数据库");
    }

    @Test
    @DisplayName("distributed.enabled=false：即使传入 Redis 后端也完全走本地（零行为变更）")
    void distributedDisabled_shouldStayLocal() {
        properties.getDistributed().setEnabled(false);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        assertFalse(service.isDistributed());
        assertEquals(LocalCounterBackend.NAME, service.backendName());

        final QuotaDecision decision = service.reserve(QuotaRequest.of(dimension, 120));

        assertTrue(decision.allowed());
        assertFalse(decision.degraded());
        assertEquals(QuotaDecision.REASON_OK, decision.reason());
        for (final QuotaWindow window : QuotaWindow.values()) {
            final QuotaUsage usage = usageOf(service, dimension, window);
            assertEquals(1L, usage.requestCount());
            assertEquals(120L, usage.tokenCount());
        }
        assertEquals(4, service.flush());
        assertEquals(0, template.commandCount(), "本地模式不得访问 Redis");
        assertEquals(0.0, gauge("jairouter.quota.counter.redis.enabled"));
    }

    @Test
    @DisplayName("开启分布式但未装配后端：保持本地模式、标记降级并计数（不抛异常）")
    void distributedEnabledWithoutBackend_shouldDegradeOnStartup() {
        final QuotaLedgerService service =
            new QuotaLedgerService(repository.proxy(), properties, clock, null, metrics);

        assertFalse(service.isDistributed());
        assertTrue(service.isDegraded());
        assertEquals(QuotaLedgerService.REASON_REDIS_NOT_CONFIGURED, service.degradedReason());
        assertEquals(1.0, metrics.degradationCount(QuotaLedgerService.REASON_REDIS_NOT_CONFIGURED));
        assertTrue(service.reserve(QuotaRequest.of(QuotaDimension.ofApiKey("key-1"), 10)).allowed());
        assertEquals(0, template.commandCount());
    }

    // ==================== 降级：Redis 断连 / 超时 ====================

    @Test
    @DisplayName("Redis 断连：reserve 降级放行、无异常、本地计数不丢、degraded 标记可见")
    void redisUnavailable_shouldDegradeToLocal() {
        template.setFailing(true);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        final QuotaDecision decision = assertDoesNotThrow(() -> service.reserve(QuotaRequest.of(dimension, 120)));

        assertTrue(decision.allowed(), "默认 fail-open：必须放行");
        assertTrue(decision.degraded());
        assertEquals(QuotaLedgerService.REASON_REDIS_UNAVAILABLE, decision.reason());
        assertTrue(service.isDegraded());
        assertEquals(QuotaLedgerService.REASON_REDIS_UNAVAILABLE, service.degradedReason());
        // 降级次数按操作口径 +1（四级窗口合记一次）
        assertEquals(1.0, metrics.degradationCount(QuotaLedgerService.REASON_REDIS_UNAVAILABLE));
        // 计数不丢：四级窗口的本地兜底计数全部可见
        for (final QuotaWindow window : QuotaWindow.values()) {
            final QuotaUsage usage = usageOf(service, dimension, window);
            assertEquals(1L, usage.requestCount(), "窗口 " + window + " 请求数");
            assertEquals(120L, usage.tokenCount(), "窗口 " + window + " token 数");
        }
        // 上述 4 次读降级各再计一次降级
        assertEquals(5.0, metrics.degradationCount(QuotaLedgerService.REASON_REDIS_UNAVAILABLE));
        assertEquals(5.0, outcome("failure"), "首个窗口失败后剩余窗口不再尝试（1 次 reserve + 4 次读）");
    }

    @Test
    @DisplayName("Redis 超时：超过 distributed.timeout 即降级，原因标记为超时，且不抛异常")
    void redisTimeout_shouldDegradeWithinTimeout() {
        properties.getDistributed().setTimeout(Duration.ofMillis(30));
        template.setNeverCompleting(true);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        final long start = System.nanoTime();
        final QuotaDecision decision = assertDoesNotThrow(() -> service.reserve(QuotaRequest.of(dimension, 50)));
        final long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(decision.allowed());
        assertTrue(decision.degraded());
        assertEquals(QuotaLedgerService.REASON_REDIS_TIMEOUT, decision.reason());
        assertEquals(QuotaLedgerService.REASON_REDIS_TIMEOUT, service.degradedReason());
        assertTrue(elapsedMillis < 1000L, "热路径必须在超时上限附近返回，实际 " + elapsedMillis + "ms");
        // 超时同样不丢计数（本地兜底）
        assertEquals(1L, usageOf(service, dimension, QuotaWindow.DAY).requestCount());
        assertEquals(50L, usageOf(service, dimension, QuotaWindow.DAY).tokenCount());
        assertEquals(3.0, metrics.degradationCount(QuotaLedgerService.REASON_REDIS_TIMEOUT),
            "1 次 reserve 降级 + 2 次读降级");

        // 超时属“结果未知”：超时窗口的增量不重试（避免重复计数）；被跳过的窗口增量作为待补写保留
        template.setNeverCompleting(false);
        service.reserve(QuotaRequest.of(dimension, 50));
        assertArrayEquals(new long[]{1L, 50L}, template.values(redisBackend.keyOf(new QuotaCounterKey(
            dimension, QuotaWindow.MINUTE, QuotaWindow.MINUTE.windowStart(NOW)))),
            "MINUTE 窗口命令超时：增量丢弃，仅保留恢复后的本次增量");
        assertArrayEquals(new long[]{2L, 100L}, template.values(redisBackend.keyOf(new QuotaCounterKey(
            dimension, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW)))),
            "DAY 窗口被跳过（未发命令）：增量作为待补写保留，恢复后合并补写");
    }

    @Test
    @DisplayName("fail-closed：failOpen=false 且 Redis 不可用时拒绝请求，且不记本地账")
    void failClosed_shouldDenyWithoutCounting() {
        properties.setFailOpen(false);
        template.setFailing(true);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        final QuotaDecision decision = service.reserve(QuotaRequest.of(dimension, 120));

        assertFalse(decision.allowed());
        assertTrue(decision.degraded());
        assertEquals(QuotaLedgerService.REASON_REDIS_UNAVAILABLE, decision.reason());
        assertEquals(Optional.empty(), service.usage(dimension, QuotaWindow.DAY), "被拒绝的请求不计账");
    }

    // ==================== 恢复与补偿 ====================

    @Test
    @DisplayName("Redis 恢复：降级期间累积的增量合并补写，不重复计数")
    void redisRecovered_shouldPushPendingIncrements() {
        template.setFailing(true);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        final String redisKey = redisBackend.keyOf(new QuotaCounterKey(dimension, QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW)));

        service.reserve(QuotaRequest.of(dimension, 5));
        service.reserve(QuotaRequest.of(dimension, 5));
        service.reserve(QuotaRequest.of(dimension, 5));
        assertEquals(3L, usageOf(service, dimension, QuotaWindow.MINUTE).requestCount());

        template.setFailing(false);
        final QuotaDecision recovered = service.reserve(QuotaRequest.of(dimension, 5));

        assertFalse(recovered.degraded(), "恢复后不得再标记降级");
        assertFalse(service.isDegraded());
        assertEquals("", service.degradedReason());
        // 第 4 次 reserve 把待补写的 3 次 + 本次 1 次合并为一条命令写入
        assertArrayEquals(new long[]{4L, 20L}, template.values(redisKey));
        assertEquals("4", template.lastInvocation().args().get(0));
        assertEquals("20", template.lastInvocation().args().get(1));
        assertEquals(4L, usageOf(service, dimension, QuotaWindow.MINUTE).requestCount());
    }

    @Test
    @DisplayName("恢复过程中命令失败：增量放回待补写队列，下次继续补写（不丢计数）")
    void publishFailure_shouldKeepIncrementPending() {
        template.setFailing(true);
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        final String redisKey = redisBackend.keyOf(new QuotaCounterKey(dimension, QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW)));

        service.reserve(QuotaRequest.of(dimension, 7));
        service.reserve(QuotaRequest.of(dimension, 7));
        template.setFailing(false);
        service.reserve(QuotaRequest.of(dimension, 7));

        assertArrayEquals(new long[]{3L, 21L}, template.values(redisKey));
    }

    // ==================== 分布式读数 ====================

    @Test
    @DisplayName("读数：以 Redis 权威值为准（含其它实例写入的计数）")
    void usage_shouldReadAuthoritativeRedisValue() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        // 其它实例在同一窗口已用 40 次 / 400 token
        template.seed(redisBackend.keyOf(new QuotaCounterKey(dimension, QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW))), 40L, 400L, 3600L);

        service.reserve(QuotaRequest.of(dimension, 100));

        final QuotaUsage usage = usageOf(service, dimension, QuotaWindow.MINUTE);
        assertEquals(41L, usage.requestCount(), "跨实例共享额度：本实例 +1 后应为 41");
        assertEquals(500L, usage.tokenCount());
        assertFalse(service.isDegraded());
    }

    @Test
    @DisplayName("读数：Redis 可用但本节点有未补写增量时，把增量计入读数（避免降级期间低估用量）")
    void usage_shouldAddPendingResidueOnTopOfRedisValue() {
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        template.seed(redisBackend.keyOf(new QuotaCounterKey(dimension, QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW))), 10L, 100L, 3600L);
        template.setFailing(true);
        final QuotaLedgerService service = service();
        service.reserve(QuotaRequest.of(dimension, 50));

        template.setFailing(false);
        final QuotaUsage usage = usageOf(service, dimension, QuotaWindow.MINUTE);

        assertEquals(11L, usage.requestCount(), "Redis 10 + 未补写 1");
        assertEquals(150L, usage.tokenCount());
    }

    @Test
    @DisplayName("读数：Redis 不可用时回退本地计数视图（degrade-to-local 默认 true）")
    void usage_shouldFallBackToLocalWhenRedisUnavailable() {
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        final QuotaLedgerService service = service();
        service.reserve(QuotaRequest.of(dimension, 30));
        template.setFailing(true);

        final QuotaUsage usage = usageOf(service, dimension, QuotaWindow.HOUR);

        assertEquals(1L, usage.requestCount());
        assertEquals(30L, usage.tokenCount());
        assertTrue(service.isDegraded());
    }

    @Test
    @DisplayName("degrade-to-local=false：读数不降级，usage 返回空、usageStrict 抛出（交调用方 fail-open）")
    void degradeToLocalDisabled_shouldNotFallBackOnReads() {
        properties.getDistributed().setDegradeToLocal(false);
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        final QuotaLedgerService service = service();
        service.reserve(QuotaRequest.of(dimension, 30));
        template.setFailing(true);

        assertEquals(Optional.empty(), service.usage(dimension, QuotaWindow.DAY));
        assertThrows(IllegalStateException.class, () -> service.usageStrict(dimension, QuotaWindow.DAY));
        // 写路径仍然只是降级告警，不抛异常
        assertTrue(assertDoesNotThrow(() -> service.reserve(QuotaRequest.of(dimension, 30))).degraded());
    }

    @Test
    @DisplayName("读数：Redis 无该桶时回退数据库快照（与本地模式一致）")
    void usage_shouldFallBackToDatabaseWhenRedisBucketMissing() {
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-db");
        repository.seed("key-db", QuotaWindow.DAY.name(), QuotaWindow.DAY.windowStart(NOW), 5L, 500L);
        final QuotaLedgerService service = service();

        final QuotaUsage usage = usageOf(service, dimension, QuotaWindow.DAY);

        assertEquals(5L, usage.requestCount());
        assertEquals(500L, usage.tokenCount());
        assertEquals(Optional.empty(), service.usage(QuotaDimension.ofApiKey("key-unknown"), QuotaWindow.DAY));
    }

    // ==================== 快照与维护路径 ====================

    @Test
    @DisplayName("快照：Redis 模式下 JPA 快照仍按本地镜像增量落库（供重启 / 审计）")
    void flush_shouldStillSnapshotInDistributedMode() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 30));
        service.reserve(QuotaRequest.of(dimension, 10));

        assertEquals(4, service.flush());
        assertEquals(0, service.flush(), "无未落库增量时不应再写入");
        final QuotaLedgerEntity dayRow =
            repository.row("key-1", QuotaWindow.DAY.name(), QuotaWindow.DAY.windowStart(NOW));
        assertNotNull(dayRow);
        assertEquals(2L, dayRow.getRequestCount());
        assertEquals(40L, dayRow.getTokenCount());
    }

    @Test
    @DisplayName("settle：冲正量补写 Redis（失败回滚同样生效，且不出现负数）")
    void settle_shouldPushAdjustmentsToRedis() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        final String redisKey = redisBackend.keyOf(new QuotaCounterKey(dimension, QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW)));
        service.reserve(QuotaRequest.of(dimension, 100));

        service.settle(new QuotaSettlement(dimension, 100, 60, false));
        assertArrayEquals(new long[]{1L, 60L}, template.values(redisKey));

        service.settle(new QuotaSettlement(dimension, 60, 0, true));
        assertArrayEquals(new long[]{0L, 0L}, template.values(redisKey), "回滚后两个字段都被钳制到 0");

        // 无对应预留的维度：不新建 Redis key
        final QuotaDimension unknown = QuotaDimension.ofApiKey("key-unknown");
        service.settle(new QuotaSettlement(unknown, 10, 0, true));
        assertNull(template.values(redisBackend.keyOf(new QuotaCounterKey(unknown, QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW)))));
    }

    @Test
    @DisplayName("reset：同时清空本地槽位、数据库快照与 Redis 计数桶")
    void reset_shouldClearRedisBucketsToo() {
        final QuotaLedgerService service = service();
        final QuotaDimension first = QuotaDimension.ofApiKey("key-1");
        final QuotaDimension second = QuotaDimension.ofApiKey("key-2");
        service.reserve(QuotaRequest.of(first, 10));
        service.reserve(QuotaRequest.of(second, 10));
        assertEquals(8, service.flush());
        assertEquals(8, template.keys().size());

        final int cleared = service.reset("key-1");

        assertEquals(12, cleared, "4 个本地槽位 + 4 行数据库快照 + 4 个 Redis 桶");
        assertEquals(4, template.keys().size());
        assertTrue(template.keys().stream().allMatch(key -> key.contains(":key-2:")));
        assertEquals(Optional.empty(), service.usage(first, QuotaWindow.DAY));
        assertNotNull(service.usage(second, QuotaWindow.DAY).orElse(null));
    }

    @Test
    @DisplayName("cleanupExpired：按保留期同时清理数据库行与 Redis 桶（返回值语义与 PR-1 一致）")
    void cleanupExpired_shouldPurgeRedisBuckets() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        service.reserve(QuotaRequest.of(dimension, 10));
        assertEquals(4, service.flush());
        assertEquals(4, template.keys().size());

        clock.advance(Duration.ofDays(40));
        assertEquals(3, service.cleanupExpired(), "minute / hour / day 的数据库行");

        assertEquals(1, template.keys().size(), "仅月窗口未过期");
        assertTrue(template.keys().iterator().next().contains(":MONTH:"));
        assertEquals(1, service.usageAll("key-1").size(), "过期的本地槽位也应被清理");
    }

    // ==================== 指标 ====================

    @Test
    @DisplayName("指标：后端类型 gauge 与 Redis 命令次数 / 耗时均被记录")
    void metrics_shouldRecordBackendAndCommandDurations() {
        final QuotaLedgerService service = service();
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");

        assertEquals(1.0, gauge("jairouter.quota.counter.redis.enabled"));
        assertEquals(RedisCounterBackend.NAME, service.backendName());

        service.reserve(QuotaRequest.of(dimension, 10));

        assertEquals(4.0, outcome("success"), "四级窗口各一次 Redis 命令");
        assertEquals(4L, meterRegistry.get("jairouter.quota.counter.redis.command.duration")
            .tag("outcome", "success").timer().count());
        assertTrue(meterRegistry.get("jairouter.quota.counter.redis.command.duration")
            .tag("outcome", "success").timer().totalTime(TimeUnit.NANOSECONDS) > 0L);
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
     * 读取某结果的 Redis 命令次数。
     *
     * @param result success / failure
     * @return 次数
     */
    private double outcome(final String result) {
        return meterRegistry.get("jairouter.quota.counter.redis.commands").tag("outcome", result).counter().count();
    }

    /**
     * 读取 gauge 值。
     *
     * @param name 指标名
     * @return 值
     */
    private double gauge(final String name) {
        return meterRegistry.get(name).gauge().value();
    }

    /**
     * 构造账本服务（分布式模式，共用假 Redis 与内存仓库，时钟可控）。
     *
     * @return 账本服务
     */
    private QuotaLedgerService service() {
        return new QuotaLedgerService(repository.proxy(), properties, clock, redisBackend, metrics);
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

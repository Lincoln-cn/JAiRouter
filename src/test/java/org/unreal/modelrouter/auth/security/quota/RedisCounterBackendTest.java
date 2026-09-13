package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RedisCounterBackend} 单元测试（v3.1 PR-3）。
 *
 * <p>使用手写的假 {@link FakeReactiveRedisTemplate}（{@link ReactiveRedisTemplate} 桩，
 * 不走 Mockito）验证 Lua 脚本调用形态、TTL 计算、双字段累加、非负钳制、读数语义与异常传播。
 * 真实 Redis 的原子性与 TTL 由 {@code QuotaDistributedRedisIntegrationTest} 在
 * {@code REDIS_TEST=true} 时验证。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("RedisCounterBackend 分布式计数测试")
class RedisCounterBackendTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    private static final Duration WAIT = Duration.ofSeconds(5);

    private static final QuotaDimension DIMENSION =
        new QuotaDimension("tenant-1", "key-1", "user-1", "chat", "gpt-4");

    private FakeReactiveRedisTemplate template;
    private QuotaProperties properties;
    private RedisCounterBackend backend;

    @BeforeEach
    void setUp() {
        template = new FakeReactiveRedisTemplate();
        properties = new QuotaProperties();
        properties.setEnabled(true);
        properties.getDistributed().setEnabled(true);
        backend = new RedisCounterBackend(template, properties);
    }

    // ==================== key 结构 ====================

    @Test
    @DisplayName("key 结构：{prefix}:{tenant}:{apiKey}:{user}:{service}:{model}:{WINDOW}:{windowStart}")
    void keyOf_shouldBuildNamespacedKey() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        assertEquals("jairouter:quota:tenant-1:key-1:user-1:chat:gpt-4:DAY:20260314000000", backend.keyOf(key));
    }

    @Test
    @DisplayName("key 结构：缺省维度写占位符，非法字符替换为下划线（避免冒号破坏分段）")
    void keyOf_shouldSanitizeSegments() {
        final QuotaCounterKey key = new QuotaCounterKey(QuotaDimension.ofApiKey("key:1"),
            QuotaWindow.MINUTE, QuotaWindow.MINUTE.windowStart(NOW));

        assertEquals("jairouter:quota:_:key_1:_:_:_:MINUTE:20260314134500", backend.keyOf(key));
    }

    @Test
    @DisplayName("key 结构：前缀可配置，空配置回落默认值")
    void keyOf_shouldHonorConfiguredPrefix() {
        properties.getDistributed().setKeyPrefix("custom:quota");
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.HOUR, QuotaWindow.HOUR.windowStart(NOW));

        assertEquals("custom:quota:tenant-1:key-1:user-1:chat:gpt-4:HOUR:20260314130000", backend.keyOf(key));

        properties.getDistributed().setKeyPrefix("");
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_KEY_PREFIX, properties.distributedKeyPrefix());
        assertTrue(backend.keyOf(key).startsWith("jairouter:quota:"));
    }

    // ==================== Lua 调用形态与累加 ====================

    @Test
    @DisplayName("累加：调用多个 Lua 累加脚本，KEYS/ARGV 携带 key、双字段增量与 TTL 秒数")
    void increment_shouldInvokeLuaScriptWithTtl() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW));

        final long[] totals = backend.increment(key, 1L, 120L).block(WAIT);

        assertArrayEquals(new long[]{1L, 120L}, totals);
        final FakeReactiveRedisTemplate.Invocation invocation = template.lastInvocation();
        assertNotNull(invocation);
        assertEquals(RedisCounterBackend.INCREMENT_SCRIPT, invocation.script());
        assertEquals(List.of("jairouter:quota:tenant-1:key-1:user-1:chat:gpt-4:MINUTE:20260314134500"),
            invocation.keys());
        // ARGV = [请求增量, token 增量, TTL 秒数]（MINUTE 窗口 × 1d 保留期 = 86400s）
        assertEquals(List.of("1", "120", "86400"), invocation.args());
        assertEquals(86400L, template.ttlSeconds(invocation.keys().get(0)));
    }

    @Test
    @DisplayName("累加：同一 key 多次累加，两个字段各自精确累计")
    void increment_shouldAccumulateBothFields() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        backend.increment(key, 1L, 100L).block(WAIT);
        backend.increment(key, 1L, 50L).block(WAIT);
        final long[] totals = backend.increment(key, 3L, 7L).block(WAIT);

        assertArrayEquals(new long[]{5L, 157L}, totals);
        assertArrayEquals(new long[]{5L, 157L}, template.values(backend.keyOf(key)));
    }

    @Test
    @DisplayName("累加：脚本内非负钳制，冲正不会把计数打成负数")
    void increment_shouldClampAtZero() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        final long[] totals = backend.increment(key, -5L, -100L).block(WAIT);

        assertArrayEquals(new long[]{0L, 0L}, totals);
    }

    @Test
    @DisplayName("结算语义：incrementIfPresent 对不存在的桶返回空 Mono 且不创建 key")
    void incrementIfPresent_shouldNotCreateBucket() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        assertNull(backend.incrementIfPresent(key, -1L, -100L).block(WAIT));
        assertTrue(template.keys().isEmpty(), "不得为结算新建 key");

        backend.increment(key, 1L, 100L).block(WAIT);
        final long[] adjusted = backend.incrementIfPresent(key, -1L, -40L).block(WAIT);
        assertArrayEquals(new long[]{0L, 60L}, adjusted);
        assertEquals(3, template.invocations().size(), "1 次未命中 + 1 次累加 + 1 次冲正");
    }

    // ==================== 读数 ====================

    @Test
    @DisplayName("读数：桶不存在返回 empty，存在返回双字段值")
    void read_shouldDistinguishMissingAndZero() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        assertEquals(Optional.empty(), backend.read(key).block(WAIT));

        backend.increment(key, 2L, 30L).block(WAIT);
        final Optional<long[]> usage = backend.read(key).block(WAIT);
        assertTrue(usage.isPresent());
        assertArrayEquals(new long[]{2L, 30L}, usage.get());
    }

    @Test
    @DisplayName("读数：读取其它实例写入的计数（跨实例共享额度的核心场景）")
    void read_shouldSeeOtherInstancesCounters() {
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));
        template.seed(backend.keyOf(key), 42L, 4200L, 3600L);

        final Optional<long[]> usage = backend.read(key).block(WAIT);

        assertTrue(usage.isPresent());
        assertArrayEquals(new long[]{42L, 4200L}, usage.get());
    }

    // ==================== TTL ====================

    @Test
    @DisplayName("TTL：窗口长度 × 该窗口保留期（默认保留期，MONTH 按 30 天近似）")
    void ttlSeconds_shouldFollowDefaultRetention() {
        assertEquals(60L * 1440L, backend.ttlSeconds(QuotaWindow.MINUTE), "1 分钟窗口 × 1d(1440 分钟)");
        assertEquals(3600L * 48L, backend.ttlSeconds(QuotaWindow.HOUR), "1 小时窗口 × 2d(48 小时)");
        assertEquals(86400L * 35L, backend.ttlSeconds(QuotaWindow.DAY), "1 天窗口 × 35d");
        assertEquals(2592000L * 13L, backend.ttlSeconds(QuotaWindow.MONTH), "1 月(30d) × 13mo");
    }

    @Test
    @DisplayName("TTL：可被保留期配置覆盖，且向上取整到整窗口长度（至少 1 个窗口）")
    void ttlSeconds_shouldFollowConfiguredRetention() {
        properties.setRetention(new LinkedHashMap<>(
            Map.of("minute", "90s", "hour", "3h", "day", "5m", "month", "2mo")));

        assertEquals(120L, backend.ttlSeconds(QuotaWindow.MINUTE), "90s 向上取整到 2 个分钟窗口");
        assertEquals(3600L * 3L, backend.ttlSeconds(QuotaWindow.HOUR));
        assertEquals(86400L, backend.ttlSeconds(QuotaWindow.DAY), "5m 不足一个天窗口，按 1 个窗口兜底");
        assertEquals(2592000L * 2L, backend.ttlSeconds(QuotaWindow.MONTH));
    }

    @Test
    @DisplayName("TTL：非法保留期兜底为 1 天（与 QuotaProperties 兜底一致）")
    void ttlSeconds_shouldTolerateIllegalRetention() {
        properties.setRetention(new LinkedHashMap<>(Map.of("minute", "abc")));

        assertEquals(86400L, backend.ttlSeconds(QuotaWindow.MINUTE));
    }

    // ==================== 异常传播 ====================

    @Test
    @DisplayName("异常传播：Redis 命令失败时以异常形式上抛（不吞异常，由账本降级）")
    void increment_shouldPropagateRedisFailure() {
        template.setFailing(true);
        final QuotaCounterKey key = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));

        final RedisConnectionFailureException error = assertThrows(RedisConnectionFailureException.class,
            () -> backend.increment(key, 1L, 1L).block(WAIT));
        assertTrue(error.getMessage().contains("模拟 Redis 不可用"));

        assertThrows(RedisConnectionFailureException.class, () -> backend.read(key).block(WAIT));
        assertThrows(RedisConnectionFailureException.class, () -> backend.reset("key-1").block(WAIT));
        assertThrows(RedisConnectionFailureException.class,
            () -> backend.deleteExpired(QuotaWindow.DAY, NOW).block(WAIT));
    }

    // ==================== 维护路径 ====================

    @Test
    @DisplayName("reset：只删除 API Key 段匹配的桶（用户段同名的 key 不会被误删）")
    void reset_shouldDeleteOnlyMatchingApiKeyBuckets() {
        final LocalDateTime dayStart = QuotaWindow.DAY.windowStart(NOW);
        final String targetKey = backend.keyOf(new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, dayStart));
        final String otherApiKey = backend.keyOf(new QuotaCounterKey(
            new QuotaDimension("tenant-1", "key-2", "user-1", "chat", "gpt-4"), QuotaWindow.DAY, dayStart));
        // 干扰项：API Key 段是 other-key，但用户段恰好等于 key-1（glob 会匹配，段校验必须排除）
        final String decoy = backend.keyOf(new QuotaCounterKey(
            new QuotaDimension("tenant-1", "other-key", "key-1", "chat", "gpt-4"), QuotaWindow.DAY, dayStart));
        template.seed(targetKey, 3L, 30L, 1000L);
        template.seed(otherApiKey, 1L, 10L, 1000L);
        template.seed(decoy, 1L, 10L, 1000L);

        assertEquals(1L, backend.reset("key-1").block(WAIT));

        assertNull(template.values(targetKey));
        assertNotNull(template.values(otherApiKey));
        assertNotNull(template.values(decoy), "用户段同名不等于 API Key 匹配");
        assertEquals(0L, backend.reset("").block(WAIT));
        assertEquals(0L, backend.reset(null).block(WAIT));
    }

    @Test
    @DisplayName("deleteExpired：只删除指定窗口且窗口起点早于截止时间的桶")
    void deleteExpired_shouldDeleteOnlyExpiredBuckets() {
        final QuotaCounterKey expiredDay = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY,
            QuotaWindow.DAY.windowStart(NOW.minusDays(40)));
        final QuotaCounterKey freshDay = new QuotaCounterKey(DIMENSION, QuotaWindow.DAY,
            QuotaWindow.DAY.windowStart(NOW));
        final QuotaCounterKey expiredHour = new QuotaCounterKey(DIMENSION, QuotaWindow.HOUR,
            QuotaWindow.HOUR.windowStart(NOW.minusDays(40)));
        template.seed(backend.keyOf(expiredDay), 1L, 1L, 1000L);
        template.seed(backend.keyOf(freshDay), 1L, 1L, 1000L);
        template.seed(backend.keyOf(expiredHour), 1L, 1L, 1000L);

        // DAY 窗口保留期 35 天：40 天前的天窗口过期，小时窗口（不同 window 段）不在此次清理范围
        assertEquals(1L, backend.deleteExpired(QuotaWindow.DAY, NOW.minusDays(35)).block(WAIT));

        assertNull(template.values(backend.keyOf(expiredDay)));
        assertNotNull(template.values(backend.keyOf(freshDay)));
        assertNotNull(template.values(backend.keyOf(expiredHour)));
        assertEquals(0L, backend.deleteExpired(null, NOW).block(WAIT));
        assertEquals(0L, backend.deleteExpired(QuotaWindow.DAY, null).block(WAIT));
    }

    @Test
    @DisplayName("后端标识：name 固定为 redis，便于指标与日志标记后端类型")
    void name_shouldBeRedis() {
        assertEquals(RedisCounterBackend.NAME, backend.name());
        assertFalse(backend.name().isEmpty());
    }
}

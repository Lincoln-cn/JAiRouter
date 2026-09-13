package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RedisCounterBackend} 真 Redis 集成测试（v3.1 PR-3）。
 *
 * <p><b>默认跳过</b>（本机通常没有 Redis）：仅当环境变量 {@code REDIS_TEST=true} 时执行；
 * 连接参数可用 {@code REDIS_TEST_HOST}（默认 {@code 127.0.0.1}）与 {@code REDIS_TEST_PORT}
 * （默认 {@code 6379}）覆盖。启用方式：</p>
 *
 * <pre>
 *   # 1. 启动一个临时 Redis（推荐 Docker，避免污染已有实例）
 *   docker run --rm -p 6379:6379 redis:7-alpine
 *   # 2. 打开开关后运行测试
 *   PowerShell:  $env:REDIS_TEST="true"; .\mvnw test -Dtest=QuotaDistributedRedisIntegrationTest
 *   bash:        REDIS_TEST=true ./mvnw test -Dtest=QuotaDistributedRedisIntegrationTest
 * </pre>
 *
 * <p>测试统一使用 {@code db=15} 与带随机后缀的 key 前缀（{@code jairouter:quota-it:&lt;uuid&gt;}），
 * 每次运行前后清理自己的 key，不与其它数据互相干扰。</p>
 *
 * <p>验证目标：Lua 脚本在真实 Redis 上的<b>原子性</b>（并发累加不丢计数）、TTL 实际生效且不被重复
 * 续期、读数 / 结算 / 清理路径在真实协议下的行为。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST", matches = "true")
@DisplayName("RedisCounterBackend 真 Redis 集成测试（REDIS_TEST=true 时执行）")
class QuotaDistributedRedisIntegrationTest {

    /** 测试专用数据库编号（避免污染业务数据） */
    private static final int TEST_DATABASE = 15;

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    private LettuceConnectionFactory connectionFactory;
    private ReactiveRedisTemplate<String, String> template;
    private QuotaProperties properties;
    private RedisCounterBackend backend;
    private String prefix;

    @BeforeEach
    void setUp() {
        final String host = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
        final int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
        final RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setDatabase(TEST_DATABASE);
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        template = new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());

        properties = new QuotaProperties();
        properties.setEnabled(true);
        properties.getDistributed().setEnabled(true);
        prefix = "jairouter:quota-it:" + UUID.randomUUID().toString().replace("-", "");
        properties.getDistributed().setKeyPrefix(prefix);
        backend = new RedisCounterBackend(template, properties);
        deleteAll();
    }

    @AfterEach
    void tearDown() {
        try {
            deleteAll();
        } finally {
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
        }
    }

    @Test
    @DisplayName("真 Redis：Lua 原子累加两个字段并设置 TTL（TTL 落在期望窗口内）")
    void increment_shouldAccumulateAtomicallyAndSetTtl() {
        final QuotaCounterKey key = new QuotaCounterKey(QuotaDimension.ofApiKey("key-1"), QuotaWindow.MINUTE,
            QuotaWindow.MINUTE.windowStart(NOW));
        final String redisKey = backend.keyOf(key);

        assertArrayEquals(new long[]{1L, 100L}, backend.increment(key, 1L, 100L).block(WAIT));
        assertArrayEquals(new long[]{3L, 130L}, backend.increment(key, 2L, 30L).block(WAIT));

        // 直接读 Redis 校验写入的是 req / tok 两个字段
        assertEquals("3", template.opsForHash().get(redisKey, "req").block(WAIT));
        assertEquals("130", template.opsForHash().get(redisKey, "tok").block(WAIT));

        // TTL = 窗口长度 × 保留期（MINUTE × 1d = 86400s），允许少量执行耗时偏差
        final Duration ttl = template.getExpire(redisKey).block(WAIT);
        assertNotNull(ttl);
        final long expected = backend.ttlSeconds(QuotaWindow.MINUTE);
        assertTrue(ttl.getSeconds() <= expected && ttl.getSeconds() > expected - 60L,
            "TTL 应在 (" + (expected - 60L) + ", " + expected + "] 秒内，实际 " + ttl.getSeconds());
    }

    @Test
    @DisplayName("真 Redis：并发累加不丢计数（Lua 原子性 + HINCRBY）")
    void increment_shouldBeAtomicUnderConcurrency() throws Exception {
        final QuotaCounterKey key = new QuotaCounterKey(QuotaDimension.ofApiKey("key-concurrent"), QuotaWindow.DAY,
            QuotaWindow.DAY.windowStart(NOW));
        final int threads = 8;
        final int perThread = 50;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int round = 0; round < perThread; round++) {
                        backend.increment(key, 1L, 3L).block(WAIT);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        final long[] totals = backend.read(key).block(WAIT).orElseThrow();
        assertArrayEquals(new long[]{(long) threads * perThread, (long) threads * perThread * 3L}, totals);
    }

    @Test
    @DisplayName("真 Redis：读数区分“无桶”与“计数为 0”，结算不创建桶，冲正钳制到 0")
    void read_shouldDistinguishMissingBucket() {
        final QuotaCounterKey key = new QuotaCounterKey(QuotaDimension.ofApiKey("key-read"), QuotaWindow.HOUR,
            QuotaWindow.HOUR.windowStart(NOW));
        final String redisKey = backend.keyOf(key);

        assertTrue(backend.read(key).block(WAIT).isEmpty());
        assertNull(backend.incrementIfPresent(key, 1L, 1L).block(WAIT));
        assertFalse(template.hasKey(redisKey).block(WAIT), "结算不得创建 key");

        backend.increment(key, 2L, 20L).block(WAIT);
        assertArrayEquals(new long[]{2L, 20L}, backend.read(key).block(WAIT).orElseThrow());
        assertArrayEquals(new long[]{0L, 0L}, backend.increment(key, -5L, -100L).block(WAIT),
            "冲正为负时脚本内钳制到 0");
    }

    @Test
    @DisplayName("真 Redis：reset 按 API Key 段删除（不误删用户段同名的 key）")
    void reset_shouldDeleteOnlyMatchingApiKeyKeys() {
        final LocalDateTime dayStart = QuotaWindow.DAY.windowStart(NOW);
        final QuotaCounterKey target = new QuotaCounterKey(QuotaDimension.ofApiKey("key-1"), QuotaWindow.DAY, dayStart);
        final QuotaCounterKey other = new QuotaCounterKey(QuotaDimension.ofApiKey("key-2"), QuotaWindow.DAY, dayStart);
        final QuotaCounterKey decoy = new QuotaCounterKey(
            new QuotaDimension("t1", "other-key", "key-1", "chat", "gpt-4"), QuotaWindow.DAY, dayStart);
        backend.increment(target, 1L, 1L).block(WAIT);
        backend.increment(other, 1L, 1L).block(WAIT);
        backend.increment(decoy, 1L, 1L).block(WAIT);

        assertEquals(1L, backend.reset("key-1").block(WAIT));

        assertTrue(backend.read(target).block(WAIT).isEmpty());
        assertTrue(backend.read(other).block(WAIT).isPresent());
        assertTrue(backend.read(decoy).block(WAIT).isPresent(), "用户段与 API Key 同名不得误删");
    }

    @Test
    @DisplayName("真 Redis：deleteExpired 按窗口与窗口起点删除过期计数")
    void deleteExpired_shouldDeleteOnlyExpiredBuckets() {
        final QuotaDimension dimension = QuotaDimension.ofApiKey("key-1");
        final QuotaCounterKey expiredDay = new QuotaCounterKey(dimension, QuotaWindow.DAY,
            QuotaWindow.DAY.windowStart(NOW.minusDays(40)));
        final QuotaCounterKey freshDay = new QuotaCounterKey(dimension, QuotaWindow.DAY,
            QuotaWindow.DAY.windowStart(NOW));
        final QuotaCounterKey expiredHour = new QuotaCounterKey(dimension, QuotaWindow.HOUR,
            QuotaWindow.HOUR.windowStart(NOW.minusDays(40)));
        backend.increment(expiredDay, 1L, 1L).block(WAIT);
        backend.increment(freshDay, 1L, 1L).block(WAIT);
        backend.increment(expiredHour, 1L, 1L).block(WAIT);

        assertEquals(1L, backend.deleteExpired(QuotaWindow.DAY, NOW.minusDays(35)).block(WAIT));

        assertTrue(backend.read(expiredDay).block(WAIT).isEmpty());
        assertTrue(backend.read(freshDay).block(WAIT).isPresent());
        assertTrue(backend.read(expiredHour).block(WAIT).isPresent(), "不同窗口不在本次清理范围");
    }

    @Test
    @DisplayName("真 Redis：TTL 只在首次写入设置（后续累加不续期，桶按保留期自然过期）")
    void ttl_shouldNotBeRefreshedByLaterIncrements() throws Exception {
        final QuotaCounterKey key = new QuotaCounterKey(QuotaDimension.ofApiKey("key-ttl"), QuotaWindow.DAY,
            QuotaWindow.DAY.windowStart(NOW));
        final String redisKey = backend.keyOf(key);
        backend.increment(key, 1L, 1L).block(WAIT);
        final Duration first = template.getExpire(redisKey).block(WAIT);
        assertNotNull(first);

        Thread.sleep(1100L);
        backend.increment(key, 1L, 1L).block(WAIT);
        final Duration second = template.getExpire(redisKey).block(WAIT);
        assertNotNull(second);

        assertTrue(second.getSeconds() < first.getSeconds(),
            "TTL 不得被后续写入续期: first=" + first.getSeconds() + ", second=" + second.getSeconds());
        assertEquals(backend.ttlSeconds(QuotaWindow.DAY), first.getSeconds() + 1L);
    }

    @Test
    @DisplayName("真 Redis：key 结构（前缀 / 维度段 / 窗口 / 定长窗口起点）可读且与配置一致")
    void keyFormat_shouldFollowConfiguredPrefix() {
        final QuotaCounterKey key = new QuotaCounterKey(
            new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-4"), QuotaWindow.MONTH,
            QuotaWindow.MONTH.windowStart(NOW));

        assertEquals(prefix + ":t1:key-1:u1:chat:gpt-4:MONTH:20260301000000", backend.keyOf(key));

        backend.increment(key, 1L, 1L).block(WAIT);
        final Duration ttl = template.getExpire(backend.keyOf(key)).block(WAIT);
        assertNotNull(ttl);
        assertEquals(backend.ttlSeconds(QuotaWindow.MONTH), ttl.getSeconds() + 1L);
    }

    /**
     * 删除本次测试前缀下的全部 key。
     */
    private void deleteAll() {
        final ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(256).build();
        final List<String> keys = template.scan(options).collectList().block(WAIT);
        if (keys != null && !keys.isEmpty()) {
            template.delete(Flux.fromIterable(keys)).block(WAIT);
        }
    }
}

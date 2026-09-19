package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 分布式配额「原子 CAS 预留」接入测试（P1）。
 *
 * <p>期望：{@code tryReserve} 在分布式模式下走 {@code incrementWithLimit}，
 * 并发下不允许超过 {@code dailyRequestLimit}。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuotaEnforcementService 分布式 CAS 预留")
class QuotaEnforcementServiceDistributedCasTest {

    private static final String KEY_ID = "key-cas";
    private static final String KEY_HASH = "hash-cas";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private ApiKeyService apiKeyService;

    private FakeReactiveRedisTemplate template;
    private QuotaProperties properties;
    private RedisCounterBackend redisBackend;
    private QuotaLedgerService ledgerService;
    private QuotaEnforcementService enforcementService;
    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() {
        template = new FakeReactiveRedisTemplate();
        properties = new QuotaProperties();
        properties.setEnabled(true);
        properties.getDistributed().setEnabled(true);
        // timeout 字段类型以 QuotaProperties.Distributed 为准（毫秒 int 或 Duration）
        redisBackend = new RedisCounterBackend(template, properties);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        ledgerService = new QuotaLedgerService(new InMemoryQuotaLedgerRepository().proxy(),
                properties, clock, redisBackend, null);
        enforcementService = new QuotaEnforcementService(ledgerService, properties, apiKeyService, clock);

        apiKeyCache = new HashMap<>();
        keyIdIndex = new HashMap<>();
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);
        registerKey(1L, 0L, 0);
    }

    private void registerKey(final long dailyReqLimit, final long dailyTokLimit, final int perMinute) {
        ApiKey key = new ApiKey();
        key.setKeyId(KEY_ID);
        key.setDailyRequestLimit(dailyReqLimit);
        key.setDailyTokenLimit(dailyTokLimit);
        key.setRateLimitPerMinute(perMinute);
        apiKeyCache.put(KEY_HASH, key);
        keyIdIndex.put(KEY_ID, KEY_HASH);
    }

    private ServerHttpRequest request() {
        return MockServerHttpRequest.post("/v1/chat/completions").build();
    }

    @Test
    @DisplayName("分布式：账本 isDistributed=true")
    void ledger_isDistributed() {
        assertTrue(ledgerService.isDistributed());
        assertEquals("redis", ledgerService.backendName());
    }

    @Test
    @DisplayName("分布式：限额=1 时第二次 tryReserve 必须拒绝（CAS，无双花）")
    void distributed_secondReserve_rejected() {
        registerKey(1L, 0L, 0);

        Optional<QuotaLimitViolation> first =
                enforcementService.tryReserve(request(), KEY_ID, 1L);
        assertFalse(first.isPresent(), "第一笔应放行");

        Optional<QuotaLimitViolation> second =
                enforcementService.tryReserve(request(), KEY_ID, 1L);
        assertTrue(second.isPresent(), "第二笔应超限拒绝");
        assertEquals(QuotaWindow.DAY, second.get().window());

        QuotaCounterKey key = new QuotaCounterKey(QuotaDimension.ofApiKey(KEY_ID),
                QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));
        long[] values = template.values(redisBackend.keyOf(key));
        assertEquals(1L, values[0], "Redis DAY 权威计数应恰好为 1，不得双花");
    }

    @Test
    @DisplayName("分布式：限额=0 不限制，可连续预留")
    void distributed_unlimited_allowsMultiple() {
        registerKey(0L, 0L, 0);

        assertTrue(enforcementService.tryReserve(request(), KEY_ID, 1L).isEmpty());
        assertTrue(enforcementService.tryReserve(request(), KEY_ID, 1L).isEmpty());
        assertTrue(enforcementService.tryReserve(request(), KEY_ID, 1L).isEmpty());
    }

    @Nested
    @DisplayName("并发 CAS")
    class Concurrent {

        @Test
        @DisplayName("并发 8 线程、限额=3：成功次数 == 3")
        void concurrent_respectsDailyRequestLimit() throws Exception {
            registerKey(3L, 0L, 0);
            final int threads = 8;
            final AtomicInteger allowed = new AtomicInteger();
            final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
            final java.util.concurrent.ExecutorService pool =
                    java.util.concurrent.Executors.newFixedThreadPool(threads);
            try {
                for (int i = 0; i < threads; i++) {
                    pool.execute(() -> {
                        try {
                            start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                            if (enforcementService.tryReserve(request(), KEY_ID, 1L).isEmpty()) {
                                allowed.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }
            assertEquals(3, allowed.get(), "分布式 CAS 下成功预留数必须等于限额");
        }
    }
}

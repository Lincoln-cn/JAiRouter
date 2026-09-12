package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.quota.InMemoryQuotaLedgerRepository;
import org.unreal.modelrouter.auth.security.quota.QuotaDecision;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaRequest;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApiKeyQuotaService} 账本委派（v3.1 PR-1）测试。
 *
 * <p>被测对象是真实 {@link ApiKeyQuotaService}（字段注入真实 {@link QuotaLedgerService}）与
 * 真实账本服务 + 手写内存仓库；仅 {@link ApiKeyService} / {@link TokenBucketRateLimiter}
 * 沿用例既有的 Mockito 协作方夹具（与 {@code ApiKeyQuotaServiceTest} 保持一致）。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyQuotaService 账本委派测试")
class ApiKeyQuotaServiceLedgerTest {

    private static final String KEY_ID = "key-1";

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private TokenBucketRateLimiter rateLimiter;

    @InjectMocks
    private ApiKeyQuotaService quotaService;

    private InMemoryQuotaLedgerRepository ledgerRepository;
    private QuotaProperties quotaProperties;
    private QuotaLedgerService ledgerService;
    private Map<String, ApiKey> apiKeyCache;

    @BeforeEach
    void setUp() {
        apiKeyCache = new HashMap<>();
        final Map<String, String> keyIdIndex = new HashMap<>();
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);

        ledgerRepository = InMemoryQuotaLedgerRepository.create();
        quotaProperties = new QuotaProperties();
        quotaProperties.setEnabled(true);
        ledgerService = new QuotaLedgerService(ledgerRepository.proxy(), quotaProperties, Clock.systemDefaultZone());
        ReflectionTestUtils.setField(quotaService, "quotaLedgerService", ledgerService);

        // 旧内存路径固定为「今日 50 请求 / 10000 token」，便于区分数据来源
        apiKeyCache.put("hash-" + KEY_ID, legacyApiKey(50L, 10000L));
        keyIdIndex.put(KEY_ID, "hash-" + KEY_ID);
    }

    @Test
    @DisplayName("账本启用：今日用量来自账本而非内存统计")
    void ledgerEnabled_shouldReadTodayUsageFromLedger() {
        reserveToday(3, 400L);

        final ApiKeyQuotaService.QuotaUsageDetail detail = quotaService.getQuotaUsage(KEY_ID).orElseThrow();

        assertEquals(3L, detail.getTodayRequestCount());
        assertEquals(1200L, detail.getTodayTokenUsage());
        assertEquals(1000L, detail.getDailyRequestLimit(), "返回结构保持不变");
    }

    @Test
    @DisplayName("账本关闭：完全走既有内存逻辑（含零账本数据库访问）")
    void ledgerDisabled_shouldFallBackToLegacyUsage() {
        reserveToday(2, 500L);
        quotaProperties.setEnabled(false);
        final int invocationsBefore = ledgerRepository.invocationCount();

        final ApiKeyQuotaService.QuotaUsageDetail detail = quotaService.getQuotaUsage(KEY_ID).orElseThrow();

        assertEquals(50L, detail.getTodayRequestCount());
        assertEquals(10000L, detail.getTodayTokenUsage());
        assertEquals(invocationsBefore, ledgerRepository.invocationCount(), "账本关闭时不得访问账本");
    }

    @Test
    @DisplayName("账本启用但无今日数据：回退既有内存路径")
    void ledgerEnabledWithoutTodayData_shouldFallBackToLegacyUsage() {
        final ApiKeyQuotaService.QuotaUsageDetail detail = quotaService.getQuotaUsage(KEY_ID).orElseThrow();

        assertEquals(50L, detail.getTodayRequestCount());
        assertEquals(10000L, detail.getTodayTokenUsage());
    }

    @Test
    @DisplayName("账本启用但数据库不可用：reserve 降级放行，管理侧查询回退既有内存路径")
    void ledgerUnavailable_shouldFallBackToLegacyUsage() {
        ledgerRepository.setFailing(true);

        final QuotaDecision decision = ledgerService.reserve(QuotaRequest.of(QuotaDimension.ofApiKey(KEY_ID), 100));
        assertTrue(decision.allowed());
        assertTrue(decision.degraded());

        final ApiKeyQuotaService.QuotaUsageDetail detail = quotaService.getQuotaUsage(KEY_ID).orElseThrow();

        assertEquals(50L, detail.getTodayRequestCount());
        assertEquals(10000L, detail.getTodayTokenUsage());
    }

    @Test
    @DisplayName("重置每日配额：同步清空账本，避免出现“重置后用量不归零”")
    void resetDailyQuota_shouldAlsoResetLedger() {
        reserveToday(2, 500L);
        when(apiKeyService.resetDailyQuota(KEY_ID)).thenReturn(Mono.empty());

        quotaService.resetDailyQuota(KEY_ID);

        verify(apiKeyService).resetDailyQuota(KEY_ID);
        verify(rateLimiter).reset(KEY_ID);
        assertTrue(ledgerService.usageAll(KEY_ID).isEmpty(), "账本中该 Key 的用量应被清空");
        assertEquals(50L, quotaService.getQuotaUsage(KEY_ID).orElseThrow().getTodayRequestCount(),
            "账本清空后回退既有内存路径");
    }

    @Test
    @DisplayName("账本驱动告警：账本用量触达阈值时告警列表随之变化")
    void alerts_shouldFollowLedgerUsage() {
        // 旧内存值 50/100 未达 80% 阈值；账本值 90/100 达阈值
        apiKeyCache.get("hash-" + KEY_ID).setDailyRequestLimit(100L);
        reserveToday(90, 1L);

        assertEquals(1, quotaService.getAlerts().size(), "告警应基于账本用量计算");
        assertEquals(1, quotaService.getAllQuotaUsage().size());
        assertEquals(90L, quotaService.getQuotaUsage(KEY_ID).orElseThrow().getTodayRequestCount());
    }

    /**
     * 通过真实账本服务写入今日用量。
     *
     * @param requests 请求次数
     * @param tokens   每次预留的 token 数
     */
    private void reserveToday(final int requests, final long tokens) {
        final QuotaDimension dimension = QuotaDimension.ofApiKey(KEY_ID);
        for (int i = 0; i < requests; i++) {
            final QuotaDecision decision = ledgerService.reserve(QuotaRequest.of(dimension, tokens));
            assertTrue(decision.allowed());
            assertFalse(decision.degraded());
        }
    }

    /**
     * 构造带今日用量的 API Key（模拟既有内存统计）。
     *
     * @param todayRequests 今日请求数
     * @param todayTokens   今日 token 数
     * @return API Key
     */
    private static ApiKey legacyApiKey(final long todayRequests, final long todayTokens) {
        final String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        final Map<String, Long> dailyUsage = new HashMap<>();
        dailyUsage.put(today, todayRequests);
        final Map<String, Long> dailyTokenUsage = new HashMap<>();
        dailyTokenUsage.put(today, todayTokens);

        final UsageStatistics usage = UsageStatistics.builder()
            .totalRequests(200L)
            .successfulRequests(180L)
            .failedRequests(20L)
            .dailyUsage(dailyUsage)
            .dailyTokenUsage(dailyTokenUsage)
            .build();

        return ApiKey.builder()
            .keyId(KEY_ID)
            .keyHash("hash-" + KEY_ID)
            .description("测试 Key " + KEY_ID)
            .dailyRequestLimit(1000L)
            .dailyTokenLimit(500000L)
            .rateLimitPerMinute(60)
            .quotaAlertThreshold(0.8)
            .enabled(true)
            .usage(usage)
            .build();
    }
}

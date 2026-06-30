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
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ApiKeyQuotaService 单元测试
 *
 * @author JAiRouter Team
 * @since v2.7.6
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyQuotaService 配额管理服务测试")
class ApiKeyQuotaServiceTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private TokenBucketRateLimiter rateLimiter;

    @InjectMocks
    private ApiKeyQuotaService quotaService;

    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() {
        apiKeyCache = new HashMap<>();
        keyIdIndex = new HashMap<>();
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);
    }

    private ApiKey createMockApiKey(String keyId, long dailyRequestLimit,
                                     long dailyTokenLimit, int rateLimitPerMinute) {
        String keyHash = "hash-" + keyId;
        String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, Long> dailyUsage = new HashMap<>();
        dailyUsage.put(today, 50L);
        Map<String, Long> dailyTokenUsage = new HashMap<>();
        dailyTokenUsage.put(today, 10000L);

        UsageStatistics usage = UsageStatistics.builder()
            .totalRequests(200L)
            .successfulRequests(180L)
            .failedRequests(20L)
            .dailyUsage(dailyUsage)
            .dailyTokenUsage(dailyTokenUsage)
            .build();

        ApiKey apiKey = ApiKey.builder()
            .keyId(keyId)
            .keyHash(keyHash)
            .description("测试 Key " + keyId)
            .dailyRequestLimit(dailyRequestLimit)
            .dailyTokenLimit(dailyTokenLimit)
            .rateLimitPerMinute(rateLimitPerMinute)
            .quotaAlertThreshold(0.8)
            .enabled(true)
            .usage(usage)
            .build();

        apiKeyCache.put(keyHash, apiKey);
        keyIdIndex.put(keyId, keyHash);
        return apiKey;
    }

    @Test
    @DisplayName("getQuotaUsage - 正常返回配额详情")
    void getQuotaUsage_normal_shouldReturnDetail() {
        createMockApiKey("key-1", 1000, 500000, 60);
        when(rateLimiter.getCurrentCount("key-1")).thenReturn(10);

        Optional<ApiKeyQuotaService.QuotaUsageDetail> result = quotaService.getQuotaUsage("key-1");

        assertTrue(result.isPresent());
        ApiKeyQuotaService.QuotaUsageDetail detail = result.get();
        assertEquals("key-1", detail.getKeyId());
        assertEquals(1000, detail.getDailyRequestLimit());
        assertEquals(500000, detail.getDailyTokenLimit());
        assertEquals(60, detail.getRateLimitPerMinute());
        assertEquals(50, detail.getTodayRequestCount());
        assertEquals(10000, detail.getTodayTokenUsage());
        assertEquals(10, detail.getCurrentRatePerMinute());
        assertEquals(200, detail.getTotalRequests());
        // 50/1000 = 5%
        assertEquals(5.0, detail.getDailyRequestUsagePercent(), 0.1);
        // 10000/500000 = 2%
        assertEquals(2.0, detail.getDailyTokenUsagePercent(), 0.1);
        assertFalse(detail.isAlertTriggered());
    }

    @Test
    @DisplayName("getQuotaUsage - Key 不存在返回 empty")
    void getQuotaUsage_keyNotFound_shouldReturnEmpty() {
        Optional<ApiKeyQuotaService.QuotaUsageDetail> result = quotaService.getQuotaUsage("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getQuotaUsage - 无限额时使用百分比为 -1")
    void getQuotaUsage_noLimits_shouldReturnNegativeOne() {
        createMockApiKey("key-nolimit", 0, 0, 0);

        Optional<ApiKeyQuotaService.QuotaUsageDetail> result = quotaService.getQuotaUsage("key-nolimit");

        assertTrue(result.isPresent());
        assertEquals(-1, result.get().getDailyRequestUsagePercent());
        assertEquals(-1, result.get().getDailyTokenUsagePercent());
        assertFalse(result.get().isAlertTriggered());
    }

    @Test
    @DisplayName("getQuotaUsage - 超过告警阈值触发告警")
    void getQuotaUsage_exceedThreshold_shouldTriggerAlert() {
        createMockApiKey("key-alert", 100, 100000, 10);
        // 手动设置今日请求数为 85（85% > 80% 阈值）
        ApiKey ak = apiKeyCache.get("hash-key-alert");
        ak.getUsage().getDailyUsage().put(
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE), 85L);

        Optional<ApiKeyQuotaService.QuotaUsageDetail> result = quotaService.getQuotaUsage("key-alert");

        assertTrue(result.isPresent());
        assertTrue(result.get().isAlertTriggered());
        assertEquals(85.0, result.get().getDailyRequestUsagePercent(), 0.1);
    }

    @Test
    @DisplayName("getAlerts - 返回所有触发告警的 Key")
    void getAlerts_shouldReturnTriggeredKeys() {
        createMockApiKey("key-ok", 1000, 500000, 60);
        createMockApiKey("key-warn", 100, 100000, 10);

        // key-warn 超过 80% 阈值
        ApiKey ak = apiKeyCache.get("hash-key-warn");
        ak.getUsage().getDailyUsage().put(
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE), 85L);

        List<ApiKeyQuotaService.QuotaAlertInfo> alerts = quotaService.getAlerts();

        assertEquals(1, alerts.size());
        assertEquals("key-warn", alerts.get(0).getKeyId());
        assertNotNull(alerts.get(0).getMessage());
    }

    @Test
    @DisplayName("getAlerts - 无告警时返回空列表")
    void getAlerts_noAlerts_shouldReturnEmptyList() {
        createMockApiKey("key-1", 1000, 500000, 60);

        List<ApiKeyQuotaService.QuotaAlertInfo> alerts = quotaService.getAlerts();
        assertTrue(alerts.isEmpty());
    }

    @Test
    @DisplayName("getAllQuotaUsage - 返回所有 Key 的配额详情")
    void getAllQuotaUsage_shouldReturnAllDetails() {
        createMockApiKey("key-1", 1000, 500000, 60);
        createMockApiKey("key-2", 2000, 1000000, 120);

        List<ApiKeyQuotaService.QuotaUsageDetail> overview = quotaService.getAllQuotaUsage();

        assertEquals(2, overview.size());
    }

    @Test
    @DisplayName("resetDailyQuota - 重置配额和速率限制")
    void resetDailyQuota_shouldResetBoth() {
        createMockApiKey("key-reset", 1000, 500000, 60);
        when(apiKeyService.resetDailyQuota("key-reset")).thenReturn(reactor.core.publisher.Mono.empty());

        quotaService.resetDailyQuota("key-reset");

        verify(apiKeyService).resetDailyQuota("key-reset");
        verify(rateLimiter).reset("key-reset");
    }

    @Test
    @DisplayName("cleanupExpiredDailyUsage - 清理过期数据")
    void cleanupExpiredDailyUsage_shouldRemoveOldEntries() {
        String keyId = "key-cleanup";
        String keyHash = "hash-key-cleanup";

        // 创建包含过期日期的数据
        String oldDate = LocalDateTime.now().minusDays(60)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);
        String recentDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, Long> dailyUsage = new HashMap<>();
        dailyUsage.put(oldDate, 100L);
        dailyUsage.put(recentDate, 50L);

        Map<String, Long> dailyTokenUsage = new HashMap<>();
        dailyTokenUsage.put(oldDate, 5000L);
        dailyTokenUsage.put(recentDate, 2000L);

        UsageStatistics usage = UsageStatistics.builder()
            .totalRequests(200L)
            .successfulRequests(180L)
            .failedRequests(20L)
            .dailyUsage(dailyUsage)
            .dailyTokenUsage(dailyTokenUsage)
            .build();

        ApiKey apiKey = ApiKey.builder()
            .keyId(keyId)
            .keyHash(keyHash)
            .usage(usage)
            .build();

        apiKeyCache.put(keyHash, apiKey);
        keyIdIndex.put(keyId, keyHash);

        int cleaned = quotaService.cleanupExpiredDailyUsage();

        assertEquals(2, cleaned, "应清理 2 条过期记录（dailyUsage + dailyTokenUsage 各 1 条）");
        assertNull(dailyUsage.get(oldDate));
        assertNotNull(dailyUsage.get(recentDate));
        assertNull(dailyTokenUsage.get(oldDate));
        assertNotNull(dailyTokenUsage.get(recentDate));
    }
}

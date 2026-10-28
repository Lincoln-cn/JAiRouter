package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * #711：一次认证只允许给「今日请求数」加 1。
 *
 * <p>{@code UsageStatistics#incrementRequest} 内部已经把当天计数 +1，而
 * {@code ApiKeyService#doUpdateUsageStatistics} 过去又额外 {@code put} 了一次 ⇒ 每个请求被计两次，
 * 日配额（{@code ApiKeyQuotaService} 读 {@code getDailyUsage()}）提前一半就用光，
 * 概览页的今日请求数也是真实值的两倍。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("#711 今日请求数只计一次")
class ApiKeyUsageStatisticsCountingTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private ApiKeyValidator apiKeyValidator;

    @Mock
    private ApiKeyBatchService apiKeyBatchService;

    @Mock
    private ApiKeyPersistenceService apiKeyPersistenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApiKeyService apiKeyService;
    private ApiKey testApiKey;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        apiKeyService = new ApiKeyService(storeManager, objectMapper, securityProperties);
        setField("apiKeyValidator", apiKeyValidator);
        setField("apiKeyBatchService", apiKeyBatchService);
        setField("apiKeyPersistenceService", apiKeyPersistenceService);
        setField("eventPublisher", eventPublisher);

        final Map<String, ApiKey> apiKeyCache = (Map<String, ApiKey>) readField("apiKeyCache");
        final Map<String, String> keyIdIndex = (Map<String, String>) readField("keyIdIndex");

        testApiKey = ApiKey.builder()
                .keyId("test-key-id")
                .keyHash(ApiKeyHashUtil.hashApiKey("sk-test-key-12345678"))
                .keyPrefix("sk-")
                .description("Test API Key")
                .permissions(List.of("chat"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .createdBy("system")
                .usage(UsageStatistics.builder()
                        .totalRequests(0L)
                        .successfulRequests(0L)
                        .failedRequests(0L)
                        .dailyUsage(new HashMap<>())
                        .dailyTokenUsage(new HashMap<>())
                        .build())
                .build();

        apiKeyCache.put(testApiKey.getKeyHash(), testApiKey);
        keyIdIndex.put(testApiKey.getKeyId(), testApiKey.getKeyHash());
    }

    @Test
    @DisplayName("CNT-711-001 两次成功认证 ⇒ 今日请求数正好 2（不是 4）")
    void twoSuccessfulCalls_countTodayTwice() {
        apiKeyService.updateUsageStatistics("test-key-id", true);
        apiKeyService.updateUsageStatistics("test-key-id", true);

        final UsageStatistics usage = testApiKey.getUsage();
        final String today = LocalDate.now().toString();
        assertNotNull(usage.getDailyUsage().get(today), "今日计数必须存在");
        assertEquals(2L, usage.getDailyUsage().get(today), "每个请求只能计一次");
        assertEquals(2L, usage.getTotalRequests(), "总请求数也必须是 2");
        assertEquals(2L, usage.getSuccessfulRequests());
        assertEquals(0L, usage.getFailedRequests());
    }

    @Test
    @DisplayName("CNT-711-002 成功一次 + 失败一次 ⇒ 今日请求数 2，失败计数 1")
    void mixedOutcomes_stillCountOnceEach() {
        apiKeyService.updateUsageStatistics("test-key-id", true);
        apiKeyService.updateUsageStatistics("test-key-id", false);

        final UsageStatistics usage = testApiKey.getUsage();
        final String today = LocalDate.now().toString();
        assertEquals(2L, usage.getDailyUsage().get(today));
        assertEquals(2L, usage.getTotalRequests());
        assertEquals(1L, usage.getSuccessfulRequests());
        assertEquals(1L, usage.getFailedRequests());
        assertEquals(0.5, usage.getSuccessRate(), 1e-9);
    }

    private void setField(final String name, final Object value) throws Exception {
        final Field field = ApiKeyService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(apiKeyService, value);
    }

    private Object readField(final String name) throws Exception {
        final Field field = ApiKeyService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(apiKeyService);
    }
}

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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * #811：认证热路径上的用量统计只能回写「自己这一条」，不得整表覆盖。
 *
 * <p>历史形态：{@code doUpdateUsageStatistics} 每次认证都调
 * {@code ApiKeyPersistenceService#saveApiKeysToStore(apiKeyCache)}，把本副本内存里的全部 Key
 * 序列化后整份覆盖存储。两个后果：① 每个请求都要把全部 Key 序列化一遍（写放大，Key 越多越慢）；
 * ② 多副本场景下，A 副本的覆盖写会把 B 副本刚创建/更新的 Key 按 A 的内存快照回滚掉。</p>
 *
 * <p>本测试钉住修复后的形态：调用 {@code saveSingleApiKey(cache, keyHash)}，且全程不出现整表写。
 * 断言是双向的 —— 少了单个写会红（统计丢失），出现整表写也会红（回归到覆盖写）。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.6
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("#811 用量统计只回写单个 Key")
class ApiKeyUsageStatisticsSingleKeySaveTest {

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
    private Map<String, ApiKey> apiKeyCache;
    private ApiKey testApiKey;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        apiKeyService = new ApiKeyService(storeManager, objectMapper, securityProperties);
        setField("apiKeyValidator", apiKeyValidator);
        setField("apiKeyBatchService", apiKeyBatchService);
        setField("apiKeyPersistenceService", apiKeyPersistenceService);
        setField("eventPublisher", eventPublisher);

        apiKeyCache = (Map<String, ApiKey>) readField("apiKeyCache");
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
    @DisplayName("811-001 一次成功认证 ⇒ 只回写自己这条，不再整表覆盖")
    void usageUpdate_writesSingleKeyOnly() {
        apiKeyService.updateUsageStatistics("test-key-id", true);

        verify(apiKeyPersistenceService).saveSingleApiKey(apiKeyCache, testApiKey.getKeyHash());
        verify(apiKeyPersistenceService, never()).saveApiKeysToStore(any());
    }

    @Test
    @DisplayName("811-002 失败认证同样只回写自己这条")
    void failedUsageUpdate_writesSingleKeyOnly() {
        apiKeyService.updateUsageStatistics("test-key-id", false);

        verify(apiKeyPersistenceService).saveSingleApiKey(apiKeyCache, testApiKey.getKeyHash());
        verify(apiKeyPersistenceService, never()).saveApiKeysToStore(any());
    }

    @Test
    @DisplayName("811-003 未知 Key 不触发任何持久化")
    void unknownKey_writesNothing() {
        apiKeyService.updateUsageStatistics("no-such-key-id", true);

        verify(apiKeyPersistenceService, never()).saveSingleApiKey(any(), any());
        verify(apiKeyPersistenceService, never()).saveApiKeysToStore(any());
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

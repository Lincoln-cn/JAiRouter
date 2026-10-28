package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #715：Token 用量更新只落盘单个 Key。
 *
 * <p>此前每次用量更新都调用 {@code saveApiKeysToStore(cache)}，把内存缓存里的全部 Key 整份覆盖
 * 存储；多副本下会把别的副本刚写的 Key 状态一起回滚。本用例验证新路径：读回存储里的最新文档、
 * 只替换目标 Key，其余条目沿用存储版本。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("单个 API Key 的增量落盘（#715）")
class ApiKeyPersistenceSingleKeySaveTest {

    private static final String STORE_KEY = "security.api-keys";

    private static final String DEFAULT_STORE_KEY = "security.api-keys";

    @Mock
    private StoreManager storeManager;

    private ApiKeyPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyPersistenceService(storeManager, new ObjectMapper());
    }

    @Test
    @DisplayName("PST-715-001 只覆盖目标 Key，其余条目沿用存储版本")
    void onlyTargetKeyIsOverwritten() {
        final Map<String, Object> storedConfig = new HashMap<>();
        storedConfig.put(ApiKeyPersistenceService.STORE_API_KEYS,
                new ArrayList<>(List.of(apiKeyAsStoredMap("hash-b", "key-b", 5L))));
        when(storeManager.getConfig(DEFAULT_STORE_KEY)).thenReturn(storedConfig);

        final Map<String, ApiKey> cache = new ConcurrentHashMap<>();
        cache.put("hash-a", apiKey("key-a", "hash-a", 42L));
        cache.put("hash-b", apiKey("key-b", "hash-b", 0L));

        service.saveSingleApiKey(cache, "hash-a");

        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(storeManager).saveConfig(eq(DEFAULT_STORE_KEY), captor.capture());
        final List<?> saved = (List<?>) captor.getValue().get(ApiKeyPersistenceService.STORE_API_KEYS);
        assertNotNull(saved);
        assertEquals(2, saved.size(), "目标 Key 与存储里已有的其它 Key 都要在，实际: " + saved.size());
        assertEquals(42L, tokensOf(saved, "hash-a"), "目标 Key 用内存里的最新值");
        assertEquals(5L, tokensOf(saved, "hash-b"),
                "非目标 Key 必须沿用存储版本，不能被本副本的陈旧快照覆盖");
    }

    private static long tokensOf(final List<?> saved, final String keyHash) {
        for (final Object item : saved) {
            final ApiKey apiKey = item instanceof ApiKey ak
                    ? ak
                    : new ObjectMapper().convertValue(item, ApiKey.class);
            if (keyHash.equals(apiKey.getKeyHash())) {
                return apiKey.getUsage().getDailyTokenUsage()
                        .values().stream().mapToLong(Long::longValue).sum();
            }
        }
        return -1L;
    }

    private static ApiKey apiKey(final String keyId, final String keyHash, final long tokens) {
        final ApiKey apiKey = new ApiKey();
        apiKey.setKeyId(keyId);
        apiKey.setKeyHash(keyHash);
        final UsageStatistics statistics = new UsageStatistics();
        statistics.incrementTokens(tokens);
        apiKey.setUsage(statistics);
        return apiKey;
    }

    private static Map<String, Object> apiKeyAsStoredMap(final String keyHash, final String keyId, final long tokens) {
        final Map<String, Object> map = new HashMap<>();
        map.put("keyId", keyId);
        map.put("keyHash", keyHash);
        map.put("usage", Map.of("dailyTokenUsage", Map.of("2026-01-01", tokens)));
        return map;
    }
}

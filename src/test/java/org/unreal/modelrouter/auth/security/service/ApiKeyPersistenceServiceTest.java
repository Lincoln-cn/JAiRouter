package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApiKeyPersistenceService#refreshApiKeyCache} 单元测试（#162）。
 *
 * <p>覆盖多副本下缓存刷新必须成立的两条语义：<b>补入</b>兄弟副本新建的 Key，以及
 * <b>移除</b>兄弟副本已删除的 Key（后者是「吊销跨副本生效」的关键）；并锁定「不回写存储」
 * 与「读不到配置时不清空缓存」两个安全边界。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyPersistenceServiceTest {

    private static final String STORE_KEY = "security.api-keys";

    @Mock
    private StoreManager storeManager;

    private ApiKeyPersistenceService service;
    private Map<String, ApiKey> cache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() {
        service = new ApiKeyPersistenceService(storeManager, new ObjectMapper());
        cache = new ConcurrentHashMap<>();
        keyIdIndex = new ConcurrentHashMap<>();
    }

    private static Map<String, Object> entry(final String keyId, final String keyHash) {
        Map<String, Object> item = new HashMap<>();
        item.put("keyId", keyId);
        item.put("keyHash", keyHash);
        return item;
    }

    private static ApiKey cached(final String keyId, final String keyHash) {
        return ApiKey.builder().keyId(keyId).keyHash(keyHash).build();
    }

    private void storeHas(final int version, final List<Map<String, Object>> entries) {
        when(storeManager.getConfigVersions(STORE_KEY)).thenReturn(List.of(version));
        Map<String, Object> config = new HashMap<>();
        config.put("apiKeys", entries);
        when(storeManager.getConfigByVersion(STORE_KEY, version)).thenReturn(config);
    }

    @Test
    @DisplayName("刷新补入其它副本新建的 Key")
    void refreshAddsKeysCreatedOnOtherReplicas() {
        storeHas(2, List.of(entry("k1", "h1"), entry("k2", "h2")));

        int refreshed = service.refreshApiKeyCache(cache, keyIdIndex);

        assertEquals(2, refreshed);
        assertEquals(2, cache.size());
        assertEquals("h1", keyIdIndex.get("k1"));
        assertEquals("h2", keyIdIndex.get("k2"));
    }

    @Test
    @DisplayName("刷新移除其它副本已删除的 Key——吊销跨副本生效的关键")
    void refreshRemovesKeysDeletedOnOtherReplicas() {
        cache.put("h1", cached("k1", "h1"));
        cache.put("h2", cached("k2", "h2"));
        keyIdIndex.put("k1", "h1");
        keyIdIndex.put("k2", "h2");
        storeHas(3, List.of(entry("k1", "h1")));

        int refreshed = service.refreshApiKeyCache(cache, keyIdIndex);

        assertEquals(1, refreshed);
        assertTrue(cache.containsKey("h1"));
        assertFalse(cache.containsKey("h2"), "已从存储删除的 Key 必须从缓存移除");
        assertFalse(keyIdIndex.containsKey("k2"), "ID 索引中的对应条目也要清除");
    }

    @Test
    @DisplayName("刷新后 keyId 索引与缓存内容保持一致")
    void refreshKeepsKeyIdIndexConsistentWithCache() {
        cache.put("h1", cached("k1", "h1"));
        keyIdIndex.put("k1", "h1");
        keyIdIndex.put("ghost", "h-ghost");
        storeHas(2, List.of(entry("k1", "h1"), entry("k3", "h3")));

        service.refreshApiKeyCache(cache, keyIdIndex);

        assertEquals(2, cache.size());
        assertEquals(2, keyIdIndex.size());
        assertEquals("h3", keyIdIndex.get("k3"));
        assertFalse(keyIdIndex.containsKey("ghost"));
    }

    @Test
    @DisplayName("存储中尚无配置版本时保留现有缓存")
    void refreshKeepsCacheWhenStoreHasNoVersion() {
        cache.put("h1", cached("k1", "h1"));
        when(storeManager.getConfigVersions(STORE_KEY)).thenReturn(List.of());

        assertEquals(-1, service.refreshApiKeyCache(cache, keyIdIndex));
        assertEquals(1, cache.size());
        assertTrue(cache.containsKey("h1"));
    }

    @Test
    @DisplayName("存储条目为空时保留现有缓存，避免把有效 Key 全部剔除")
    void refreshKeepsCacheWhenEntriesEmpty() {
        cache.put("h1", cached("k1", "h1"));
        storeHas(1, List.of());

        assertEquals(-1, service.refreshApiKeyCache(cache, keyIdIndex));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("存储读取异常时保留现有缓存并返回 -1，不向外抛")
    void refreshKeepsCacheOnStoreFailure() {
        cache.put("h1", cached("k1", "h1"));
        when(storeManager.getConfigVersions(STORE_KEY)).thenThrow(new IllegalStateException("store down"));

        assertEquals(-1, service.refreshApiKeyCache(cache, keyIdIndex));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("刷新不回写存储——回写会用本副本旧视图覆盖兄弟副本的变更")
    void refreshDoesNotWriteBackToStore() {
        storeHas(2, List.of(entry("k1", "h1")));

        service.refreshApiKeyCache(cache, keyIdIndex);

        verify(storeManager, never()).saveConfig(anyString(), any());
    }

    @Test
    @DisplayName("缓存中既无旧值也无新值时，刷新后不会残留陈旧条目")
    void refreshReplacesStaleEntries() {
        cache.put("h-stale", cached("k-stale", "h-stale"));
        storeHas(4, List.of(entry("k-new", "h-new")));

        service.refreshApiKeyCache(cache, keyIdIndex);

        assertEquals(1, cache.size());
        assertTrue(cache.containsKey("h-new"));
        assertFalse(cache.containsKey("h-stale"));
    }
}

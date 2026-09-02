package org.unreal.modelrouter.router.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2.9.9: CaffeineCacheStore 单元测试
 *
 * <p>验证：put/get / 过期（短 TTL）/ 最大 size 淘汰 / null 不缓存 / size 统计。
 */
@DisplayName("CaffeineCacheStore 测试")
class CaffeineCacheStoreTest {

    private ResponseCacheProperties properties;
    private CaffeineCacheStore cacheStore;

    @BeforeEach
    void setUp() {
        properties = new ResponseCacheProperties();
        properties.setMaxSize(100);
        properties.setTtl(Duration.ofMinutes(1));
        cacheStore = new CaffeineCacheStore(properties);
    }

    @Test
    @DisplayName("put 后可 get 命中")
    void putAndGet() {
        cacheStore.put("key-1", Map.of("choices", "hello"), properties.getTtl());

        Optional<Object> result = cacheStore.get("key-1");
        assertTrue(result.isPresent());
        assertEquals(Map.of("choices", "hello"), result.get());
        assertEquals(1, cacheStore.size());
    }

    @Test
    @DisplayName("未命中返回 empty")
    void missReturnsEmpty() {
        Optional<Object> result = cacheStore.get("not-exists");
        assertFalse(result.isPresent());
        assertEquals(0, cacheStore.size());
    }

    @Test
    @DisplayName("短 TTL 到期后不再命中（含 ±10% 抖动余量）")
    void expireAfterShortTtl() throws Exception {
        properties.setTtl(Duration.ofMillis(100));
        cacheStore.put("expire-key", "data", properties.getTtl());

        // TTL 抖动上限为 110ms，睡 400ms 保证过期
        Thread.sleep(400);

        assertFalse(cacheStore.get("expire-key").isPresent());
        assertEquals(0, cacheStore.size());
    }

    @Test
    @DisplayName("超过 maxSize 触发淘汰")
    void maxSizeEviction() {
        properties.setMaxSize(2);
        // maximumSize 在构造 Caffeine builder 时读取一次，修改属性后必须重建 store 才能生效
        cacheStore = new CaffeineCacheStore(properties);
        cacheStore.put("k1", "v1", properties.getTtl());
        cacheStore.put("k2", "v2", properties.getTtl());
        cacheStore.put("k3", "v3", properties.getTtl());
        cacheStore.put("k4", "v4", properties.getTtl());
        cacheStore.put("k5", "v5", properties.getTtl());

        assertTrue(cacheStore.size() <= 2, "缓存条目数应不超过 maxSize=2，实际=" + cacheStore.size());
        assertTrue(cacheStore.get("k1").isEmpty() || cacheStore.get("k2").isEmpty()
                        || cacheStore.get("k3").isEmpty(),
                "先写入的条目应已被淘汰");
    }

    @Test
    @DisplayName("null 数据与 null/空白键不缓存")
    void nullValuesNotCached() {
        cacheStore.put("null-data", null, properties.getTtl());
        cacheStore.put(null, "data", properties.getTtl());
        cacheStore.put("   ", "data", properties.getTtl());

        assertEquals(0, cacheStore.size());
        assertFalse(cacheStore.get("null-data").isPresent());
        assertFalse(cacheStore.get("   ").isPresent());
    }

    @Test
    @DisplayName("覆盖写入更新值且条目数不变")
    void overwriteUpdatesValue() {
        cacheStore.put("key-1", "old", properties.getTtl());
        cacheStore.put("key-1", "new", properties.getTtl());

        Optional<Object> result = cacheStore.get("key-1");
        assertTrue(result.isPresent());
        assertEquals("new", result.get());
        assertEquals(1, cacheStore.size());
    }
}

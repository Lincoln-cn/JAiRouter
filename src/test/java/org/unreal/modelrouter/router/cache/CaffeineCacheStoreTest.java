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

    // ==================== v2.9.10: delete / clear / deleteByPrefix ====================

    @Test
    @DisplayName("delete 删除指定键")
    void deleteRemovesKey() {
        cacheStore.put("key-1", "v1", properties.getTtl());
        cacheStore.put("key-2", "v2", properties.getTtl());

        cacheStore.delete("key-1");

        assertFalse(cacheStore.get("key-1").isPresent());
        assertTrue(cacheStore.get("key-2").isPresent());
        assertEquals(1, cacheStore.size());
    }

    @Test
    @DisplayName("delete null/空白键不做操作")
    void deleteNullKeyNoOp() {
        cacheStore.put("key-1", "v1", properties.getTtl());

        cacheStore.delete(null);
        cacheStore.delete("  ");

        assertTrue(cacheStore.get("key-1").isPresent());
        assertEquals(1, cacheStore.size());
    }

    @Test
    @DisplayName("delete 不存在的键不报错")
    void deleteNonExistentKeyNoError() {
        cacheStore.put("key-1", "v1", properties.getTtl());

        cacheStore.delete("not-exists");

        assertTrue(cacheStore.get("key-1").isPresent());
        assertEquals(1, cacheStore.size());
    }

    @Test
    @DisplayName("deleteByPrefix 按前缀批量删除")
    void deleteByPrefixRemovesMatchingKeys() {
        cacheStore.put("rc:chat:gpt-4:abc", "v1", properties.getTtl());
        cacheStore.put("rc:chat:gpt-4:def", "v2", properties.getTtl());
        cacheStore.put("rc:chat:gpt-3.5:ghi", "v3", properties.getTtl());
        cacheStore.put("rc:embedding:em-1:jkl", "v4", properties.getTtl());

        cacheStore.deleteByPrefix("rc:chat:gpt-4:");

        assertFalse(cacheStore.get("rc:chat:gpt-4:abc").isPresent());
        assertFalse(cacheStore.get("rc:chat:gpt-4:def").isPresent());
        assertTrue(cacheStore.get("rc:chat:gpt-3.5:ghi").isPresent());
        assertTrue(cacheStore.get("rc:embedding:em-1:jkl").isPresent());
        assertEquals(2, cacheStore.size());
    }

    @Test
    @DisplayName("deleteByPrefix 按服务类型前缀删除")
    void deleteByServiceTypePrefix() {
        cacheStore.put("rc:chat:m1:aaa", "v1", properties.getTtl());
        cacheStore.put("rc:chat:m2:bbb", "v2", properties.getTtl());
        cacheStore.put("rc:embedding:m3:ccc", "v3", properties.getTtl());

        cacheStore.deleteByPrefix("rc:chat:");

        assertFalse(cacheStore.get("rc:chat:m1:aaa").isPresent());
        assertFalse(cacheStore.get("rc:chat:m2:bbb").isPresent());
        assertTrue(cacheStore.get("rc:embedding:m3:ccc").isPresent());
        assertEquals(1, cacheStore.size());
    }

    @Test
    @DisplayName("deleteByPrefix null/空白前缀不做操作")
    void deleteByPrefixNullNoOp() {
        cacheStore.put("key-1", "v1", properties.getTtl());

        cacheStore.deleteByPrefix(null);
        cacheStore.deleteByPrefix("  ");

        assertEquals(1, cacheStore.size());
    }

    @Test
    @DisplayName("deleteByPrefix 无匹配条目不报错")
    void deleteByPrefixNoMatchNoError() {
        cacheStore.put("rc:chat:m1:aaa", "v1", properties.getTtl());

        cacheStore.deleteByPrefix("rc:tts:");

        assertEquals(1, cacheStore.size());
    }

    @Test
    @DisplayName("clear 清空全部缓存")
    void clearRemovesAllEntries() {
        cacheStore.put("key-1", "v1", properties.getTtl());
        cacheStore.put("key-2", "v2", properties.getTtl());
        cacheStore.put("key-3", "v3", properties.getTtl());

        cacheStore.clear();

        assertEquals(0, cacheStore.size());
        assertFalse(cacheStore.get("key-1").isPresent());
        assertFalse(cacheStore.get("key-2").isPresent());
        assertFalse(cacheStore.get("key-3").isPresent());
    }
}

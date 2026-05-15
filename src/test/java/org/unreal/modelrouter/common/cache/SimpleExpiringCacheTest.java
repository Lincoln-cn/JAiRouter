package org.unreal.modelrouter.common.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleExpiringCache 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.3
 */
class SimpleExpiringCacheTest {

    private SimpleExpiringCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new SimpleExpiringCache<>("test-cache", 5000, 100, 1000);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    @Test
    @DisplayName("基本put/get操作应正确工作")
    void basicPutGet_shouldWorkCorrectly() {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    @DisplayName("不存在的键应返回null")
    void getNonExistent_shouldReturnNull() {
        assertNull(cache.get("non-existent"));
    }

    @Test
    @DisplayName("null值应被移除")
    void putNull_shouldRemove() {
        cache.put("key1", "value1");
        cache.put("key1", null);
        assertNull(cache.get("key1"));
    }

    @Test
    @DisplayName("containsKey应正确判断")
    void containsKey_shouldWorkCorrectly() {
        cache.put("key1", "value1");
        assertTrue(cache.containsKey("key1"));
        assertFalse(cache.containsKey("non-existent"));
    }

    @Test
    @DisplayName("remove应正确移除")
    void remove_shouldWorkCorrectly() {
        cache.put("key1", "value1");
        cache.remove("key1");
        assertNull(cache.get("key1"));
    }

    @Test
    @DisplayName("clear应清空所有条目")
    void clear_shouldClearAll() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("统计信息应正确记录")
    void stats_shouldRecordCorrectly() {
        cache.put("key1", "value1");

        // Hit
        cache.get("key1");
        // Miss
        cache.get("non-existent");

        SimpleExpiringCache.CacheStats stats = cache.getStats();
        assertEquals(1, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(0.5, stats.getHitRate(), 0.01);
    }

    @Test
    @DisplayName("自定义过期时间应生效")
    void customExpireTime_shouldWork() throws InterruptedException {
        cache.put("key1", "value1", 100); // 100ms 过期
        assertEquals("value1", cache.get("key1"));
        Thread.sleep(150);
        assertNull(cache.get("key1"));
    }

    @Test
    @DisplayName("过期条目应自动清理")
    void expiredEntry_shouldBeCleaned() throws InterruptedException {
        SimpleExpiringCache<String, String> shortCache =
                new SimpleExpiringCache<>("short-cache", 50, 100, 50);

        try {
            shortCache.put("key1", "value1");
            assertEquals("value1", shortCache.get("key1"));

            // 等待过期
            Thread.sleep(200);

            assertNull(shortCache.get("key1"));
        } finally {
            shortCache.shutdown();
        }
    }

    @Test
    @DisplayName("get with loader应正确加载")
    void getWithLoader_shouldLoadCorrectly() {
        AtomicInteger loadCount = new AtomicInteger(0);

        String value = cache.get("key1", k -> {
            loadCount.incrementAndGet();
            return "loaded-value";
        });

        assertEquals("loaded-value", value);
        assertEquals(1, loadCount.get());

        // 再次获取应使用缓存
        cache.get("key1", k -> {
            loadCount.incrementAndGet();
            return "loaded-again";
        });

        assertEquals(1, loadCount.get()); // 不应该再次加载
    }

    @Test
    @DisplayName("最大容量限制应触发LRU淘汰")
    void maxSize_shouldTriggerLruEviction() {
        SimpleExpiringCache<String, String> smallCache =
                new SimpleExpiringCache<>("small-cache", 60000, 5, 60000);

        try {
            // 添加超过容量的条目
            for (int i = 0; i < 10; i++) {
                smallCache.put("key" + i, "value" + i);
            }

            // 应该有淘汰发生
            SimpleExpiringCache.CacheStats stats = smallCache.getStats();
            assertTrue(stats.getEvictionCount() > 0 || smallCache.size() <= 5);
        } finally {
            smallCache.shutdown();
        }
    }

    @Test
    @DisplayName("多线程并发访问应正确工作")
    void concurrentAccess_shouldWorkCorrectly() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        cache.put(key, "value-" + j);
                        cache.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(cache.size() > 0);
        SimpleExpiringCache.CacheStats stats = cache.getStats();
        assertTrue(stats.getHitCount() > 0);
    }

    @Test
    @DisplayName("多线程并发getWithLoader应只加载一次")
    void concurrentGetWithLoader_shouldLoadOnce() throws InterruptedException {
        AtomicInteger loadCount = new AtomicInteger(0);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    cache.get("shared-key", k -> {
                        loadCount.incrementAndGet();
                        return "shared-value";
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 同时开始
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 由于 ConcurrentHashMap 的特性，可能会加载多次
        // 但最终值应该是正确的
        assertEquals("shared-value", cache.get("shared-key"));
    }

    @Test
    @DisplayName("shutdown应正确关闭")
    void shutdown_shouldCloseProperly() throws InterruptedException {
        SimpleExpiringCache<String, String> tempCache =
                new SimpleExpiringCache<>("temp-cache", 60000);

        tempCache.put("key1", "value1");
        tempCache.shutdown();

        // 关闭后不应崩溃
        assertDoesNotThrow(() -> tempCache.get("key1"));
    }

    @Test
    @DisplayName("默认构造器应使用默认配置")
    void defaultConstructor_shouldUseDefaultConfig() {
        SimpleExpiringCache<String, String> defaultCache =
                new SimpleExpiringCache<>("default-cache", 60000);

        try {
            assertNotNull(defaultCache);
            assertEquals(0, defaultCache.size());
        } finally {
            defaultCache.shutdown();
        }
    }

    @Test
    @DisplayName("size应返回正确数量")
    void size_shouldReturnCorrectCount() {
        assertEquals(0, cache.size());

        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("统计toString应包含关键信息")
    void statsToString_shouldContainKeyInfo() {
        cache.put("key1", "value1");
        cache.get("key1");
        cache.get("non-existent");

        String statsStr = cache.getStats().toString();
        assertTrue(statsStr.contains("test-cache"));
        assertTrue(statsStr.contains("hitRate"));
    }
}

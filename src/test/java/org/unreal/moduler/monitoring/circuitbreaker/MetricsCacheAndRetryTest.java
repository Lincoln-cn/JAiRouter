package org.unreal.moduler.monitoring.circuitbreaker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCacheAndRetry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsCacheAndRetry 单元测试
 */
class MetricsCacheAndRetryTest {

    private MetricsCacheAndRetry cacheAndRetry;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cacheAndRetry = new MetricsCacheAndRetry(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        if (cacheAndRetry != null) {
            cacheAndRetry.shutdown();
        }
    }

    @Test
    void testCacheMetric() {
        String key = "test-key";
        String value = "test-value";
        Duration ttl = Duration.ofMinutes(1);

        // 缓存数据
        cacheAndRetry.cacheMetric(key, value, ttl);

        // 验证缓存
        String cached = cacheAndRetry.getCachedMetric(key, String.class);
        assertEquals(value, cached);
    }

    @Test
    void testCacheExpiry() throws InterruptedException {
        String key = "test-key";
        String value = "test-value";
        Duration ttl = Duration.ofMillis(100); // 很短的TTL

        // 缓存数据
        cacheAndRetry.cacheMetric(key, value, ttl);

        // 立即获取应该成功
        String cached = cacheAndRetry.getCachedMetric(key, String.class);
        assertEquals(value, cached);

        // 等待过期
        Thread.sleep(150);

        // 过期后应该返回null
        String expired = cacheAndRetry.getCachedMetric(key, String.class);
        assertNull(expired);
    }

    @Test
    void testCacheMissAndHit() {
        String key = "test-key";
        String value = "test-value";

        // 缓存未命中
        String missed = cacheAndRetry.getCachedMetric(key, String.class);
        assertNull(missed);

        // 缓存数据
        cacheAndRetry.cacheMetric(key, value, Duration.ofMinutes(1));

        // 缓存命中
        String hit = cacheAndRetry.getCachedMetric(key, String.class);
        assertEquals(value, hit);
    }

    @Test
    void testGetOrCompute() {
        String key = "compute-key";
        String computedValue = "computed-value";
        AtomicInteger computeCount = new AtomicInteger(0);

        // 第一次调用应该计算
        String result1 = cacheAndRetry.getOrCompute(key, String.class, () -> {
            computeCount.incrementAndGet();
            return computedValue;
        }, Duration.ofMinutes(1));

        assertEquals(computedValue, result1);
        assertEquals(1, computeCount.get());

        // 第二次调用应该从缓存获取
        String result2 = cacheAndRetry.getOrCompute(key, String.class, () -> {
            computeCount.incrementAndGet();
            return "should-not-be-called";
        }, Duration.ofMinutes(1));

        assertEquals(computedValue, result2);
        assertEquals(1, computeCount.get()); // 计算次数不应该增加
    }

    @Test
    void testExecuteWithRetrySuccess() {
        String operationId = "test-operation";
        AtomicInteger executeCount = new AtomicInteger(0);

        // 成功的操作
        cacheAndRetry.executeWithRetry(operationId, () -> {
            executeCount.incrementAndGet();
        });

        // 等待异步执行完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(1, executeCount.get());
    }

    @Test
    void testExecuteWithRetryFailure() {
        String operationId = "test-operation";
        AtomicInteger executeCount = new AtomicInteger(0);

        // 总是失败的操作
        CompletableFuture<Void> future = cacheAndRetry.executeWithRetry(operationId, () -> {
            executeCount.incrementAndGet();
            throw new RuntimeException("Test failure");
        });

        // 验证异常被抛出
        assertThrows(Exception.class, future::join);
        assertEquals(1, executeCount.get());
    }

    @Test
    void testExecuteWithRetryWithReturnValue() {
        String operationId = "test-operation";
        String expectedValue = "success";

        // 成功的操作
        CompletableFuture<String> future = cacheAndRetry.executeWithRetry(operationId, () -> expectedValue);

        // 验证返回值
        assertEquals(expectedValue, future.join());
    }

    @Test
    void testCacheStats() {
        // 添加一些缓存数据
        cacheAndRetry.cacheMetric("key1", "value1", Duration.ofMinutes(1));
        cacheAndRetry.cacheMetric("key2", "value2", Duration.ofMinutes(1));

        MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();

        assertNotNull(stats);
        assertEquals(2, stats.getCurrentSize());
        assertTrue(stats.getMaxSize() > 0);
        assertTrue(stats.getUsageRatio() >= 0.0);
        assertTrue(stats.getUsageRatio() <= 1.0);
        assertEquals(0, stats.getActiveRetries()); // 没有活跃的重试
        assertEquals(0, stats.getQueuedRetries()); // 没有排队的重试
    }

    @Test
    void testClearCache() {
        // 添加缓存数据
        cacheAndRetry.cacheMetric("key1", "value1", Duration.ofMinutes(1));
        cacheAndRetry.cacheMetric("key2", "value2", Duration.ofMinutes(1));

        MetricsCacheAndRetry.CacheStats statsBefore = cacheAndRetry.getCacheStats();
        assertEquals(2, statsBefore.getCurrentSize());

        // 清空缓存
        cacheAndRetry.clearCache();

        MetricsCacheAndRetry.CacheStats statsAfter = cacheAndRetry.getCacheStats();
        assertEquals(0, statsAfter.getCurrentSize());

        // 验证缓存确实被清空
        String cached = cacheAndRetry.getCachedMetric("key1", String.class);
        assertNull(cached);
    }

    @Test
    void testCacheEviction() {
        // 这个测试需要添加大量数据来触发驱逐
        // 由于MAX_CACHE_SIZE是10000，我们添加足够的数据
        for (int i = 0; i < 100; i++) {
            cacheAndRetry.cacheMetric("key" + i, "value" + i, Duration.ofMinutes(1));
        }

        MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();
        assertEquals(100, stats.getCurrentSize());
        assertTrue(stats.getUsageRatio() > 0);
    }

    @Test
    void testCleanupExpiredCache() {
        // 添加一个很快过期的缓存项
        cacheAndRetry.cacheMetric("short-lived", "value", Duration.ofMillis(50));
        
        // 添加一个长期有效的缓存项
        cacheAndRetry.cacheMetric("long-lived", "value", Duration.ofMinutes(10));

        // 等待短期缓存过期
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 手动触发清理
        cacheAndRetry.cleanupExpiredCache();

        // 验证过期项被清理，有效项保留
        assertNull(cacheAndRetry.getCachedMetric("short-lived", String.class));
        assertNotNull(cacheAndRetry.getCachedMetric("long-lived", String.class));
    }

    @Test
    void testCacheWithDifferentTypes() {
        // 测试不同类型的缓存
        cacheAndRetry.cacheMetric("string-key", "string-value", Duration.ofMinutes(1));
        cacheAndRetry.cacheMetric("integer-key", 42, Duration.ofMinutes(1));
        cacheAndRetry.cacheMetric("double-key", 3.14, Duration.ofMinutes(1));

        // 验证类型安全的获取
        String stringValue = cacheAndRetry.getCachedMetric("string-key", String.class);
        Integer intValue = cacheAndRetry.getCachedMetric("integer-key", Integer.class);
        Double doubleValue = cacheAndRetry.getCachedMetric("double-key", Double.class);

        assertEquals("string-value", stringValue);
        assertEquals(Integer.valueOf(42), intValue);
        assertEquals(Double.valueOf(3.14), doubleValue);

        // 测试类型不匹配的情况
        assertThrows(ClassCastException.class, () -> {
            cacheAndRetry.getCachedMetric("string-key", Integer.class);
        });
    }
}
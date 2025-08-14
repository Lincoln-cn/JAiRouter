package org.unreal.moduler.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCacheAndRetry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsCacheAndRetry 单元测试
 */
class MetricsCacheAndRetryTest {

    private MeterRegistry meterRegistry;
    private MetricsCacheAndRetry cacheAndRetry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cacheAndRetry = new MetricsCacheAndRetry(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        cacheAndRetry.shutdown();
    }

    @Test
    void testCacheMetric() {
        String key = "test-key";
        String value = "test-value";
        Duration ttl = Duration.ofMinutes(1);

        // 缓存数据
        cacheAndRetry.cacheMetric(key, value, ttl);

        // 验证缓存统计
        MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();
        assertEquals(1, stats.getCurrentSize());
    }

    @Test
    void testGetCachedMetric() {
        String key = "test-key";
        String value = "test-value";
        Duration ttl = Duration.ofMinutes(1);

        // 缓存数据
        cacheAndRetry.cacheMetric(key, value, ttl);

        // 获取缓存数据
        String cachedValue = cacheAndRetry.getCachedMetric(key, String.class);
        assertEquals(value, cachedValue);

        // 获取不存在的数据
        String nonExistent = cacheAndRetry.getCachedMetric("non-existent", String.class);
        assertNull(nonExistent);
    }

    @Test
    void testGetOrCompute() {
        String key = "test-key";
        String computedValue = "computed-value";
        AtomicInteger computeCount = new AtomicInteger(0);

        // 第一次调用应该计算值
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
    void testExecuteWithRetrySuccess() throws ExecutionException, InterruptedException {
        String operationId = "test-operation";
        AtomicInteger counter = new AtomicInteger(0);

        CompletableFuture<Integer> future = cacheAndRetry.executeWithRetry(operationId, () -> {
            return counter.incrementAndGet();
        });

        Integer result = future.get();
        assertEquals(1, result);
        assertEquals(1, counter.get());
    }

    @Test
    void testExecuteWithRetryFailure() {
        String operationId = "test-operation";
        AtomicInteger attemptCount = new AtomicInteger(0);

        CompletableFuture<String> future = cacheAndRetry.executeWithRetry(operationId, () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Test error");
        });

        // 应该抛出异常
        assertThrows(ExecutionException.class, future::get);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testCacheExpiration() throws InterruptedException {
        String key = "test-key";
        String value = "test-value";
        Duration shortTtl = Duration.ofMillis(50);

        // 缓存数据
        cacheAndRetry.cacheMetric(key, value, shortTtl);

        // 立即获取应该成功
        String cachedValue = cacheAndRetry.getCachedMetric(key, String.class);
        assertEquals(value, cachedValue);

        // 等待过期
        Thread.sleep(100);

        // 过期后应该返回null
        String expiredValue = cacheAndRetry.getCachedMetric(key, String.class);
        assertNull(expiredValue);
    }

    @Test
    void testClearCache() {
        String key1 = "test-key-1";
        String key2 = "test-key-2";
        String value = "test-value";

        // 缓存多个数据
        cacheAndRetry.cacheMetric(key1, value, Duration.ofMinutes(1));
        cacheAndRetry.cacheMetric(key2, value, Duration.ofMinutes(1));

        MetricsCacheAndRetry.CacheStats statsBefore = cacheAndRetry.getCacheStats();
        assertEquals(2, statsBefore.getCurrentSize());

        // 清空缓存
        cacheAndRetry.clearCache();

        MetricsCacheAndRetry.CacheStats statsAfter = cacheAndRetry.getCacheStats();
        assertEquals(0, statsAfter.getCurrentSize());

        // 验证数据已被清除
        assertNull(cacheAndRetry.getCachedMetric(key1, String.class));
        assertNull(cacheAndRetry.getCachedMetric(key2, String.class));
    }

    @Test
    void testCacheStats() {
        String key = "test-key";
        String value = "test-value";

        // 初始状态
        MetricsCacheAndRetry.CacheStats initialStats = cacheAndRetry.getCacheStats();
        assertEquals(0, initialStats.getCurrentSize());
        assertTrue(initialStats.getMaxSize() > 0);

        // 添加缓存项
        cacheAndRetry.cacheMetric(key, value, Duration.ofMinutes(1));

        MetricsCacheAndRetry.CacheStats updatedStats = cacheAndRetry.getCacheStats();
        assertEquals(1, updatedStats.getCurrentSize());
        assertTrue(updatedStats.getUsageRatio() > 0);
    }

    @Test
    void testConcurrentCacheOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "thread-" + threadId + "-key-" + j;
                    String value = "value-" + j;
                    cacheAndRetry.cacheMetric(key, value, Duration.ofMinutes(1));
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证缓存大小
        MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();
        assertEquals(threadCount * operationsPerThread, stats.getCurrentSize());
    }

    @Test
    void testGetOrComputeWithException() {
        String key = "test-key";
        AtomicInteger attemptCount = new AtomicInteger(0);

        // 计算函数抛出异常
        String result = cacheAndRetry.getOrCompute(key, String.class, () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Computation failed");
        }, Duration.ofMinutes(1));

        assertNull(result);
        assertEquals(1, attemptCount.get());

        // 缓存中不应该有数据
        String cachedValue = cacheAndRetry.getCachedMetric(key, String.class);
        assertNull(cachedValue);
    }

    @Test
    void testExecuteWithRetryRunnable() throws InterruptedException {
        String operationId = "test-operation";
        AtomicInteger counter = new AtomicInteger(0);

        // 执行成功的操作
        cacheAndRetry.executeWithRetry(operationId, counter::incrementAndGet);

        // 给异步操作一些时间完成
        Thread.sleep(100);

        assertEquals(1, counter.get());
    }

    @Test
    void testCacheUsageRatio() {
        MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();
        int maxSize = stats.getMaxSize();
        
        // 添加一些缓存项
        for (int i = 0; i < maxSize / 10; i++) {
            cacheAndRetry.cacheMetric("key-" + i, "value-" + i, Duration.ofMinutes(1));
        }

        MetricsCacheAndRetry.CacheStats updatedStats = cacheAndRetry.getCacheStats();
        double expectedRatio = (double) (maxSize / 10) / maxSize;
        assertEquals(expectedRatio, updatedStats.getUsageRatio(), 0.01);
    }

    @Test
    void testCleanupExpiredCache() throws InterruptedException {
        String key = "test-key";
        String value = "test-value";
        Duration shortTtl = Duration.ofMillis(50);

        // 缓存数据
        cacheAndRetry.cacheMetric(key, value, shortTtl);

        MetricsCacheAndRetry.CacheStats statsBefore = cacheAndRetry.getCacheStats();
        assertEquals(1, statsBefore.getCurrentSize());

        // 等待过期
        Thread.sleep(100);

        // 手动触发清理
        cacheAndRetry.cleanupExpiredCache();

        MetricsCacheAndRetry.CacheStats statsAfter = cacheAndRetry.getCacheStats();
        assertEquals(0, statsAfter.getCurrentSize());
    }
}
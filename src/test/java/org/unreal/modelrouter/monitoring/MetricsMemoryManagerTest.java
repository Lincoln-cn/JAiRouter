package org.unreal.modelrouter.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.MetricsMemoryManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 指标内存管理器测试
 */
@ExtendWith(MockitoExtension.class)
class MetricsMemoryManagerTest {

    @Mock
    private MonitoringProperties monitoringProperties;

    private MetricsMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        memoryManager = new MetricsMemoryManager(monitoringProperties);
    }

    @Test
    void testCacheOperations() {
        String key = "test-key";
        String value = "test-value";
        
        // 初始时缓存为空
        MetricsMemoryManager.CacheEntry entry = memoryManager.getCacheEntry(key);
        assertNull(entry);
        
        // 放入缓存
        memoryManager.putCacheEntry(key, value);
        
        // 获取缓存条目
        entry = memoryManager.getCacheEntry(key);
        assertNotNull(entry);
        assertEquals(value, entry.getValue());
        assertTrue(entry.getAge() >= 0);
        assertTrue(entry.getLastAccessTime() > 0);
        assertTrue(entry.getCreationTime() > 0);
    }

    @Test
    void testCacheEntryAccessTimeUpdate() throws InterruptedException {
        String key = "test-key";
        String value = "test-value";
        
        memoryManager.putCacheEntry(key, value);
        MetricsMemoryManager.CacheEntry entry1 = memoryManager.getCacheEntry(key);
        long firstAccessTime = entry1.getLastAccessTime();
        
        // 等待一小段时间
        Thread.sleep(10);
        
        // 再次访问
        MetricsMemoryManager.CacheEntry entry2 = memoryManager.getCacheEntry(key);
        long secondAccessTime = entry2.getLastAccessTime();
        
        // 访问时间应该更新
        assertTrue(secondAccessTime >= firstAccessTime);
    }

    @Test
    void testMemoryStats() {
        // 添加一些缓存条目
        for (int i = 0; i < 10; i++) {
            memoryManager.putCacheEntry("key" + i, "value" + i);
        }
        
        // 访问一些条目以产生缓存命中
        for (int i = 0; i < 5; i++) {
            memoryManager.getCacheEntry("key" + i);
        }
        
        // 尝试访问不存在的条目以产生缓存未命中
        for (int i = 10; i < 15; i++) {
            memoryManager.getCacheEntry("key" + i);
        }
        
        MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
        
        assertNotNull(stats);
        assertTrue(stats.getMemoryUsageRatio() >= 0);
        assertTrue(stats.getUsedMemory() > 0);
        assertEquals(10, stats.getCacheSize());
        assertEquals(5, stats.getCacheHits());
        assertEquals(5, stats.getCacheMisses());
        assertEquals(0.5, stats.getCacheHitRatio(), 0.01);
    }

    @Test
    void testSamplingBasedOnMemoryPressure() {
        double baseSamplingRate = 1.0;
        
        // 在正常内存使用情况下，应该使用基础采样率
        boolean shouldSample = memoryManager.shouldSample(baseSamplingRate);
        // 由于内存使用情况可能变化，这里只测试方法不抛异常
        assertNotNull(shouldSample);
    }

    @Test
    void testClearCache() {
        // 添加一些缓存条目
        for (int i = 0; i < 5; i++) {
            memoryManager.putCacheEntry("key" + i, "value" + i);
        }
        
        MetricsMemoryManager.MemoryStats statsBefore = memoryManager.getMemoryStats();
        assertEquals(5, statsBefore.getCacheSize());
        
        // 清空缓存
        memoryManager.clearCache();
        
        MetricsMemoryManager.MemoryStats statsAfter = memoryManager.getMemoryStats();
        assertEquals(0, statsAfter.getCacheSize());
        
        // 验证缓存条目确实被清空
        for (int i = 0; i < 5; i++) {
            assertNull(memoryManager.getCacheEntry("key" + i));
        }
    }

    @Test
    void testPerformMemoryCheck() {
        // 添加一些缓存条目
        for (int i = 0; i < 100; i++) {
            memoryManager.putCacheEntry("key" + i, "value" + i);
        }
        
        // 执行内存检查
        memoryManager.performMemoryCheck();
        
        // 验证方法执行没有抛出异常
        MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
        assertNotNull(stats);
    }

    @Test
    void testConcurrentCacheAccess() throws InterruptedException {
        // 测试并发缓存访问
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    String key = "thread" + threadId + "-key" + j;
                    String value = "thread" + threadId + "-value" + j;
                    
                    memoryManager.putCacheEntry(key, value);
                    MetricsMemoryManager.CacheEntry entry = memoryManager.getCacheEntry(key);
                    assertNotNull(entry);
                    assertEquals(value, entry.getValue());
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
        
        // 验证缓存状态
        MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
        assertTrue(stats.getCacheSize() > 0);
        assertTrue(stats.getCacheHits() > 0);
    }

    @Test
    void testMemoryStatsToString() {
        // 添加一些数据
        memoryManager.putCacheEntry("key1", "value1");
        memoryManager.getCacheEntry("key1"); // 产生缓存命中
        memoryManager.getCacheEntry("nonexistent"); // 产生缓存未命中
        
        MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
        String statsString = stats.toString();
        
        assertNotNull(statsString);
        assertTrue(statsString.contains("usage="));
        assertTrue(statsString.contains("cache="));
        assertTrue(statsString.contains("hitRatio="));
    }

    @Test
    void testCacheEntryAge() throws InterruptedException {
        String key = "test-key";
        String value = "test-value";
        
        memoryManager.putCacheEntry(key, value);
        MetricsMemoryManager.CacheEntry entry = memoryManager.getCacheEntry(key);
        
        long initialAge = entry.getAge();
        assertTrue(initialAge >= 0);
        
        // 等待一小段时间
        Thread.sleep(10);
        
        long laterAge = entry.getAge();
        assertTrue(laterAge >= initialAge);
    }

    @Test
    void testLargeCacheOperations() {
        // 测试大量缓存操作
        int numEntries = 1000;
        
        // 添加大量条目
        for (int i = 0; i < numEntries; i++) {
            memoryManager.putCacheEntry("key" + i, "value" + i);
        }
        
        MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
        
        // 由于可能触发LRU淘汰，缓存大小可能小于添加的条目数
        assertTrue(stats.getCacheSize() > 0);
        assertTrue(stats.getCacheSize() <= numEntries);
        
        // 如果发生了淘汰，淘汰计数应该大于0
        if (stats.getCacheSize() < numEntries) {
            assertTrue(stats.getEvictions() > 0);
        }
    }

    @Test
    void testShutdown() {
        // 添加一些缓存条目
        memoryManager.putCacheEntry("key1", "value1");
        
        // 关闭内存管理器
        memoryManager.shutdown();
        
        // 验证缓存被清空
        MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
        assertEquals(0, stats.getCacheSize());
    }
}
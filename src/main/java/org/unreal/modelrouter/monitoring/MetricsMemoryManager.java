package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Conditional;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 指标内存管理器
 * 提供内存优化、LRU缓存和内存监控功能
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class MetricsMemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(MetricsMemoryManager.class);

    // 内存阈值配置
    private static final double MEMORY_WARNING_THRESHOLD = 0.8;  // 80%内存使用率告警
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.9; // 90%内存使用率紧急清理
    private static final long MAX_CACHE_SIZE = 10000;            // 最大缓存条目数
    private static final long CLEANUP_INTERVAL_SECONDS = 30;     // 清理间隔

    private final MonitoringProperties monitoringProperties;
    private final MemoryMXBean memoryMXBean;
    private final ScheduledExecutorService cleanupExecutor;
    
    // 缓存管理
    private final ConcurrentHashMap<String, CacheEntry> metricsCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> accessOrder = new ConcurrentLinkedQueue<>();
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // 统计信息
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong memoryCleanups = new AtomicLong(0);

    public MetricsMemoryManager(MonitoringProperties monitoringProperties) {
        this.monitoringProperties = monitoringProperties;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.cleanupExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "metrics-memory-manager");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void initialize() {
        // 启动定期内存检查和清理
        cleanupExecutor.scheduleAtFixedRate(
            this::performMemoryCheck,
            CLEANUP_INTERVAL_SECONDS,
            CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        logger.info("MetricsMemoryManager initialized with max cache size: {}", MAX_CACHE_SIZE);
    }

    /**
     * 检查内存使用情况并执行必要的清理
     */
    public void performMemoryCheck() {
        try {
            double memoryUsage = getCurrentMemoryUsage();
            
            if (memoryUsage >= MEMORY_CRITICAL_THRESHOLD) {
                logger.warn("Critical memory usage detected: {:.2f}%, performing aggressive cleanup", memoryUsage * 100);
                performAggressiveCleanup();
                memoryCleanups.incrementAndGet();
            } else if (memoryUsage >= MEMORY_WARNING_THRESHOLD) {
                logger.info("High memory usage detected: {:.2f}%, performing gentle cleanup", memoryUsage * 100);
                performGentleCleanup();
            }
            
            // 检查缓存大小
            if (metricsCache.size() > MAX_CACHE_SIZE) {
                performLRUEviction();
            }
            
        } catch (Exception e) {
            logger.error("Error during memory check", e);
        }
    }

    /**
     * 获取当前内存使用率
     */
    private double getCurrentMemoryUsage() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        
        if (max <= 0) {
            // 如果max为-1（未定义），使用committed作为参考
            max = heapUsage.getCommitted();
        }
        
        return max > 0 ? (double) used / max : 0.0;
    }

    /**
     * 执行温和清理
     */
    private void performGentleCleanup() {
        cacheLock.writeLock().lock();
        try {
            // 清理25%的最旧条目
            int targetSize = (int) (metricsCache.size() * 0.75);
            evictToSize(targetSize);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 执行激进清理
     */
    private void performAggressiveCleanup() {
        cacheLock.writeLock().lock();
        try {
            // 清理50%的最旧条目
            int targetSize = (int) (metricsCache.size() * 0.5);
            evictToSize(targetSize);
            
            // 强制垃圾回收
            System.gc();
            
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 执行LRU淘汰
     */
    private void performLRUEviction() {
        cacheLock.writeLock().lock();
        try {
            evictToSize((int) (MAX_CACHE_SIZE * 0.8)); // 淘汰到80%容量
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 淘汰缓存条目到指定大小
     */
    private void evictToSize(int targetSize) {
        while (metricsCache.size() > targetSize && !accessOrder.isEmpty()) {
            String oldestKey = accessOrder.poll();
            if (oldestKey != null && metricsCache.remove(oldestKey) != null) {
                evictions.incrementAndGet();
            }
        }
    }

    /**
     * 获取缓存条目
     */
    public CacheEntry getCacheEntry(String key) {
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = metricsCache.get(key);
            if (entry != null) {
                // 更新访问时间
                entry.updateAccessTime();
                cacheHits.incrementAndGet();
                return entry;
            } else {
                cacheMisses.incrementAndGet();
                return null;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * 放入缓存条目
     */
    public void putCacheEntry(String key, Object value) {
        cacheLock.writeLock().lock();
        try {
            // 检查是否需要淘汰
            if (metricsCache.size() >= MAX_CACHE_SIZE) {
                performLRUEviction();
            }
            
            CacheEntry entry = new CacheEntry(value);
            metricsCache.put(key, entry);
            accessOrder.offer(key);
            
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 检查是否应该采样（基于内存压力）
     */
    public boolean shouldSample(double baseSamplingRate) {
        double memoryUsage = getCurrentMemoryUsage();
        
        if (memoryUsage >= MEMORY_CRITICAL_THRESHOLD) {
            // 内存紧张时，大幅降低采样率
            return Math.random() < (baseSamplingRate * 0.1);
        } else if (memoryUsage >= MEMORY_WARNING_THRESHOLD) {
            // 内存告警时，适度降低采样率
            return Math.random() < (baseSamplingRate * 0.5);
        } else {
            // 内存正常时，使用基础采样率
            return Math.random() < baseSamplingRate;
        }
    }

    /**
     * 获取内存统计信息
     */
    public MemoryStats getMemoryStats() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        
        return new MemoryStats(
            getCurrentMemoryUsage(),
            heapUsage.getUsed(),
            heapUsage.getMax(),
            metricsCache.size(),
            cacheHits.get(),
            cacheMisses.get(),
            evictions.get(),
            memoryCleanups.get()
        );
    }

    /**
     * 清空所有缓存
     */
    public void clearCache() {
        cacheLock.writeLock().lock();
        try {
            metricsCache.clear();
            accessOrder.clear();
            logger.info("Metrics cache cleared");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 关闭内存管理器
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clearCache();
        logger.info("MetricsMemoryManager shutdown completed");
    }

    /**
     * 缓存条目类
     */
    public static class CacheEntry {
        private final Object value;
        private volatile long lastAccessTime;
        private final long creationTime;

        public CacheEntry(Object value) {
            this.value = value;
            this.creationTime = System.currentTimeMillis();
            this.lastAccessTime = creationTime;
        }

        public Object getValue() {
            return value;
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        public long getAge() {
            return System.currentTimeMillis() - creationTime;
        }
    }

    /**
     * 内存统计信息类
     */
    public static class MemoryStats {
        private final double memoryUsageRatio;
        private final long usedMemory;
        private final long maxMemory;
        private final int cacheSize;
        private final long cacheHits;
        private final long cacheMisses;
        private final long evictions;
        private final long memoryCleanups;

        public MemoryStats(double memoryUsageRatio, long usedMemory, long maxMemory, int cacheSize,
                          long cacheHits, long cacheMisses, long evictions, long memoryCleanups) {
            this.memoryUsageRatio = memoryUsageRatio;
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
            this.cacheSize = cacheSize;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.evictions = evictions;
            this.memoryCleanups = memoryCleanups;
        }

        // Getters
        public double getMemoryUsageRatio() { return memoryUsageRatio; }
        public long getUsedMemory() { return usedMemory; }
        public long getMaxMemory() { return maxMemory; }
        public int getCacheSize() { return cacheSize; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getEvictions() { return evictions; }
        public long getMemoryCleanups() { return memoryCleanups; }

        public double getCacheHitRatio() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format("MemoryStats{usage=%.2f%%, cache=%d, hitRatio=%.2f%%, evictions=%d, cleanups=%d}",
                               memoryUsageRatio * 100, cacheSize, getCacheHitRatio() * 100, evictions, memoryCleanups);
        }
    }
}
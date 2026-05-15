package org.unreal.modelrouter.common.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ByteBuffer 池化管理器
 *
 * 减少频繁分配/释放 ByteBuffer 带来的 GC 压力。
 * 使用分桶策略管理不同大小的缓冲区。
 *
 * 特点：
 * - 按大小分桶管理
 * - 自动回收和复用
 * - 内存使用统计
 * - 定期清理空闲缓冲区
 *
 * @author JAiRouter Team
 * @since 2.7.5
 */
public class ByteBufferPool {

    private static final Logger logger = LoggerFactory.getLogger(ByteBufferPool.class);

    /**
     * 默认桶大小配置
     */
    private static final int[] DEFAULT_BUCKET_SIZES = {
            1024,      // 1KB
            4096,      // 4KB
            16384,     // 16KB
            65536,     // 64KB
            262144,    // 256KB
            1048576    // 1MB
    };

    /**
     * 每个桶的最大容量
     */
    private static final int MAX_BUCKET_CAPACITY = 50;

    /**
     * 缓冲区池：按大小分桶
     */
    private final Map<Integer, Queue<ByteBuffer>> pools = new ConcurrentHashMap<>();

    /**
     * 桶大小配置
     */
    private final int[] bucketSizes;

    /**
     * 每个桶的最大容量
     */
    private final int maxBucketCapacity;

    /**
     * 统计：分配次数
     */
    private final AtomicLong allocationCount = new AtomicLong(0);

    /**
     * 统计：复用次数
     */
    private final AtomicLong reuseCount = new AtomicLong(0);

    /**
     * 统计：回收次数
     */
    private final AtomicLong recycleCount = new AtomicLong(0);

    /**
     * 统计：丢弃次数（桶已满）
     */
    private final AtomicLong discardCount = new AtomicLong(0);

    /**
     * 清理执行器
     */
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * 创建 ByteBuffer 池
     *
     * @param bucketSizes 桶大小配置
     * @param maxBucketCapacity 每个桶最大容量
     * @param cleanupIntervalMs 清理间隔（毫秒）
     */
    public ByteBufferPool(final int[] bucketSizes, final int maxBucketCapacity, final long cleanupIntervalMs) {
        this.bucketSizes = bucketSizes.clone();
        this.maxBucketCapacity = maxBucketCapacity;

        // 初始化池
        for (int size : bucketSizes) {
            pools.put(size, new ConcurrentLinkedQueue<>());
        }

        // 启动定期清理
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bytebuffer-pool-cleanup");
            t.setDaemon(true);
            return t;
        });

        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupIdleBuffers,
                cleanupIntervalMs,
                cleanupIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("创建ByteBuffer池: buckets={}, maxCapacity={}, cleanupInterval={}ms",
                bucketSizes.length, maxBucketCapacity, cleanupIntervalMs);
    }

    /**
     * 使用默认配置创建
     */
    public ByteBufferPool() {
        this(DEFAULT_BUCKET_SIZES, MAX_BUCKET_CAPACITY, 60000);
    }

    /**
     * 获取缓冲区
     *
     * @param requiredSize 所需大小
     * @return ByteBuffer
     */
    public ByteBuffer acquire(final int requiredSize) {
        int bucketSize = findBucketSize(requiredSize);

        Queue<ByteBuffer> pool = pools.get(bucketSize);
        if (pool != null) {
            ByteBuffer buffer = pool.poll();
            if (buffer != null) {
                buffer.clear(); // 重置位置
                reuseCount.incrementAndGet();
                return buffer;
            }
        }

        // 池中没有，创建新的
        allocationCount.incrementAndGet();
        return ByteBuffer.allocate(bucketSize);
    }

    /**
     * 释放缓冲区
     *
     * @param buffer 缓冲区
     */
    public void release(final ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }

        int bucketSize = findBucketSize(buffer.capacity());
        Queue<ByteBuffer> pool = pools.get(bucketSize);

        if (pool == null) {
            // 没有对应的桶，直接丢弃
            discardCount.incrementAndGet();
            return;
        }

        if (pool.size() >= maxBucketCapacity) {
            // 桶已满，丢弃
            discardCount.incrementAndGet();
            return;
        }

        buffer.clear();
        pool.offer(buffer);
        recycleCount.incrementAndGet();
    }

    /**
     * 查找合适的桶大小
     */
    private int findBucketSize(final int requiredSize) {
        for (int size : bucketSizes) {
            if (size >= requiredSize) {
                return size;
            }
        }
        // 超过最大桶，返回实际需要的容量
        return requiredSize;
    }

    /**
     * 清理空闲缓冲区
     */
    private void cleanupIdleBuffers() {
        int totalCleaned = 0;

        for (Map.Entry<Integer, Queue<ByteBuffer>> entry : pools.entrySet()) {
            Queue<ByteBuffer> pool = entry.getValue();
            int currentSize = pool.size();

            // 如果池超过半满，清理一半
            if (currentSize > maxBucketCapacity / 2) {
                int toClean = currentSize / 2;
                for (int i = 0; i < toClean; i++) {
                    pool.poll();
                    totalCleaned++;
                }
            }
        }

        if (totalCleaned > 0) {
            discardCount.addAndGet(totalCleaned);
            logger.debug("清理空闲缓冲区: cleaned={}", totalCleaned);
        }
    }

    /**
     * 获取池统计信息
     */
    public PoolStats getStats() {
        int totalPooled = 0;
        long totalMemory = 0;

        for (Map.Entry<Integer, Queue<ByteBuffer>> entry : pools.entrySet()) {
            int size = entry.getValue().size();
            totalPooled += size;
            totalMemory += (long) size * entry.getKey();
        }

        return new PoolStats(
                totalPooled,
                totalMemory,
                allocationCount.get(),
                reuseCount.get(),
                recycleCount.get(),
                discardCount.get()
        );
    }

    /**
     * 清空所有池
     */
    public void clear() {
        for (Queue<ByteBuffer> pool : pools.values()) {
            pool.clear();
        }
        logger.info("ByteBuffer池已清空");
    }

    /**
     * 关闭池
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

        clear();
        logger.info("ByteBuffer池已关闭: stats={}", getStats());
    }

    /**
     * 池统计信息
     */
    public static class PoolStats {
        private final int pooledBuffers;
        private final long pooledMemory;
        private final long allocationCount;
        private final long reuseCount;
        private final long recycleCount;
        private final long discardCount;

        public PoolStats(final int pooledBuffers, final long pooledMemory,
                        final long allocationCount, final long reuseCount,
                        final long recycleCount, final long discardCount) {
            this.pooledBuffers = pooledBuffers;
            this.pooledMemory = pooledMemory;
            this.allocationCount = allocationCount;
            this.reuseCount = reuseCount;
            this.recycleCount = recycleCount;
            this.discardCount = discardCount;
        }

        public int getPooledBuffers() { return pooledBuffers; }
        public long getPooledMemory() { return pooledMemory; }
        public long getAllocationCount() { return allocationCount; }
        public long getReuseCount() { return reuseCount; }
        public long getRecycleCount() { return recycleCount; }
        public long getDiscardCount() { return discardCount; }

        public double getReuseRate() {
            long total = allocationCount + reuseCount;
            return total == 0 ? 0.0 : (double) reuseCount / total;
        }

        @Override
        public String toString() {
            return String.format("PoolStats{pooled=%d, memory=%dKB, allocs=%d, reuses=%d, reuseRate=%.1f%%}",
                    pooledBuffers, pooledMemory / 1024, allocationCount, reuseCount, getReuseRate() * 100);
        }
    }
}

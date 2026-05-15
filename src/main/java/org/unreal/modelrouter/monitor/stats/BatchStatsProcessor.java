package org.unreal.modelrouter.monitor.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 批量统计处理器
 *
 * 收集统计事件并批量处理，减少数据库写入频率。
 *
 * 特点：
 * - 内存缓冲区收集事件
 * - 定时批量刷新
 * - 最大批次限制
 * - 背压保护
 *
 * @author JAiRouter Team
 * @since 2.7.4
 */
public class BatchStatsProcessor implements Consumer<StatsUpdateEvent> {

    private static final Logger logger = LoggerFactory.getLogger(BatchStatsProcessor.class);

    /**
     * 默认批次大小
     */
    private static final int DEFAULT_BATCH_SIZE = 100;

    /**
     * 默认刷新间隔（毫秒）
     */
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 1000;

    /**
     * 最大缓冲区大小（背压保护）
     */
    private static final int MAX_BUFFER_SIZE = 10000;

    /**
     * 事件缓冲区
     */
    private final List<StatsUpdateEvent> buffer;

    /**
     * 批次大小
     */
    private final int batchSize;

    /**
     * 刷新间隔
     */
    private final long flushIntervalMs;

    /**
     * 批量处理器
     */
    private final Consumer<List<StatsUpdateEvent>> batchHandler;

    /**
     * 定时刷新执行器
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 统计计数器
     */
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong batchCount = new AtomicLong(0);

    /**
     * 聚合统计（按 serviceType:modelName 聚合）
     */
    private final Map<String, ConcurrentModelCallStats> aggregatedStats = new ConcurrentHashMap<>();

    /**
     * 创建批量处理器
     *
     * @param batchSize 批次大小
     * @param flushIntervalMs 刷新间隔（毫秒）
     * @param batchHandler 批量处理器
     */
    public BatchStatsProcessor(final int batchSize, final long flushIntervalMs,
                               final Consumer<List<StatsUpdateEvent>> batchHandler) {
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.batchHandler = batchHandler;
        this.buffer = new ArrayList<>(batchSize * 2);

        // 启动定时刷新
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-stats-processor");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(
                this::flushIfNeeded,
                flushIntervalMs,
                flushIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("创建批量统计处理器: batchSize={}, flushIntervalMs={}", batchSize, flushIntervalMs);
    }

    /**
     * 使用默认配置创建
     *
     * @param batchHandler 批量处理器
     */
    public BatchStatsProcessor(final Consumer<List<StatsUpdateEvent>> batchHandler) {
        this(DEFAULT_BATCH_SIZE, DEFAULT_FLUSH_INTERVAL_MS, batchHandler);
    }

    /**
     * 接收统计事件
     */
    @Override
    public synchronized void accept(final StatsUpdateEvent event) {
        if (event == null) {
            return;
        }

        // 背压保护
        if (buffer.size() >= MAX_BUFFER_SIZE) {
            droppedCount.incrementAndGet();
            logger.warn("缓冲区已满，丢弃事件: {}", event);
            return;
        }

        buffer.add(event);
        processedCount.incrementAndGet();

        // 更新聚合统计
        updateAggregatedStats(event);

        // 达到批次大小立即刷新
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    /**
     * 更新聚合统计
     */
    private void updateAggregatedStats(final StatsUpdateEvent event) {
        String key = event.getServiceType() + ":" + event.getModelName();
        ConcurrentModelCallStats stats = aggregatedStats.computeIfAbsent(key,
                k -> new ConcurrentModelCallStats(event.getModelName(), event.getServiceType()));

        switch (event.getType()) {
            case CALL_SUCCESS -> stats.updateStats(true, event.getResponseTime());
            case CALL_FAILURE -> stats.updateStats(false, event.getResponseTime());
            case CIRCUIT_BREAKER -> stats.recordCircuitBreaker();
            case RATE_LIMITED -> stats.recordRateLimit();
            case ERROR_CODE -> stats.recordErrorCode(event.getErrorCode());
        }
    }

    /**
     * 定时检查是否需要刷新
     */
    private synchronized void flushIfNeeded() {
        if (!buffer.isEmpty()) {
            flush();
        }
    }

    /**
     * 刷新缓冲区
     */
    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        // 复制当前缓冲区
        List<StatsUpdateEvent> batch = new ArrayList<>(buffer);
        buffer.clear();
        batchCount.incrementAndGet();

        // 异步处理批次
        if (batchHandler != null) {
            try {
                batchHandler.accept(batch);
                logger.debug("批量处理事件: size={}, batchCount={}", batch.size(), batchCount.get());
            } catch (Exception e) {
                logger.error("批量处理失败: size={}, error={}", batch.size(), e.getMessage(), e);
            }
        }
    }

    /**
     * 获取聚合统计
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 统计对象
     */
    public ConcurrentModelCallStats getAggregatedStats(final String serviceType, final String modelName) {
        return aggregatedStats.get(serviceType + ":" + modelName);
    }

    /**
     * 获取所有聚合统计
     */
    public Map<String, ConcurrentModelCallStats> getAllAggregatedStats() {
        return new ConcurrentHashMap<>(aggregatedStats);
    }

    /**
     * 获取统计信息
     */
    public ProcessorStats getStats() {
        return new ProcessorStats(
                buffer.size(),
                processedCount.get(),
                droppedCount.get(),
                batchCount.get(),
                aggregatedStats.size()
        );
    }

    /**
     * 关闭处理器
     */
    public void shutdown() {
        // 最后一次刷新
        synchronized (this) {
            if (!buffer.isEmpty()) {
                flush();
            }
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("批量统计处理器已关闭: processed={}, dropped={}, batches={}",
                processedCount.get(), droppedCount.get(), batchCount.get());
    }

    /**
     * 重置统计
     */
    public void reset() {
        synchronized (this) {
            buffer.clear();
        }
        aggregatedStats.clear();
        processedCount.set(0);
        droppedCount.set(0);
        batchCount.set(0);
    }

    /**
     * 处理器统计信息
     */
    public static class ProcessorStats {
        private final int bufferSize;
        private final long processedCount;
        private final long droppedCount;
        private final long batchCount;
        private final int aggregatedStatsCount;

        public ProcessorStats(final int bufferSize, final long processedCount,
                             final long droppedCount, final long batchCount, final int aggregatedStatsCount) {
            this.bufferSize = bufferSize;
            this.processedCount = processedCount;
            this.droppedCount = droppedCount;
            this.batchCount = batchCount;
            this.aggregatedStatsCount = aggregatedStatsCount;
        }

        public int getBufferSize() { return bufferSize; }
        public long getProcessedCount() { return processedCount; }
        public long getDroppedCount() { return droppedCount; }
        public long getBatchCount() { return batchCount; }
        public int getAggregatedStatsCount() { return aggregatedStatsCount; }

        @Override
        public String toString() {
            return String.format("ProcessorStats{buffer=%d, processed=%d, dropped=%d, batches=%d, stats=%d}",
                    bufferSize, processedCount, droppedCount, batchCount, aggregatedStatsCount);
        }
    }
}

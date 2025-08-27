package org.unreal.modelrouter.tracing.async;

import io.opentelemetry.api.trace.SpanContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步追踪数据处理器
 * 
 * 负责异步处理追踪数据，包括：
 * - 追踪数据的队列和缓冲机制
 * - 批量处理和导出功能
 * - 背压处理和流量控制
 * - 异步任务管理和监控
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AsyncTracingProcessor {

    private final TracingConfiguration tracingConfiguration;
    private final Scheduler processingScheduler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 数据队列和缓冲
    private final ConcurrentLinkedQueue<TraceData> traceQueue = new ConcurrentLinkedQueue<>();
    private final Sinks.Many<TraceData> traceDataSink;
    
    // 统计信息
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong batchCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    
    public AsyncTracingProcessor(TracingConfiguration tracingConfiguration) {
        this.tracingConfiguration = tracingConfiguration;
        
        // 创建专用的处理调度器
        TracingConfiguration.PerformanceConfig.ThreadPoolConfig threadPoolConfig = 
                tracingConfiguration.getPerformance().getThreadPool();
        this.processingScheduler = Schedulers.newBoundedElastic(
                threadPoolConfig.getCoreSize(),
                threadPoolConfig.getQueueCapacity(),
                threadPoolConfig.getThreadNamePrefix() + "async-processor"
        );
        
        // 创建响应式数据流
        this.traceDataSink = Sinks.many().multicast().onBackpressureBuffer(
                tracingConfiguration.getPerformance().getBuffer().getSize()
        );
    }

    @PostConstruct
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("启动异步追踪数据处理器");
            setupProcessingPipeline();
        }
    }

    @PreDestroy
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("停止异步追踪数据处理器");
            processingScheduler.dispose();
            traceDataSink.tryEmitComplete();
        }
    }

    /**
     * 异步提交追踪数据
     */
    public Mono<Boolean> submitTraceData(String traceId, String spanId, String operationName, 
                                        long startTime, long duration, boolean success, 
                                        SpanContext spanContext) {
        return Mono.fromCallable(() -> {
            if (!isRunning.get()) {
                log.warn("处理器未运行，丢弃追踪数据: traceId={}", traceId);
                droppedCount.incrementAndGet();
                return false;
            }

            TraceData traceData = new TraceData(
                    traceId, spanId, operationName, startTime, duration, success, spanContext, Instant.now()
            );

            // 检查队列容量，实现背压控制
            int maxQueueSize = tracingConfiguration.getPerformance().getBuffer().getSize();
            if (traceQueue.size() >= maxQueueSize) {
                log.warn("追踪数据队列已满，丢弃数据: traceId={}, queueSize={}", traceId, traceQueue.size());
                droppedCount.incrementAndGet();
                return false;
            }

            // 提交到响应式流
            Sinks.EmitResult result = traceDataSink.tryEmitNext(traceData);
            if (result.isSuccess()) {
                traceQueue.offer(traceData);
                return true;
            } else {
                log.warn("提交追踪数据失败: traceId={}, result={}", traceId, result);
                droppedCount.incrementAndGet();
                return false;
            }
        }).subscribeOn(processingScheduler)
          .onErrorReturn(false);
    }

    /**
     * 批量提交追踪数据
     */
    public Mono<Integer> submitTraceDataBatch(List<TraceData> traceDataList) {
        return Flux.fromIterable(traceDataList)
                .flatMap(traceData -> submitTraceData(
                        traceData.getTraceId(),
                        traceData.getSpanId(),
                        traceData.getOperationName(),
                        traceData.getStartTime(),
                        traceData.getDuration(),
                        traceData.isSuccess(),
                        traceData.getSpanContext()
                ))
                .map(success -> success ? 1 : 0)
                .reduce(0, Integer::sum)
                .subscribeOn(processingScheduler);
    }

    /**
     * 设置处理管道
     */
    private void setupProcessingPipeline() {
        TracingConfiguration.PerformanceConfig.BatchConfig batchConfig = 
                tracingConfiguration.getPerformance().getBatch();
        TracingConfiguration.PerformanceConfig.BufferConfig bufferConfig = 
                tracingConfiguration.getPerformance().getBuffer();

        traceDataSink.asFlux()
                .subscribeOn(processingScheduler)
                .bufferTimeout(batchConfig.getSize(), batchConfig.getTimeout())
                .filter(batch -> !batch.isEmpty())
                .flatMap(this::processBatch, batchConfig.getMaxConcurrentBatches())
                .subscribe(
                        this::handleBatchResult,
                        error -> log.error("处理管道错误", error),
                        () -> log.info("处理管道完成")
                );

        // 定期清理过期数据
        Flux.interval(bufferConfig.getFlushInterval(), processingScheduler)
                .subscribe(tick -> cleanupExpiredData());
    }

    /**
     * 处理批次数据
     */
    private Mono<BatchResult> processBatch(List<TraceData> batch) {
        return Mono.fromCallable(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 模拟批量处理逻辑
                int successCount = 0;
                int failureCount = 0;
                
                for (TraceData traceData : batch) {
                    try {
                        // 实际的处理逻辑（如导出到后端系统）
                        processTraceData(traceData);
                        successCount++;
                    } catch (Exception e) {
                        log.error("处理追踪数据失败: traceId={}", traceData.getTraceId(), e);
                        failureCount++;
                    }
                }
                
                long processingTime = System.currentTimeMillis() - startTime;
                processedCount.addAndGet(successCount);
                this.failureCount.addAndGet(failureCount);
                batchCount.incrementAndGet();
                
                return new BatchResult(successCount, failureCount, processingTime);
                
            } catch (Exception e) {
                log.error("批处理失败", e);
                failureCount.addAndGet(batch.size());
                return new BatchResult(0, batch.size(), 0);
            }
        }).subscribeOn(processingScheduler);
    }

    /**
     * 处理单个追踪数据
     */
    private void processTraceData(TraceData traceData) {
        // 实际的处理逻辑，例如：
        // 1. 序列化数据
        // 2. 应用过滤规则
        // 3. 导出到外部系统
        // 4. 记录处理日志
        
        log.debug("处理追踪数据: traceId={}, spanId={}, operation={}, duration={}ms", 
                traceData.getTraceId(), traceData.getSpanId(), 
                traceData.getOperationName(), traceData.getDuration());
    }

    /**
     * 处理批次结果
     */
    private void handleBatchResult(BatchResult result) {
        log.debug("批处理完成: 成功={}, 失败={}, 处理时间={}ms", 
                result.getSuccessCount(), result.getFailureCount(), result.getProcessingTime());
        
        // 可以在这里添加指标记录或告警逻辑
        if (result.getFailureCount() > 0) {
            log.warn("批处理包含失败项: 失败数量={}", result.getFailureCount());
        }
    }

    /**
     * 清理过期数据
     */
    private void cleanupExpiredData() {
        try {
            Duration maxAge = tracingConfiguration.getPerformance().getBuffer().getMaxWaitTime();
            Instant cutoff = Instant.now().minus(maxAge);
            
            int removedCount = 0;
            while (!traceQueue.isEmpty()) {
                TraceData data = traceQueue.peek();
                if (data != null && data.getTimestamp().isBefore(cutoff)) {
                    traceQueue.poll();
                    removedCount++;
                } else {
                    break;
                }
            }
            
            if (removedCount > 0) {
                log.info("清理过期追踪数据: 清理数量={}", removedCount);
                droppedCount.addAndGet(removedCount);
            }
        } catch (Exception e) {
            log.error("清理过期数据失败", e);
        }
    }

    /**
     * 获取处理统计信息
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(
                processedCount.get(),
                droppedCount.get(),
                batchCount.get(),
                failureCount.get(),
                traceQueue.size(),
                isRunning.get()
        );
    }

    /**
     * 强制刷新缓冲区
     */
    public Mono<Void> flush() {
        return Mono.fromRunnable(() -> {
            log.info("强制刷新异步处理缓冲区");
            // 触发立即处理当前队列中的数据
        }).subscribeOn(processingScheduler).then();
    }

    /**
     * 追踪数据实体
     */
    @Data
    public static class TraceData {
        private final String traceId;
        private final String spanId;
        private final String operationName;
        private final long startTime;
        private final long duration;
        private final boolean success;
        private final SpanContext spanContext;
        private final Instant timestamp;
    }

    /**
     * 批处理结果
     */
    @Data
    public static class BatchResult {
        private final int successCount;
        private final int failureCount;
        private final long processingTime;
    }

    /**
     * 处理统计信息
     */
    @Data
    public static class ProcessingStats {
        private final long processedCount;
        private final long droppedCount;
        private final long batchCount;
        private final long failureCount;
        private final int queueSize;
        private final boolean isRunning;
        
        public double getSuccessRate() {
            long total = processedCount + failureCount;
            return total > 0 ? (double) processedCount / total : 0.0;
        }
        
        public double getDropRate() {
            long total = processedCount + droppedCount;
            return total > 0 ? (double) droppedCount / total : 0.0;
        }
    }
}
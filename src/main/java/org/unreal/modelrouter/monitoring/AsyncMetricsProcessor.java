package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Conditional;

import jakarta.annotation.PreDestroy;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

/**
 * 异步指标处理器
 * 提供非阻塞的指标收集处理，避免影响主业务流程
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class AsyncMetricsProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AsyncMetricsProcessor.class);

    private final MonitoringProperties monitoringProperties;
    private final MetricsCollector metricsCollector;
    private final MetricsCircuitBreaker circuitBreaker;
    
    // 异步处理相关
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final BlockingQueue<MetricsEvent> metricsQueue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    // 统计信息
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong queueSize = new AtomicLong(0);


    public AsyncMetricsProcessor(MonitoringProperties monitoringProperties, 
                               @Lazy MetricsCollector metricsCollector,
                               MetricsCircuitBreaker circuitBreaker) {
        this.monitoringProperties = monitoringProperties;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = circuitBreaker;
        
        int bufferSize = monitoringProperties.getPerformance().getBufferSize();
        this.metricsQueue = new ArrayBlockingQueue<>(bufferSize);
        
        // 创建线程池
        this.executorService = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "metrics-processor");
            t.setDaemon(true);
            return t;
        });
        
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "metrics-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动处理器
        startProcessing();
        
        logger.info("AsyncMetricsProcessor initialized with buffer size: {}", bufferSize);
    }

    /**
     * 异步记录请求指标
     */
    public void recordRequestAsync(String service, String method, long duration, String status) {
        if (!shouldSample(MetricsType.REQUEST)) {
            return;
        }
        
        MetricsEvent event = new RequestMetricsEvent(service, method, duration, status);
        submitEvent(event);
    }

    /**
     * 异步记录后端调用指标
     */
    public void recordBackendCallAsync(String adapter, String instance, long duration, boolean success) {
        if (!shouldSample(MetricsType.BACKEND)) {
            return;
        }
        
        MetricsEvent event = new BackendCallMetricsEvent(adapter, instance, duration, success);
        submitEvent(event);
    }

    /**
     * 异步记录限流器指标
     */
    public void recordRateLimitAsync(String service, String algorithm, boolean allowed) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        
        MetricsEvent event = new RateLimitMetricsEvent(service, algorithm, allowed);
        submitEvent(event);
    }

    /**
     * 异步记录熔断器指标
     */
    public void recordCircuitBreakerAsync(String service, String state, String event) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        
        MetricsEvent metricsEvent = new CircuitBreakerMetricsEvent(service, state, event);
        submitEvent(metricsEvent);
    }

    /**
     * 异步记录负载均衡器指标
     */
    public void recordLoadBalancerAsync(String service, String strategy, String selectedInstance) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        
        MetricsEvent event = new LoadBalancerMetricsEvent(service, strategy, selectedInstance);
        submitEvent(event);
    }

    /**
     * 异步记录健康检查指标
     */
    public void recordHealthCheckAsync(String adapter, String instance, boolean healthy, long responseTime) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        
        MetricsEvent event = new HealthCheckMetricsEvent(adapter, instance, healthy, responseTime);
        submitEvent(event);
    }

    /**
     * 异步记录请求大小指标
     */
    public void recordRequestSizeAsync(String service, long requestSize, long responseSize) {
        if (!shouldSample(MetricsType.REQUEST)) {
            return;
        }
        
        MetricsEvent event = new RequestSizeMetricsEvent(service, requestSize, responseSize);
        submitEvent(event);
    }

    /**
     * 异步记录追踪指标
     */
    public void recordTraceAsync(String traceId, String spanId, String operationName, long duration, boolean success) {
        if (!shouldSample(MetricsType.TRACE)) {
            return;
        }
        
        MetricsEvent event = new TraceMetricsEvent(traceId, spanId, operationName, duration, success);
        submitEvent(event);
    }
    
    /**
     * 异步记录追踪处理指标
     */
    public void recordTraceProcessingAsync(String processorName, long duration, boolean success) {
        if (!shouldSample(MetricsType.TRACE_PROCESSING)) {
            return;
        }
        
        MetricsEvent event = new TraceProcessingMetricsEvent(processorName, duration, success);
        submitEvent(event);
    }
    
    /**
     * 异步记录追踪分析指标
     */
    public void recordTraceAnalysisAsync(String analyzerName, int spanCount, long duration, boolean success) {
        if (!shouldSample(MetricsType.TRACE_ANALYSIS)) {
            return;
        }
        
        MetricsEvent event = new TraceAnalysisMetricsEvent(analyzerName, spanCount, duration, success);
        submitEvent(event);
    }

    /**
     * 异步记录追踪导出指标
     */
    public void recordTraceExportAsync(String exporterType, long duration, boolean success, int batchSize) {
        if (!shouldSample(MetricsType.TRACE)) {
            return;
        }
        
        MetricsEvent event = new TraceExportMetricsEvent(exporterType, duration, success, batchSize);
        submitEvent(event);
    }

    /**
     * 异步记录追踪采样指标
     */
    public void recordTraceSamplingAsync(double samplingRate, boolean sampled) {
        // 采样指标本身不需要采样
        MetricsEvent event = new TraceSamplingMetricsEvent(samplingRate, sampled);
        submitEvent(event);
    }

    /**
     * 异步记录追踪数据质量指标
     */
    public void recordTraceDataQualityAsync(String traceId, int spanCount, int attributeCount, int errorCount) {
        if (!shouldSample(MetricsType.TRACE)) {
            return;
        }
        
        MetricsEvent event = new TraceDataQualityMetricsEvent(traceId, spanCount, attributeCount, errorCount);
        submitEvent(event);
    }

    /**
     * 提交指标事件到队列
     */
    private void submitEvent(MetricsEvent event) {
        if (!circuitBreaker.allowRequest()) {
            droppedCount.incrementAndGet();
            logger.debug("Metrics event dropped due to circuit breaker: {}", event.getClass().getSimpleName());
            return;
        }
        
        boolean offered = metricsQueue.offer(event);
        if (offered) {
            queueSize.incrementAndGet();
        } else {
            droppedCount.incrementAndGet();
            logger.warn("Metrics queue is full, dropping event: {}", event.getClass().getSimpleName());
        }
    }

    /**
     * 检查是否应该采样
     */
    private boolean shouldSample(MetricsType type) {
        double samplingRate = getSamplingRate(type);
        return Math.random() < samplingRate;
    }

    /**
     * 获取采样率
     */
    private double getSamplingRate(MetricsType type) {
        MonitoringProperties.Sampling sampling = monitoringProperties.getSampling();
        switch (type) {
            case REQUEST:
                return sampling.getRequestMetrics();
            case BACKEND:
                return sampling.getBackendMetrics();
            case INFRASTRUCTURE:
                return sampling.getInfrastructureMetrics();
            case TRACE:
                return sampling.getTraceMetrics();
            case TRACE_PROCESSING:
                return sampling.getTraceProcessingMetrics();
            case TRACE_ANALYSIS:
                return sampling.getTraceAnalysisMetrics();
            default:
                return 1.0;
        }
    }

    /**
     * 启动异步处理
     */
    private void startProcessing() {
        // 启动批量处理器
        executorService.submit(this::processBatch);
        
        // 启动统计信息定期输出
        scheduledExecutorService.scheduleAtFixedRate(
            this::logStatistics, 
            60, 60, TimeUnit.SECONDS
        );
    }

    /**
     * 批量处理指标事件
     */
    private void processBatch() {
        List<MetricsEvent> batch = new ArrayList<>();
        int batchSize = monitoringProperties.getPerformance().getBatchSize();
        
        while (running.get()) {
            try {
                // 收集批量事件
                MetricsEvent event = metricsQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    batch.add(event);
                    queueSize.decrementAndGet();
                    
                    // 继续收集直到达到批量大小或队列为空
                    while (batch.size() < batchSize) {
                        MetricsEvent nextEvent = metricsQueue.poll();
                        if (nextEvent == null) {
                            break;
                        }
                        batch.add(nextEvent);
                        queueSize.decrementAndGet();
                    }
                    
                    // 处理批量事件
                    processBatchEvents(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Metrics processor interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error processing metrics batch", e);
                circuitBreaker.recordFailure();
            }
        }
    }

    /**
     * 处理批量指标事件
     */
    private void processBatchEvents(List<MetricsEvent> events) {
        try {
            for (MetricsEvent event : events) {
                processEvent(event);
                processedCount.incrementAndGet();
            }
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            logger.error("Error processing metrics events", e);
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    /**
     * 处理单个指标事件
     */
    private void processEvent(MetricsEvent event) {
        try {
            if (event instanceof RequestMetricsEvent) {
                RequestMetricsEvent req = (RequestMetricsEvent) event;
                metricsCollector.recordRequest(req.service, req.method, req.duration, req.status);
            } else if (event instanceof BackendCallMetricsEvent) {
                BackendCallMetricsEvent backend = (BackendCallMetricsEvent) event;
                metricsCollector.recordBackendCall(backend.adapter, backend.instance, backend.duration, backend.success);
            } else if (event instanceof RateLimitMetricsEvent) {
                RateLimitMetricsEvent rateLimit = (RateLimitMetricsEvent) event;
                metricsCollector.recordRateLimit(rateLimit.service, rateLimit.algorithm, rateLimit.allowed);
            } else if (event instanceof CircuitBreakerMetricsEvent) {
                CircuitBreakerMetricsEvent cb = (CircuitBreakerMetricsEvent) event;
                metricsCollector.recordCircuitBreaker(cb.service, cb.state, cb.event);
            } else if (event instanceof LoadBalancerMetricsEvent) {
                LoadBalancerMetricsEvent lb = (LoadBalancerMetricsEvent) event;
                metricsCollector.recordLoadBalancer(lb.service, lb.strategy, lb.selectedInstance);
            } else if (event instanceof HealthCheckMetricsEvent) {
                HealthCheckMetricsEvent health = (HealthCheckMetricsEvent) event;
                metricsCollector.recordHealthCheck(health.adapter, health.instance, health.healthy, health.responseTime);
            } else if (event instanceof RequestSizeMetricsEvent) {
                RequestSizeMetricsEvent size = (RequestSizeMetricsEvent) event;
                metricsCollector.recordRequestSize(size.service, size.requestSize, size.responseSize);
            } else if (event instanceof TraceMetricsEvent) {
                TraceMetricsEvent trace = (TraceMetricsEvent) event;
                metricsCollector.recordTrace(trace.traceId, trace.spanId, trace.operationName, trace.duration, trace.success);
            } else if (event instanceof TraceExportMetricsEvent) {
                TraceExportMetricsEvent traceExport = (TraceExportMetricsEvent) event;
                metricsCollector.recordTraceExport(traceExport.exporterType, traceExport.duration, traceExport.success, traceExport.batchSize);
            } else if (event instanceof TraceSamplingMetricsEvent) {
                TraceSamplingMetricsEvent traceSampling = (TraceSamplingMetricsEvent) event;
                metricsCollector.recordTraceSampling(traceSampling.samplingRate, traceSampling.sampled);
            } else if (event instanceof TraceDataQualityMetricsEvent) {
                TraceDataQualityMetricsEvent traceDataQuality = (TraceDataQualityMetricsEvent) event;
                metricsCollector.recordTraceDataQuality(traceDataQuality.traceId, traceDataQuality.spanCount, traceDataQuality.attributeCount, traceDataQuality.errorCount);
            } else if (event instanceof TraceProcessingMetricsEvent) {
                TraceProcessingMetricsEvent traceProcessing = (TraceProcessingMetricsEvent) event;
                metricsCollector.recordTraceProcessing(traceProcessing.processorName, traceProcessing.duration, traceProcessing.success);
            } else if (event instanceof TraceAnalysisMetricsEvent) {
                TraceAnalysisMetricsEvent traceAnalysis = (TraceAnalysisMetricsEvent) event;
                metricsCollector.recordTraceAnalysis(traceAnalysis.analyzerName, traceAnalysis.spanCount, traceAnalysis.duration, traceAnalysis.success);
            }
        } catch (Exception e) {
            logger.warn("Failed to process metrics event: {}", e.getMessage());
        }
    }

    /**
     * 记录统计信息
     */
    private void logStatistics() {
        logger.info("Metrics processor statistics - Processed: {}, Dropped: {}, Queue size: {}, Circuit breaker state: {}", 
                   processedCount.get(), droppedCount.get(), queueSize.get(), circuitBreaker.getState());
    }

    /**
     * 获取处理统计信息
     */
    public ProcessingStats getStats() {
        return new ProcessingStats(
            processedCount.get(),
            droppedCount.get(),
            queueSize.get(),
            circuitBreaker.getState()
        );
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down AsyncMetricsProcessor");
        running.set(false);
        
        executorService.shutdown();
        scheduledExecutorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("AsyncMetricsProcessor shutdown completed");
    }

    // 指标类型枚举
    private enum MetricsType {
        REQUEST, BACKEND, INFRASTRUCTURE, TRACE, TRACE_PROCESSING, TRACE_ANALYSIS
    }

    // 指标事件基类
    private abstract static class MetricsEvent {
        protected final long timestamp = System.currentTimeMillis();
    }

    // 各种指标事件类
    private static class RequestMetricsEvent extends MetricsEvent {
        final String service;
        final String method;
        final long duration;
        final String status;

        RequestMetricsEvent(String service, String method, long duration, String status) {
            this.service = service;
            this.method = method;
            this.duration = duration;
            this.status = status;
        }
    }

    private static class BackendCallMetricsEvent extends MetricsEvent {
        final String adapter;
        final String instance;
        final long duration;
        final boolean success;

        BackendCallMetricsEvent(String adapter, String instance, long duration, boolean success) {
            this.adapter = adapter;
            this.instance = instance;
            this.duration = duration;
            this.success = success;
        }
    }

    private static class RateLimitMetricsEvent extends MetricsEvent {
        final String service;
        final String algorithm;
        final boolean allowed;

        RateLimitMetricsEvent(String service, String algorithm, boolean allowed) {
            this.service = service;
            this.algorithm = algorithm;
            this.allowed = allowed;
        }
    }

    private static class CircuitBreakerMetricsEvent extends MetricsEvent {
        final String service;
        final String state;
        final String event;

        CircuitBreakerMetricsEvent(String service, String state, String event) {
            this.service = service;
            this.state = state;
            this.event = event;
        }
    }

    private static class LoadBalancerMetricsEvent extends MetricsEvent {
        final String service;
        final String strategy;
        final String selectedInstance;

        LoadBalancerMetricsEvent(String service, String strategy, String selectedInstance) {
            this.service = service;
            this.strategy = strategy;
            this.selectedInstance = selectedInstance;
        }
    }

    private static class HealthCheckMetricsEvent extends MetricsEvent {
        final String adapter;
        final String instance;
        final boolean healthy;
        final long responseTime;

        HealthCheckMetricsEvent(String adapter, String instance, boolean healthy, long responseTime) {
            this.adapter = adapter;
            this.instance = instance;
            this.healthy = healthy;
            this.responseTime = responseTime;
        }
    }

    private static class RequestSizeMetricsEvent extends MetricsEvent {
        final String service;
        final long requestSize;
        final long responseSize;

        RequestSizeMetricsEvent(String service, long requestSize, long responseSize) {
            this.service = service;
            this.requestSize = requestSize;
            this.responseSize = responseSize;
        }
    }

    private static class TraceMetricsEvent extends MetricsEvent {
        final String traceId;
        final String spanId;
        final String operationName;
        final long duration;
        final boolean success;

        TraceMetricsEvent(String traceId, String spanId, String operationName, long duration, boolean success) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.operationName = operationName;
            this.duration = duration;
            this.success = success;
        }
    }
    
    private static class TraceProcessingMetricsEvent extends MetricsEvent {
        final String processorName;
        final long duration;
        final boolean success;

        TraceProcessingMetricsEvent(String processorName, long duration, boolean success) {
            this.processorName = processorName;
            this.duration = duration;
            this.success = success;
        }
    }
    
    private static class TraceAnalysisMetricsEvent extends MetricsEvent {
        final String analyzerName;
        final int spanCount;
        final long duration;
        final boolean success;

        TraceAnalysisMetricsEvent(String analyzerName, int spanCount, long duration, boolean success) {
            this.analyzerName = analyzerName;
            this.spanCount = spanCount;
            this.duration = duration;
            this.success = success;
        }
    }

    private static class TraceExportMetricsEvent extends MetricsEvent {
        final String exporterType;
        final long duration;
        final boolean success;
        final int batchSize;

        TraceExportMetricsEvent(String exporterType, long duration, boolean success, int batchSize) {
            this.exporterType = exporterType;
            this.duration = duration;
            this.success = success;
            this.batchSize = batchSize;
        }
    }

    private static class TraceSamplingMetricsEvent extends MetricsEvent {
        final double samplingRate;
        final boolean sampled;

        TraceSamplingMetricsEvent(double samplingRate, boolean sampled) {
            this.samplingRate = samplingRate;
            this.sampled = sampled;
        }
    }

    private static class TraceDataQualityMetricsEvent extends MetricsEvent {
        final String traceId;
        final int spanCount;
        final int attributeCount;
        final int errorCount;

        TraceDataQualityMetricsEvent(String traceId, int spanCount, int attributeCount, int errorCount) {
            this.traceId = traceId;
            this.spanCount = spanCount;
            this.attributeCount = attributeCount;
            this.errorCount = errorCount;
        }
    }

    // 处理统计信息类
    public static class ProcessingStats {
        private final long processedCount;
        private final long droppedCount;
        private final long queueSize;
        private final String circuitBreakerState;

        public ProcessingStats(long processedCount, long droppedCount, long queueSize, String circuitBreakerState) {
            this.processedCount = processedCount;
            this.droppedCount = droppedCount;
            this.queueSize = queueSize;
            this.circuitBreakerState = circuitBreakerState;
        }

        public long getProcessedCount() { return processedCount; }
        public long getDroppedCount() { return droppedCount; }
        public long getQueueSize() { return queueSize; }
        public String getCircuitBreakerState() { return circuitBreakerState; }
    }
}
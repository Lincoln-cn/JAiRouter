package org.unreal.modelrouter.monitor.monitoring.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.AsyncMetricsProcessor;
import org.unreal.modelrouter.monitor.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitor.monitoring.event.ProcessingStats;
import org.unreal.modelrouter.monitor.monitoring.config.MonitoringEnabledCondition;

/**
 * 异步指标收集器
 * 集成异步处理、内存管理和熔断保护的高性能指标收集器
 */
@Component
@Primary
@Conditional(MonitoringEnabledCondition.class)
public class AsyncMetricsCollector implements MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(AsyncMetricsCollector.class);

    private final MonitoringProperties monitoringProperties;
    private final AsyncMetricsProcessor asyncProcessor;
    private final MetricsMemoryManager memoryManager;
    private final DefaultMetricsCollector fallbackCollector;

    public AsyncMetricsCollector(final MonitoringProperties monitoringProperties,
                               final AsyncMetricsProcessor asyncProcessor,
                               final MetricsMemoryManager memoryManager,
                               final DefaultMetricsCollector fallbackCollector) {
        this.monitoringProperties = monitoringProperties;
        this.asyncProcessor = asyncProcessor;
        this.memoryManager = memoryManager;
        this.fallbackCollector = fallbackCollector;
        
        logger.info("AsyncMetricsCollector initialized with async processing: {}", 
                   monitoringProperties.getPerformance().isAsyncProcessing());
    }

    @Override
    public void recordRequest(final String service, final String method, final long duration, final String status) {
        try {
            if (shouldUseAsyncProcessing()) {
                // 检查内存压力和采样率
                double samplingRate = monitoringProperties.getSampling().getRequestMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordRequestAsync(service, method, duration, status);
                }
            } else {
                // 同步处理作为后备
                fallbackCollector.recordRequest(service, method, duration, status);
            }
        } catch (Exception e) {
            logger.warn("Failed to record request metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordRequest(service, method, duration, status);
            } catch (Exception fallbackError) {
                logger.error("Failed to record request metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordBackendCall(final String adapter, final String instance, final long duration, final boolean success) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getBackendMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordBackendCallAsync(adapter, instance, duration, success);
                }
            } else {
                fallbackCollector.recordBackendCall(adapter, instance, duration, success);
            }
        } catch (Exception e) {
            logger.warn("Failed to record backend call metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordBackendCall(adapter, instance, duration, success);
            } catch (Exception fallbackError) {
                logger.error("Failed to record backend call metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordRateLimit(final String service, final String algorithm, final boolean allowed) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getInfrastructureMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordRateLimitAsync(service, algorithm, allowed);
                }
            } else {
                fallbackCollector.recordRateLimit(service, algorithm, allowed);
            }
        } catch (Exception e) {
            logger.warn("Failed to record rate limit metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordRateLimit(service, algorithm, allowed);
            } catch (Exception fallbackError) {
                logger.error("Failed to record rate limit metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordCircuitBreaker(final String service, final String state, final String event) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getInfrastructureMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordCircuitBreakerAsync(service, state, event);
                }
            } else {
                fallbackCollector.recordCircuitBreaker(service, state, event);
            }
        } catch (Exception e) {
            logger.warn("Failed to record circuit breaker metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordCircuitBreaker(service, state, event);
            } catch (Exception fallbackError) {
                logger.error("Failed to record circuit breaker metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordLoadBalancer(final String service, final String strategy, final String selectedInstance) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getInfrastructureMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordLoadBalancerAsync(service, strategy, selectedInstance);
                }
            } else {
                fallbackCollector.recordLoadBalancer(service, strategy, selectedInstance);
            }
        } catch (Exception e) {
            logger.warn("Failed to record load balancer metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordLoadBalancer(service, strategy, selectedInstance);
            } catch (Exception fallbackError) {
                logger.error("Failed to record load balancer metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordHealthCheck(final String adapter, final String instance, final boolean healthy, final long responseTime) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getInfrastructureMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordHealthCheckAsync(adapter, instance, healthy, responseTime);
                }
            } else {
                fallbackCollector.recordHealthCheck(adapter, instance, healthy, responseTime);
            }
        } catch (Exception e) {
            logger.warn("Failed to record health check metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordHealthCheck(adapter, instance, healthy, responseTime);
            } catch (Exception fallbackError) {
                logger.error("Failed to record health check metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordRequestSize(final String service, final long requestSize, final long responseSize) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getRequestMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordRequestSizeAsync(service, requestSize, responseSize);
                }
            } else {
                fallbackCollector.recordRequestSize(service, requestSize, responseSize);
            }
        } catch (Exception e) {
            logger.warn("Failed to record request size metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordRequestSize(service, requestSize, responseSize);
            } catch (Exception fallbackError) {
                logger.error("Failed to record request size metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordTrace(final String traceId, final String spanId, final String operationName, final long duration, final boolean success) {
        try {
            if (shouldUseAsyncProcessing()) {
                // 对于追踪指标，使用基础设施指标的采样率
                double samplingRate = monitoringProperties.getSampling().getInfrastructureMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordTraceAsync(traceId, spanId, operationName, duration, success);
                }
            } else {
                fallbackCollector.recordTrace(traceId, spanId, operationName, duration, success);
            }
        } catch (Exception e) {
            logger.warn("Failed to record trace metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordTrace(traceId, spanId, operationName, duration, success);
            } catch (Exception fallbackError) {
                logger.error("Failed to record trace metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordTraceExport(final String exporterType, final long duration, final boolean success, final int batchSize) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getInfrastructureMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordTraceExportAsync(exporterType, duration, success, batchSize);
                }
            } else {
                fallbackCollector.recordTraceExport(exporterType, duration, success, batchSize);
            }
        } catch (Exception e) {
            logger.warn("Failed to record trace export metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordTraceExport(exporterType, duration, success, batchSize);
            } catch (Exception fallbackError) {
                logger.error("Failed to record trace export metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordTraceSampling(final double samplingRate, final boolean sampled) {
        try {
            if (shouldUseAsyncProcessing()) {
                // 采样指标本身不需要采样
                asyncProcessor.recordTraceSamplingAsync(samplingRate, sampled);
            } else {
                fallbackCollector.recordTraceSampling(samplingRate, sampled);
            }
        } catch (Exception e) {
            logger.warn("Failed to record trace sampling metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordTraceSampling(samplingRate, sampled);
            } catch (Exception fallbackError) {
                logger.error("Failed to record trace sampling metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordTraceDataQuality(final String traceId, final int spanCount, final int attributeCount, final int errorCount) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getInfrastructureMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordTraceDataQualityAsync(traceId, spanCount, attributeCount, errorCount);
                }
            } else {
                fallbackCollector.recordTraceDataQuality(traceId, spanCount, attributeCount, errorCount);
            }
        } catch (Exception e) {
            logger.warn("Failed to record trace data quality metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordTraceDataQuality(traceId, spanCount, attributeCount, errorCount);
            } catch (Exception fallbackError) {
                logger.error("Failed to record trace data quality metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordTraceProcessing(final String processorName, final long duration, final boolean success) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getTraceProcessingMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordTraceProcessingAsync(processorName, duration, success);
                }
            } else {
                fallbackCollector.recordTraceProcessing(processorName, duration, success);
            }
        } catch (Exception e) {
            logger.warn("Failed to record trace processing metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordTraceProcessing(processorName, duration, success);
            } catch (Exception fallbackError) {
                logger.error("Failed to record trace processing metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordTraceAnalysis(final String analyzerName, final int spanCount, final long duration, final boolean success) {
        try {
            if (shouldUseAsyncProcessing()) {
                double samplingRate = monitoringProperties.getSampling().getTraceAnalysisMetrics();
                if (memoryManager.shouldSample(samplingRate)) {
                    asyncProcessor.recordTraceAnalysisAsync(analyzerName, spanCount, duration, success);
                }
            } else {
                fallbackCollector.recordTraceAnalysis(analyzerName, spanCount, duration, success);
            }
        } catch (Exception e) {
            logger.warn("Failed to record trace analysis metric asynchronously, falling back to sync: {}", e.getMessage());
            try {
                fallbackCollector.recordTraceAnalysis(analyzerName, spanCount, duration, success);
            } catch (Exception fallbackError) {
                logger.error("Failed to record trace analysis metric even with fallback", fallbackError);
            }
        }
    }

    @Override
    public void recordRateLimitStatus(final String service, final String scope, final String algorithm,
                                       final long remainingCapacity, final double usageRatio) {
        try {
            if (shouldUseAsyncProcessing()) {
                // 限流器状态指标不需要采样
                fallbackCollector.recordRateLimitStatus(service, scope, algorithm, remainingCapacity, usageRatio);
            } else {
                fallbackCollector.recordRateLimitStatus(service, scope, algorithm, remainingCapacity, usageRatio);
            }
        } catch (Exception e) {
            logger.warn("Failed to record rate limit status metric: {}", e.getMessage());
        }
    }

    /**
     * 判断是否应该使用异步处理
     */
    private boolean shouldUseAsyncProcessing() {
        return monitoringProperties.getPerformance().isAsyncProcessing()
               && asyncProcessor != null
               && memoryManager != null;
    }


    /**
     * 获取性能统计信息
     */
    public PerformanceStats getPerformanceStats() {
        ProcessingStats processingStats = asyncProcessor.getStats();
        MetricsMemoryManager.MemoryStats memoryStats = memoryManager.getMemoryStats();

        return new PerformanceStats(
                shouldUseAsyncProcessing(),
                processingStats,
                memoryStats
        );
    }

    /**
     * 性能统计信息类
     */
    public static class PerformanceStats {
        private final boolean asyncProcessingEnabled;
        private final ProcessingStats processingStats;
        private final MetricsMemoryManager.MemoryStats memoryStats;

        public PerformanceStats(final boolean asyncProcessingEnabled,
                                final ProcessingStats processingStats,
                                final MetricsMemoryManager.MemoryStats memoryStats) {
            this.asyncProcessingEnabled = asyncProcessingEnabled;
            this.processingStats = processingStats;
            this.memoryStats = memoryStats;
        }

        public boolean isAsyncProcessingEnabled() {
            return asyncProcessingEnabled;
        }

        public ProcessingStats getProcessingStats() {
            return processingStats;
        }

        public MetricsMemoryManager.MemoryStats getMemoryStats() {
            return memoryStats;
        }

        @Override
        public String toString() {
            return String.format("PerformanceStats{async=%s, processing=%s, memory=%s}",
                    asyncProcessingEnabled, processingStats, memoryStats);
        }
    }
}
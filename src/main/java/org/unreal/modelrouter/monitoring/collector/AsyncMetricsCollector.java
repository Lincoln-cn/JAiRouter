package org.unreal.modelrouter.monitoring.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.unreal.modelrouter.monitoring.AsyncMetricsProcessor;
import org.unreal.modelrouter.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

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

    public AsyncMetricsCollector(MonitoringProperties monitoringProperties,
                               AsyncMetricsProcessor asyncProcessor,
                               MetricsMemoryManager memoryManager,
                               DefaultMetricsCollector fallbackCollector) {
        this.monitoringProperties = monitoringProperties;
        this.asyncProcessor = asyncProcessor;
        this.memoryManager = memoryManager;
        this.fallbackCollector = fallbackCollector;
        
        logger.info("AsyncMetricsCollector initialized with async processing: {}", 
                   monitoringProperties.getPerformance().isAsyncProcessing());
    }

    @Override
    public void recordRequest(String service, String method, long duration, String status) {
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
    public void recordBackendCall(String adapter, String instance, long duration, boolean success) {
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
    public void recordRateLimit(String service, String algorithm, boolean allowed) {
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
    public void recordCircuitBreaker(String service, String state, String event) {
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
    public void recordLoadBalancer(String service, String strategy, String selectedInstance) {
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
    public void recordHealthCheck(String adapter, String instance, boolean healthy, long responseTime) {
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
    public void recordRequestSize(String service, long requestSize, long responseSize) {
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

    /**
     * 检查是否应该使用异步处理
     */
    private boolean shouldUseAsyncProcessing() {
        // 检查配置是否启用异步处理
        if (!monitoringProperties.getPerformance().isAsyncProcessing()) {
            return false;
        }
        
        // 检查内存使用情况
        MetricsMemoryManager.MemoryStats memoryStats = memoryManager.getMemoryStats();
        if (memoryStats.getMemoryUsageRatio() > 0.95) {
            // 内存使用率超过95%时，暂停异步处理
            return false;
        }
        
        // 检查异步处理器状态
        AsyncMetricsProcessor.ProcessingStats processingStats = asyncProcessor.getStats();
        if ("OPEN".equals(processingStats.getCircuitBreakerState())) {
            // 熔断器开启时，使用同步处理
            return false;
        }
        
        return true;
    }

    /**
     * 获取性能统计信息
     */
    public PerformanceStats getPerformanceStats() {
        AsyncMetricsProcessor.ProcessingStats processingStats = asyncProcessor.getStats();
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
        private final AsyncMetricsProcessor.ProcessingStats processingStats;
        private final MetricsMemoryManager.MemoryStats memoryStats;

        public PerformanceStats(boolean asyncProcessingEnabled,
                              AsyncMetricsProcessor.ProcessingStats processingStats,
                              MetricsMemoryManager.MemoryStats memoryStats) {
            this.asyncProcessingEnabled = asyncProcessingEnabled;
            this.processingStats = processingStats;
            this.memoryStats = memoryStats;
        }

        public boolean isAsyncProcessingEnabled() { return asyncProcessingEnabled; }
        public AsyncMetricsProcessor.ProcessingStats getProcessingStats() { return processingStats; }
        public MetricsMemoryManager.MemoryStats getMemoryStats() { return memoryStats; }

        @Override
        public String toString() {
            return String.format("PerformanceStats{async=%s, processing=%s, memory=%s}",
                               asyncProcessingEnabled, processingStats, memoryStats);
        }
    }
}
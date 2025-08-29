package org.unreal.modelrouter.monitoring.circuitbreaker;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitoring.AsyncMetricsProcessor;
import org.unreal.modelrouter.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 监控系统健康检查器
 * 检查监控系统各组件的健康状态
 */
@Component
public class MonitoringHealthChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringHealthChecker.class);
    
    private final MeterRegistry meterRegistry;
    private final MetricsErrorHandler errorHandler;
    private final MetricsDegradationStrategy degradationStrategy;
    private final MetricsCacheAndRetry cacheAndRetry;
    private final AsyncMetricsProcessor asyncProcessor;
    private final MetricsMemoryManager memoryManager;
    
    // 健康检查阈值
    private static final double MAX_ERROR_RATE = 0.1; // 10%
    private static final double MAX_MEMORY_USAGE = 0.9; // 90%
    private static final int MAX_DEGRADED_COMPONENTS = 5;
    private static final Duration MAX_PROCESSING_DELAY = Duration.ofSeconds(30);
    
    public MonitoringHealthChecker(MeterRegistry meterRegistry,
                                 MetricsErrorHandler errorHandler,
                                 MetricsDegradationStrategy degradationStrategy,
                                 MetricsCacheAndRetry cacheAndRetry,
                                 AsyncMetricsProcessor asyncProcessor,
                                 MetricsMemoryManager memoryManager) {
        this.meterRegistry = meterRegistry;
        this.errorHandler = errorHandler;
        this.degradationStrategy = degradationStrategy;
        this.cacheAndRetry = cacheAndRetry;
        this.asyncProcessor = asyncProcessor;
        this.memoryManager = memoryManager;
    }
    
    /**
     * 执行健康检查
     */
    public HealthCheckResult performHealthCheck() {
        try {
            Map<String, Object> details = new HashMap<>();
            boolean isHealthy = true;
            StringBuilder issues = new StringBuilder();
            
            // 检查错误处理器状态
            HealthStatus errorHandlerStatus = checkErrorHandler();
            details.put("errorHandler", errorHandlerStatus.getDetails());
            if (!errorHandlerStatus.isHealthy()) {
                isHealthy = false;
                issues.append("ErrorHandler: ").append(errorHandlerStatus.getDetails().get("reason")).append("; ");
            }
            
            // 检查降级策略状态
            HealthStatus degradationStatus = checkDegradationStrategy();
            details.put("degradationStrategy", degradationStatus.getDetails());
            if (!degradationStatus.isHealthy()) {
                isHealthy = false;
                issues.append("DegradationStrategy: ").append(degradationStatus.getDetails().get("reason")).append("; ");
            }
            
            // 检查缓存和重试机制状态
            HealthStatus cacheStatus = checkCacheAndRetry();
            details.put("cacheAndRetry", cacheStatus.getDetails());
            if (!cacheStatus.isHealthy()) {
                isHealthy = false;
                issues.append("CacheAndRetry: ").append(cacheStatus.getDetails().get("reason")).append("; ");
            }
            
            // 检查异步处理器状态
            HealthStatus asyncStatus = checkAsyncProcessor();
            details.put("asyncProcessor", asyncStatus.getDetails());
            if (!asyncStatus.isHealthy()) {
                isHealthy = false;
                issues.append("AsyncProcessor: ").append(asyncStatus.getDetails().get("reason")).append("; ");
            }
            
            // 检查内存管理器状态
            HealthStatus memoryStatus = checkMemoryManager();
            details.put("memoryManager", memoryStatus.getDetails());
            if (!memoryStatus.isHealthy()) {
                isHealthy = false;
                issues.append("MemoryManager: ").append(memoryStatus.getDetails().get("reason")).append("; ");
            }
            
            // 检查Micrometer注册表状态
            HealthStatus registryStatus = checkMeterRegistry();
            details.put("meterRegistry", registryStatus.getDetails());
            if (!registryStatus.isHealthy()) {
                isHealthy = false;
                issues.append("MeterRegistry: ").append(registryStatus.getDetails().get("reason")).append("; ");
            }
            
            // 添加整体统计信息
            details.put("summary", createSummary());
            
            return new HealthCheckResult(isHealthy, details, issues.toString());
            
        } catch (Exception e) {
            logger.error("监控系统健康检查失败", e);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("error", "监控系统健康检查失败: " + e.getMessage());
            return new HealthCheckResult(false, errorDetails, "Health check exception: " + e.getMessage());
        }
    }
    
    /**
     * 检查错误处理器状态
     */
    private HealthStatus checkErrorHandler() {
        try {
            MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
            Map<String, Object> details = new HashMap<>();
            
            details.put("activeErrorComponents", stats.getActiveErrorComponents());
            details.put("degradedComponents", stats.getDegradedComponents());
            details.put("totalErrors", stats.getTotalErrors());
            
            boolean isHealthy = stats.getDegradedComponents() <= MAX_DEGRADED_COMPONENTS;
            details.put("status", isHealthy ? "UP" : "DOWN");
            details.put("reason", isHealthy ? "正常" : "降级组件过多");
            
            return new HealthStatus(isHealthy, details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }
    
    /**
     * 检查降级策略状态
     */
    private HealthStatus checkDegradationStrategy() {
        try {
            MetricsDegradationStrategy.DegradationStatus status = degradationStrategy.getDegradationStatus();
            Map<String, Object> details = new HashMap<>();
            
            details.put("level", status.getLevel().name());
            details.put("levelDescription", status.getLevel().getDescription());
            details.put("samplingRate", status.getSamplingRate());
            details.put("autoModeEnabled", status.isAutoModeEnabled());
            details.put("timeSinceLastChange", status.getTimeSinceLastChange().toMillis());
            details.put("errorComponentCount", status.getErrorComponentCount());
            
            // 紧急降级状态视为不健康
            boolean isHealthy = status.getLevel() != MetricsDegradationStrategy.DegradationLevel.EMERGENCY;
            details.put("status", isHealthy ? "UP" : "DOWN");
            details.put("reason", isHealthy ? "正常" : "处于紧急降级状态");
            
            return new HealthStatus(isHealthy, details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }
    
    /**
     * 检查缓存和重试机制状态
     */
    private HealthStatus checkCacheAndRetry() {
        try {
            MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();
            Map<String, Object> details = new HashMap<>();
            
            details.put("cacheSize", stats.getCurrentSize());
            details.put("maxCacheSize", stats.getMaxSize());
            details.put("cacheUsageRatio", stats.getUsageRatio());
            details.put("activeRetries", stats.getActiveRetries());
            details.put("queuedRetries", stats.getQueuedRetries());
            
            // 缓存使用率过高或重试过多视为不健康
            boolean isHealthy = stats.getUsageRatio() < 0.9 && stats.getActiveRetries() < 100;
            details.put("status", isHealthy ? "UP" : "DOWN");
            details.put("reason", isHealthy ? "正常" : "缓存使用率过高或重试过多");
            
            return new HealthStatus(isHealthy, details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }
    
    /**
     * 检查异步处理器状态
     */
    private HealthStatus checkAsyncProcessor() {
        try {
            AsyncMetricsProcessor.ProcessingStats stats = asyncProcessor.getStats();
            Map<String, Object> details = new HashMap<>();
            
            details.put("queueSize", stats.getQueueSize());
            details.put("processedCount", stats.getProcessedCount());
            details.put("droppedCount", stats.getDroppedCount());
            details.put("circuitBreakerState", stats.getCircuitBreakerState());
            
            // 队列积压过多或丢弃过多视为不健康
            boolean isHealthy = stats.getQueueSize() < 1000 && stats.getDroppedCount() < stats.getProcessedCount() * 0.1;
            details.put("status", isHealthy ? "UP" : "DOWN");
            details.put("reason", isHealthy ? "正常" : "队列积压或丢弃过多");
            
            return new HealthStatus(isHealthy, details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }
    
    /**
     * 检查内存管理器状态
     */
    private HealthStatus checkMemoryManager() {
        try {
            MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
            Map<String, Object> details = new HashMap<>();
            
            details.put("usedMemory", stats.getUsedMemory());
            details.put("maxMemory", stats.getMaxMemory());
            details.put("memoryUsageRatio", stats.getMemoryUsageRatio());
            details.put("cacheSize", stats.getCacheSize());
            details.put("memoryCleanups", stats.getMemoryCleanups());
            
            // 内存使用率过高视为不健康
            boolean isHealthy = stats.getMemoryUsageRatio() < MAX_MEMORY_USAGE;
            details.put("status", isHealthy ? "UP" : "DOWN");
            details.put("reason", isHealthy ? "正常" : "内存使用率过高");
            
            return new HealthStatus(isHealthy, details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }
    
    /**
     * 检查Micrometer注册表状态
     */
    private HealthStatus checkMeterRegistry() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            int meterCount = meterRegistry.getMeters().size();
            details.put("meterCount", meterCount);
            details.put("registryClass", meterRegistry.getClass().getSimpleName());
            
            // 检查是否有基本的指标
            boolean hasBasicMetrics = meterRegistry.getMeters().stream()
                .anyMatch(meter -> meter.getId().getName().startsWith("jairouter"));
            
            details.put("hasJaiRouterMetrics", hasBasicMetrics);
            
            boolean isHealthy = meterCount > 0 && hasBasicMetrics;
            details.put("status", isHealthy ? "UP" : "DOWN");
            details.put("reason", isHealthy ? "正常" : "缺少基本指标");
            
            return new HealthStatus(isHealthy, details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }
    
    /**
     * 创建整体摘要信息
     */
    private Map<String, Object> createSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // 统计各组件状态
            int totalComponents = 6; // 总组件数
            int healthyComponents = 0;
            
            if (checkErrorHandler().isHealthy()) healthyComponents++;
            if (checkDegradationStrategy().isHealthy()) healthyComponents++;
            if (checkCacheAndRetry().isHealthy()) healthyComponents++;
            if (checkAsyncProcessor().isHealthy()) healthyComponents++;
            if (checkMemoryManager().isHealthy()) healthyComponents++;
            if (checkMeterRegistry().isHealthy()) healthyComponents++;
            
            summary.put("totalComponents", totalComponents);
            summary.put("healthyComponents", healthyComponents);
            summary.put("healthRatio", (double) healthyComponents / totalComponents);
            summary.put("overallStatus", healthyComponents == totalComponents ? "HEALTHY" : "DEGRADED");
            summary.put("checkTime", Instant.now().toString());
            
        } catch (Exception e) {
            summary.put("error", "创建摘要信息失败: " + e.getMessage());
        }
        
        return summary;
    }
    
    /**
     * 健康状态封装类
     */
    private static class HealthStatus {
        private final boolean healthy;
        private final Map<String, Object> details;
        
        public HealthStatus(boolean healthy, Map<String, Object> details) {
            this.healthy = healthy;
            this.details = details;
        }
        
        public boolean isHealthy() { return healthy; }
        public Map<String, Object> getDetails() { return details; }
    }
    
    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        private final boolean healthy;
        private final Map<String, Object> details;
        private final String issues;
        
        public HealthCheckResult(boolean healthy, Map<String, Object> details, String issues) {
            this.healthy = healthy;
            this.details = details;
            this.issues = issues;
        }
        
        public boolean isHealthy() { return healthy; }
        public Map<String, Object> getDetails() { return details; }
        public String getIssues() { return issues; }
    }
}
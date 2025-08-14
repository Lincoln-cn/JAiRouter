package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Conditional;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控管理控制器
 * 提供监控配置管理和性能统计查询的API接口
 */
@RestController
@RequestMapping("/actuator/jairouter-metrics")
@Conditional(MonitoringEnabledCondition.class)
public class MonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);

    private final MonitoringProperties monitoringProperties;
    private final AsyncMetricsCollector asyncMetricsCollector;
    private final MetricsCircuitBreaker circuitBreaker;
    private final MetricsMemoryManager memoryManager;
    private final DynamicMonitoringConfigUpdater configUpdater;

    public MonitoringController(MonitoringProperties monitoringProperties,
                              AsyncMetricsCollector asyncMetricsCollector,
                              MetricsCircuitBreaker circuitBreaker,
                              MetricsMemoryManager memoryManager,
                              DynamicMonitoringConfigUpdater configUpdater) {
        this.monitoringProperties = monitoringProperties;
        this.asyncMetricsCollector = asyncMetricsCollector;
        this.circuitBreaker = circuitBreaker;
        this.memoryManager = memoryManager;
        this.configUpdater = configUpdater;
    }

    /**
     * 获取监控系统整体状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // 基本配置信息
            status.put("enabled", monitoringProperties.isEnabled());
            status.put("prefix", monitoringProperties.getPrefix());
            status.put("enabledCategories", monitoringProperties.getEnabledCategories());
            
            // 性能统计
            AsyncMetricsCollector.PerformanceStats perfStats = asyncMetricsCollector.getPerformanceStats();
            status.put("asyncProcessingEnabled", perfStats.isAsyncProcessingEnabled());
            status.put("processingStats", perfStats.getProcessingStats());
            status.put("memoryStats", perfStats.getMemoryStats());
            
            // 熔断器状态
            status.put("circuitBreakerStats", circuitBreaker.getStats());
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error getting monitoring status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取性能统计信息
     */
    @GetMapping("/performance")
    public ResponseEntity<AsyncMetricsCollector.PerformanceStats> getPerformanceStats() {
        try {
            AsyncMetricsCollector.PerformanceStats stats = asyncMetricsCollector.getPerformanceStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting performance stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取内存统计信息
     */
    @GetMapping("/memory")
    public ResponseEntity<MetricsMemoryManager.MemoryStats> getMemoryStats() {
        try {
            MetricsMemoryManager.MemoryStats stats = memoryManager.getMemoryStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting memory stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取熔断器统计信息
     */
    @GetMapping("/circuit-breaker")
    public ResponseEntity<MetricsCircuitBreaker.CircuitBreakerStats> getCircuitBreakerStats() {
        try {
            MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting circuit breaker stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取当前采样率配置
     */
    @GetMapping("/sampling")
    public ResponseEntity<MonitoringProperties.SamplingConfig> getSamplingConfig() {
        try {
            MonitoringProperties.SamplingConfig config = monitoringProperties.getSampling();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            logger.error("Error getting sampling config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新采样率配置
     */
    @PostMapping("/sampling")
    public ResponseEntity<String> updateSamplingConfig(@RequestBody SamplingUpdateRequest request) {
        try {
            MonitoringProperties.SamplingConfig sampling = monitoringProperties.getSampling();
            
            if (request.getRequestMetrics() != null) {
                sampling.setRequestMetrics(request.getRequestMetrics());
            }
            if (request.getBackendMetrics() != null) {
                sampling.setBackendMetrics(request.getBackendMetrics());
            }
            if (request.getInfrastructureMetrics() != null) {
                sampling.setInfrastructureMetrics(request.getInfrastructureMetrics());
            }
            
            logger.info("Updated sampling config: request={}, backend={}, infrastructure={}", 
                       sampling.getRequestMetrics(), sampling.getBackendMetrics(), sampling.getInfrastructureMetrics());
            
            return ResponseEntity.ok("Sampling configuration updated successfully");
        } catch (Exception e) {
            logger.error("Error updating sampling config", e);
            return ResponseEntity.internalServerError().body("Failed to update sampling configuration: " + e.getMessage());
        }
    }

    /**
     * 获取性能配置
     */
    @GetMapping("/performance-config")
    public ResponseEntity<MonitoringProperties.PerformanceConfig> getPerformanceConfig() {
        try {
            MonitoringProperties.PerformanceConfig config = monitoringProperties.getPerformance();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            logger.error("Error getting performance config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新性能配置
     */
    @PostMapping("/performance-config")
    public ResponseEntity<String> updatePerformanceConfig(@RequestBody PerformanceUpdateRequest request) {
        try {
            MonitoringProperties.PerformanceConfig performance = monitoringProperties.getPerformance();
            
            if (request.getAsyncProcessing() != null) {
                performance.setAsyncProcessing(request.getAsyncProcessing());
            }
            if (request.getBatchSize() != null) {
                performance.setBatchSize(request.getBatchSize());
            }
            if (request.getBufferSize() != null) {
                performance.setBufferSize(request.getBufferSize());
            }
            
            logger.info("Updated performance config: async={}, batchSize={}, bufferSize={}", 
                       performance.isAsyncProcessing(), performance.getBatchSize(), performance.getBufferSize());
            
            return ResponseEntity.ok("Performance configuration updated successfully");
        } catch (Exception e) {
            logger.error("Error updating performance config", e);
            return ResponseEntity.internalServerError().body("Failed to update performance configuration: " + e.getMessage());
        }
    }

    /**
     * 强制执行内存清理
     */
    @PostMapping("/memory/cleanup")
    public ResponseEntity<String> forceMemoryCleanup() {
        try {
            memoryManager.performMemoryCheck();
            logger.info("Manual memory cleanup triggered");
            return ResponseEntity.ok("Memory cleanup completed");
        } catch (Exception e) {
            logger.error("Error during manual memory cleanup", e);
            return ResponseEntity.internalServerError().body("Memory cleanup failed: " + e.getMessage());
        }
    }

    /**
     * 清空指标缓存
     */
    @PostMapping("/memory/clear-cache")
    public ResponseEntity<String> clearMetricsCache() {
        try {
            memoryManager.clearCache();
            logger.info("Metrics cache cleared manually");
            return ResponseEntity.ok("Metrics cache cleared");
        } catch (Exception e) {
            logger.error("Error clearing metrics cache", e);
            return ResponseEntity.internalServerError().body("Failed to clear cache: " + e.getMessage());
        }
    }

    /**
     * 熔断器控制
     */
    @PostMapping("/circuit-breaker/{action}")
    public ResponseEntity<String> controlCircuitBreaker(@PathVariable String action) {
        try {
            switch (action.toLowerCase()) {
                case "open":
                    circuitBreaker.forceOpen();
                    logger.info("Circuit breaker forced to OPEN state");
                    return ResponseEntity.ok("Circuit breaker opened");
                    
                case "close":
                    circuitBreaker.forceClose();
                    logger.info("Circuit breaker forced to CLOSED state");
                    return ResponseEntity.ok("Circuit breaker closed");
                    
                default:
                    return ResponseEntity.badRequest().body("Invalid action. Use 'open' or 'close'");
            }
        } catch (Exception e) {
            logger.error("Error controlling circuit breaker", e);
            return ResponseEntity.internalServerError().body("Circuit breaker control failed: " + e.getMessage());
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // 检查各组件状态
            boolean isHealthy = true;
            StringBuilder issues = new StringBuilder();
            
            // 检查内存使用
            MetricsMemoryManager.MemoryStats memoryStats = memoryManager.getMemoryStats();
            if (memoryStats.getMemoryUsageRatio() > 0.9) {
                isHealthy = false;
                issues.append("High memory usage: ").append(String.format("%.2f%%", memoryStats.getMemoryUsageRatio() * 100)).append("; ");
            }
            
            // 检查熔断器状态
            MetricsCircuitBreaker.CircuitBreakerStats cbStats = circuitBreaker.getStats();
            if ("OPEN".equals(cbStats.getState())) {
                isHealthy = false;
                issues.append("Circuit breaker is OPEN; ");
            }
            
            health.put("status", isHealthy ? "UP" : "DOWN");
            health.put("details", isHealthy ? "All systems operational" : issues.toString());
            health.put("memoryUsage", String.format("%.2f%%", memoryStats.getMemoryUsageRatio() * 100));
            health.put("circuitBreakerState", cbStats.getState());
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Error getting health status", e);
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "DOWN");
            errorHealth.put("details", "Health check failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorHealth);
        }
    }

    // 请求DTO类
    public static class SamplingUpdateRequest {
        private Double requestMetrics;
        private Double backendMetrics;
        private Double infrastructureMetrics;

        // Getters and Setters
        public Double getRequestMetrics() { return requestMetrics; }
        public void setRequestMetrics(Double requestMetrics) { this.requestMetrics = requestMetrics; }
        public Double getBackendMetrics() { return backendMetrics; }
        public void setBackendMetrics(Double backendMetrics) { this.backendMetrics = backendMetrics; }
        public Double getInfrastructureMetrics() { return infrastructureMetrics; }
        public void setInfrastructureMetrics(Double infrastructureMetrics) { this.infrastructureMetrics = infrastructureMetrics; }
    }

    public static class PerformanceUpdateRequest {
        private Boolean asyncProcessing;
        private Integer batchSize;
        private Integer bufferSize;

        // Getters and Setters
        public Boolean getAsyncProcessing() { return asyncProcessing; }
        public void setAsyncProcessing(Boolean asyncProcessing) { this.asyncProcessing = asyncProcessing; }
        public Integer getBatchSize() { return batchSize; }
        public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }
        public Integer getBufferSize() { return bufferSize; }
        public void setBufferSize(Integer bufferSize) { this.bufferSize = bufferSize; }
    }
}
package org.unreal.modelrouter.monitor.monitoring.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.monitor.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitor.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitor.monitoring.config.MonitoringHotReloadValidator;
import org.unreal.modelrouter.monitor.monitoring.error.MetricsErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控管理控制器
 * 提供监控配置管理和性能统计查询的API接口
 *
 * <p><b>为什么这里捕获 {@link RuntimeException} 而不是 {@code Exception}</b>（#604 批次 D）：本类只做
 * 参数校验与委托调用（collector / memory manager / circuit breaker / 配置对象），这些委托不抛受检
 * 异常；控制器边界的兜底语义（记录日志 + 500）保持不变，只是把“预期失败集合”写明。</p>
 *
 * <p><b>与控制台的分工</b>（#1172）：本类整族挂在 {@code /actuator/**} 下，{@code SecurityConfiguration} 对前缀要求 {@code ROLE_ADMIN}，是给运维做 break-glass 排障的入口，控制台不消费它。监控配置页面走 {@code PUT /api/monitoring/config/*}（{@code monitoring:config:write}），那条链路经 {@link DynamicMonitoringConfigUpdater}、会发配置变更事件并写 {@code MONITORING_CONFIG_CHANGED} 审计；本类直接改配置对象字段、不发事件也不写审计。因此与控制器重复的两个写入口（{@code POST /sampling}、{@code POST /performance-config}）已标注 {@code @Deprecated}，取值校验与控制器共用 {@link MonitoringHotReloadValidator}，非法输入返回 400 而不是 500。</p>
 */
@RestController
@RequestMapping("/actuator/metrics")
@Conditional(MonitoringEnabledCondition.class)
public class MetricsManagementController {

    private static final Logger logger = LoggerFactory.getLogger(MetricsManagementController.class);

    private final MonitoringProperties monitoringProperties;
    private final AsyncMetricsCollector asyncMetricsCollector;
    private final MetricsCircuitBreaker circuitBreaker;
    private final MetricsMemoryManager memoryManager;
    private final DynamicMonitoringConfigUpdater configUpdater;
    private final MetricsErrorHandler errorHandler;
    private final MetricsDegradationStrategy degradationStrategy;
    private final MetricsCacheAndRetry cacheAndRetry;
    private final MonitoringHealthChecker healthChecker;

    public MetricsManagementController(final MonitoringProperties monitoringProperties,
                              final AsyncMetricsCollector asyncMetricsCollector,
                              final MetricsCircuitBreaker circuitBreaker,
                              final MetricsMemoryManager memoryManager,
                              final DynamicMonitoringConfigUpdater configUpdater,
                              final MetricsErrorHandler errorHandler,
                              final MetricsDegradationStrategy degradationStrategy,
                              final MetricsCacheAndRetry cacheAndRetry,
                              final MonitoringHealthChecker healthChecker) {
        this.monitoringProperties = monitoringProperties;
        this.asyncMetricsCollector = asyncMetricsCollector;
        this.circuitBreaker = circuitBreaker;
        this.memoryManager = memoryManager;
        this.configUpdater = configUpdater;
        this.errorHandler = errorHandler;
        this.degradationStrategy = degradationStrategy;
        this.cacheAndRetry = cacheAndRetry;
        this.healthChecker = healthChecker;
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
            logger.error("Error getting memory stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取熔断器统计信息。
     *
     * <p>作用域：这里的熔断器是 {@link MetricsCircuitBreaker}，保护的是监控指标处理链路，
     * 与实例路由使用的 {@code CircuitBreakerManager} 无关；返回的统计不描述任何实例的路由熔断状态。</p>
     */
    @GetMapping("/circuit-breaker")
    public ResponseEntity<MetricsCircuitBreaker.CircuitBreakerStats> getCircuitBreakerStats() {
        try {
            MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            logger.error("Error getting circuit breaker stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取当前采样率配置
     */
    @GetMapping("/sampling")
    public ResponseEntity<MonitoringProperties.Sampling> getSamplingConfig() {
        try {
            MonitoringProperties.Sampling config = monitoringProperties.getSampling();
            return ResponseEntity.ok(config);
        } catch (RuntimeException e) {
            logger.error("Error getting sampling config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新采样率配置（内部运维入口）.
     *
     * <p>补齐 6 项采样率（含 {@code traceProcessing}/{@code traceAnalysis}）：原来只改前 4 项，后两项传了也不生效，属静默忽略。
     *
     * @deprecated 控制台与常规运维请改用 {@code PUT /api/monitoring/config/sampling}：那里走 {@link DynamicMonitoringConfigUpdater}、发配置变更事件并写审计；本端点仅供 {@code /actuator/**} 应急排障。
     */
    @Deprecated
    @PostMapping("/sampling")
    public ResponseEntity<String> updateSamplingConfig(@RequestBody final SamplingUpdateRequest request) {
        final MonitoringProperties.Sampling current = monitoringProperties.getSampling();
        try {
            final double targetRequest = MonitoringHotReloadValidator.valueOr(request.getRequestMetrics(), current.getRequestMetrics());
            final double targetBackend = MonitoringHotReloadValidator.valueOr(request.getBackendMetrics(), current.getBackendMetrics());
            final double targetInfrastructure = MonitoringHotReloadValidator.valueOr(request.getInfrastructureMetrics(), current.getInfrastructureMetrics());
            final double targetTrace = MonitoringHotReloadValidator.valueOr(request.getTraceMetrics(), current.getTraceMetrics());
            final double targetTraceProcessing = MonitoringHotReloadValidator.valueOr(request.getTraceProcessingMetrics(), current.getTraceProcessingMetrics());
            final double targetTraceAnalysis = MonitoringHotReloadValidator.valueOr(request.getTraceAnalysisMetrics(), current.getTraceAnalysisMetrics());

            MonitoringHotReloadValidator.requireSamplingRate("request-metrics", targetRequest);
            MonitoringHotReloadValidator.requireSamplingRate("backend-metrics", targetBackend);
            MonitoringHotReloadValidator.requireSamplingRate("infrastructure-metrics", targetInfrastructure);
            MonitoringHotReloadValidator.requireSamplingRate("trace-metrics", targetTrace);
            MonitoringHotReloadValidator.requireSamplingRate("trace-processing-metrics", targetTraceProcessing);
            MonitoringHotReloadValidator.requireSamplingRate("trace-analysis-metrics", targetTraceAnalysis);

            if (request.getRequestMetrics() != null) {
                current.setRequestMetrics(targetRequest);
            }
            if (request.getBackendMetrics() != null) {
                current.setBackendMetrics(targetBackend);
            }
            if (request.getInfrastructureMetrics() != null) {
                current.setInfrastructureMetrics(targetInfrastructure);
            }
            if (request.getTraceMetrics() != null) {
                current.setTraceMetrics(targetTrace);
            }
            if (request.getTraceProcessingMetrics() != null) {
                current.setTraceProcessingMetrics(targetTraceProcessing);
            }
            if (request.getTraceAnalysisMetrics() != null) {
                current.setTraceAnalysisMetrics(targetTraceAnalysis);
            }

            logger.info("Updated sampling config: request={}, backend={}, infrastructure={}, trace={}, "
                    + "traceProcessing={}, traceAnalysis={}",
                    current.getRequestMetrics(), current.getBackendMetrics(),
                    current.getInfrastructureMetrics(), current.getTraceMetrics(),
                    current.getTraceProcessingMetrics(), current.getTraceAnalysisMetrics());

            return ResponseEntity.ok("Sampling configuration updated successfully");
        } catch (ApiException e) {
            // 不把校验消息回显给调用方（CodeQL java/error-message-exposure，#1193）；细节留在日志里。
            logger.warn("Invalid sampling config: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid sampling configuration");
        } catch (RuntimeException e) {
            logger.error("Error updating sampling config", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to update sampling configuration");
        }
    }

    /**
     * 获取性能配置
     */
    @GetMapping("/performance-config")
    public ResponseEntity<MonitoringProperties.Performance> getPerformanceConfig() {
        try {
            MonitoringProperties.Performance config = monitoringProperties.getPerformance();
            return ResponseEntity.ok(config);
        } catch (RuntimeException e) {
            logger.error("Error getting performance config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新采集性能配置（内部运维入口）.
     *
     * @deprecated 控制台与常规运维请改用 {@code PUT /api/monitoring/config/performance}：那里走 {@link DynamicMonitoringConfigUpdater}、发配置变更事件并写审计；本端点仅供 {@code /actuator/**} 应急排障。
     */
    @Deprecated
    @PostMapping("/performance-config")
    public ResponseEntity<String> updatePerformanceConfig(@RequestBody final PerformanceUpdateRequest request) {
        final MonitoringProperties.Performance current = monitoringProperties.getPerformance();
        try {
            final int batchSize = request.getBatchSize() != null ? request.getBatchSize() : current.getBatchSize();
            final int bufferSize = request.getBufferSize() != null ? request.getBufferSize() : current.getBufferSize();
            MonitoringHotReloadValidator.requirePerformanceBounds(batchSize, bufferSize);

            if (request.getAsyncProcessing() != null) {
                current.setAsyncProcessing(request.getAsyncProcessing());
            }
            if (request.getBatchSize() != null) {
                current.setBatchSize(request.getBatchSize());
            }
            if (request.getBufferSize() != null) {
                current.setBufferSize(request.getBufferSize());
            }

            logger.info("Updated performance config: async={}, batchSize={}, bufferSize={}",
                    current.isAsyncProcessing(), current.getBatchSize(), current.getBufferSize());

            return ResponseEntity.ok("Performance configuration updated successfully");
        } catch (ApiException e) {
            // 不把校验消息回显给调用方（CodeQL java/error-message-exposure，#1193）；细节留在日志里。
            logger.warn("Invalid performance config: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid performance configuration");
        } catch (RuntimeException e) {
            logger.error("Error updating performance config", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to update performance configuration");
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
        } catch (RuntimeException e) {
            logger.error("Error during manual memory cleanup", e);
            return ResponseEntity.internalServerError().body("Memory cleanup failed");
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
        } catch (RuntimeException e) {
            logger.error("Error clearing metrics cache", e);
            return ResponseEntity.internalServerError().body("Failed to clear cache");
        }
    }

    /**
     * 熔断器控制（规范路径）。
     *
     * <p>作用域：这里的熔断器是 {@link MetricsCircuitBreaker}，保护的是监控指标处理链路，
     * 与实例路由使用的 {@code CircuitBreakerManager} 无关；本接口不会改变任何实例的路由熔断状态。</p>
     */
    @PostMapping("/metrics-pipeline/circuit-breaker/{action}")
    public ResponseEntity<String> controlMetricsPipelineCircuitBreaker(
            @PathVariable("action") final String action) {
        return controlCircuitBreakerInternal(action);
    }

    /**
     * 熔断器控制。
     *
     * @deprecated 兼容别名，作用于指标处理链路，不是实例路由熔断器；请改用
     *             {@code POST /actuator/metrics/metrics-pipeline/circuit-breaker/{action}}。
     */
    @Deprecated
    @PostMapping("/circuit-breaker/{action}")
    public ResponseEntity<String> controlCircuitBreaker(
            @PathVariable("action") final String action) {
        return controlCircuitBreakerInternal(action);
    }

    private ResponseEntity<String> controlCircuitBreakerInternal(final String action) {
        try {
            switch (action.toLowerCase()) {
                case "open":
                    circuitBreaker.forceOpen();
                    logger.info("Metrics pipeline circuit breaker forced to OPEN state");
                    return ResponseEntity.ok("Circuit breaker opened (metrics pipeline only; "
                            + "instance routing circuit breakers are NOT affected)");

                case "close":
                    circuitBreaker.forceClose();
                    logger.info("Metrics pipeline circuit breaker forced to CLOSED state");
                    return ResponseEntity.ok("Circuit breaker closed (metrics pipeline only; "
                            + "instance routing circuit breakers are NOT affected)");

                default:
                    return ResponseEntity.badRequest().body("Invalid action. Use 'open' or 'close' "
                            + "(this endpoint controls the metrics pipeline circuit breaker, "
                            + "not the instance routing circuit breaker)");
            }
        } catch (RuntimeException e) {
            logger.error("Error controlling metrics pipeline circuit breaker", e);
            return ResponseEntity.internalServerError().body("Circuit breaker control failed");
        }
    }

    /**
     * 获取错误处理统计信息
     */
    @GetMapping("/error-handler")
    public ResponseEntity<MetricsErrorHandler.MetricsErrorStats> getErrorHandlerStats() {
        try {
            MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            logger.error("Error getting error handler stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 重置错误状态
     */
    @PostMapping("/error-handler/reset")
    public ResponseEntity<String> resetErrorState(
            @RequestParam String component,
            @RequestParam final String operation) {
        try {
            errorHandler.resetErrorState(component, operation);
            logger.info("Reset error state for component: {}, operation: {}", component, operation);
            return ResponseEntity.ok("Error state reset successfully");
        } catch (RuntimeException e) {
            logger.error("Error resetting error state", e);
            return ResponseEntity.internalServerError().body("Failed to reset error state");
        }
    }

    /**
     * 获取降级策略状态
     */
    @GetMapping("/degradation")
    public ResponseEntity<MetricsDegradationStrategy.DegradationStatus> getDegradationStatus() {
        try {
            MetricsDegradationStrategy.DegradationStatus status = degradationStrategy.getDegradationStatus();
            return ResponseEntity.ok(status);
        } catch (RuntimeException e) {
            logger.error("Error getting degradation status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 设置降级级别
     */
    @PostMapping("/degradation/level")
    public ResponseEntity<String> setDegradationLevel(@RequestParam final String level) {
        try {
            MetricsDegradationStrategy.DegradationLevel degradationLevel = 
                MetricsDegradationStrategy.DegradationLevel.valueOf(level.toUpperCase());
            degradationStrategy.setDegradationLevel(degradationLevel);
            logger.info("Set degradation level to: {}", degradationLevel);
            return ResponseEntity.ok("Degradation level set to: " + degradationLevel.getDescription());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid degradation level");
        } catch (RuntimeException e) {
            logger.error("Error setting degradation level", e);
            return ResponseEntity.internalServerError().body("Failed to set degradation level");
        }
    }

    /**
     * 启用/禁用自动降级模式
     */
    @PostMapping("/degradation/auto-mode")
    public ResponseEntity<String> setAutoMode(@RequestParam final boolean enabled) {
        try {
            degradationStrategy.setAutoModeEnabled(enabled);
            logger.info("Auto degradation mode: {}", enabled ? "enabled" : "disabled");
            return ResponseEntity.ok("Auto mode " + (enabled ? "enabled" : "disabled"));
        } catch (RuntimeException e) {
            logger.error("Error setting auto mode", e);
            return ResponseEntity.internalServerError().body("Failed to set auto mode");
        }
    }

    /**
     * 强制恢复到正常模式
     */
    @PostMapping("/degradation/force-recovery")
    public ResponseEntity<String> forceRecovery() {
        try {
            degradationStrategy.forceRecovery();
            logger.info("Forced recovery to normal mode");
            return ResponseEntity.ok("Forced recovery completed");
        } catch (RuntimeException e) {
            logger.error("Error during forced recovery", e);
            return ResponseEntity.internalServerError().body("Forced recovery failed");
        }
    }

    /**
     * 获取缓存和重试统计信息
     */
    @GetMapping("/cache-retry")
    public ResponseEntity<MetricsCacheAndRetry.CacheStats> getCacheRetryStats() {
        try {
            MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            logger.error("Error getting cache retry stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清空指标缓存
     */
    @PostMapping("/cache-retry/clear-cache")
    public ResponseEntity<String> clearCache() {
        try {
            cacheAndRetry.clearCache();
            logger.info("Metrics cache cleared");
            return ResponseEntity.ok("Cache cleared successfully");
        } catch (RuntimeException e) {
            logger.error("Error clearing cache", e);
            return ResponseEntity.internalServerError().body("Failed to clear cache");
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        try {
            MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", result.isHealthy() ? "UP" : "DOWN");
            health.put("details", result.getDetails());
            health.put("issues", result.getIssues());
            
            return ResponseEntity.ok(health);
        } catch (RuntimeException e) {
            logger.error("Error getting health status", e);
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "DOWN");
            errorHealth.put("details", "Health check failed");
            return ResponseEntity.internalServerError().body(errorHealth);
        }
    }

    // 请求DTO类
    public static class SamplingUpdateRequest {
        private Double requestMetrics;
        private Double backendMetrics;
        private Double infrastructureMetrics;
        private Double traceMetrics;
        private Double traceProcessingMetrics;
        private Double traceAnalysisMetrics;

        // Getters and Setters
        public Double getRequestMetrics() {
            return requestMetrics;
        }

        public void setRequestMetrics(final Double requestMetrics) {
            this.requestMetrics = requestMetrics;
        }

        public Double getBackendMetrics() {
            return backendMetrics;
        }

        public void setBackendMetrics(final Double backendMetrics) {
            this.backendMetrics = backendMetrics;
        }

        public Double getInfrastructureMetrics() {
            return infrastructureMetrics;
        }

        public void setInfrastructureMetrics(final Double infrastructureMetrics) {
            this.infrastructureMetrics = infrastructureMetrics;
        }

        public Double getTraceMetrics() {
            return traceMetrics;
        }

        public void setTraceMetrics(final Double traceMetrics) {
            this.traceMetrics = traceMetrics;
        }

        public Double getTraceProcessingMetrics() {
            return traceProcessingMetrics;
        }

        public void setTraceProcessingMetrics(final Double traceProcessingMetrics) {
            this.traceProcessingMetrics = traceProcessingMetrics;
        }

        public Double getTraceAnalysisMetrics() {
            return traceAnalysisMetrics;
        }

        public void setTraceAnalysisMetrics(final Double traceAnalysisMetrics) {
            this.traceAnalysisMetrics = traceAnalysisMetrics;
        }
    }

    public static class PerformanceUpdateRequest {
        private Boolean asyncProcessing;
        private Integer batchSize;
        private Integer bufferSize;

        // Getters and Setters
        public Boolean getAsyncProcessing() {
            return asyncProcessing;
        }

        public void setAsyncProcessing(final Boolean asyncProcessing) {
            this.asyncProcessing = asyncProcessing;
        }

        public Integer getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(final Integer batchSize) {
            this.batchSize = batchSize;
        }

        public Integer getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(final Integer bufferSize) {
            this.bufferSize = bufferSize;
        }
    }
}
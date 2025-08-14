package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;
import org.unreal.modelrouter.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * 监控配置管理控制器
 * 提供监控配置的查询和动态更新功能
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);

    private final MonitoringProperties monitoringProperties;
    private final DynamicMonitoringConfigUpdater configUpdater;
    private final MetricsErrorHandler errorHandler;
    private final MonitoringHealthChecker healthChecker;
    private final MetricsCacheAndRetry cacheAndRetry;
    private final MetricsDegradationStrategy degradationStrategy;
    private final MetricsCircuitBreaker circuitBreaker;

    public MonitoringController(MonitoringProperties monitoringProperties,
                              DynamicMonitoringConfigUpdater configUpdater,
                              MetricsErrorHandler errorHandler,
                              MonitoringHealthChecker healthChecker,
                              MetricsCacheAndRetry cacheAndRetry,
                              MetricsDegradationStrategy degradationStrategy,
                              MetricsCircuitBreaker circuitBreaker) {
        this.monitoringProperties = monitoringProperties;
        this.configUpdater = configUpdater;
        this.errorHandler = errorHandler;
        this.healthChecker = healthChecker;
        this.cacheAndRetry = cacheAndRetry;
        this.degradationStrategy = degradationStrategy;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 获取当前监控配置
     */
    @GetMapping("/config")
    public ResponseEntity<MonitoringConfigResponse> getConfiguration() {
        try {
            MonitoringConfigResponse response = new MonitoringConfigResponse();
            response.enabled = monitoringProperties.isEnabled();
            response.prefix = monitoringProperties.getPrefix();
            response.collectionInterval = monitoringProperties.getCollectionInterval().toString();
            response.enabledCategories = monitoringProperties.getEnabledCategories();
            response.customTags = monitoringProperties.getCustomTags();
            response.sampling = monitoringProperties.getSampling();
            response.performance = monitoringProperties.getPerformance();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取监控配置失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新监控启用状态
     */
    @PutMapping("/config/enabled")
    public ResponseEntity<String> updateEnabled(@RequestBody Map<String, Boolean> request) {
        try {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                return ResponseEntity.badRequest().body("Missing 'enabled' parameter");
            }

            boolean updated = configUpdater.updateEnabled(enabled);
            if (updated) {
                return ResponseEntity.ok("监控状态已更新为: " + enabled);
            } else {
                return ResponseEntity.ok("监控状态无变化");
            }
        } catch (Exception e) {
            logger.error("更新监控启用状态失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新指标前缀
     */
    @PutMapping("/config/prefix")
    public ResponseEntity<String> updatePrefix(@RequestBody Map<String, String> request) {
        try {
            String prefix = request.get("prefix");
            if (prefix == null || prefix.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Missing or empty 'prefix' parameter");
            }

            if (!configUpdater.validateConfigurationChange("prefix", prefix)) {
                return ResponseEntity.badRequest().body("Invalid prefix format");
            }

            boolean updated = configUpdater.updatePrefix(prefix);
            if (updated) {
                return ResponseEntity.ok("指标前缀已更新为: " + prefix);
            } else {
                return ResponseEntity.ok("指标前缀无变化");
            }
        } catch (Exception e) {
            logger.error("更新指标前缀失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新收集间隔
     */
    @PutMapping("/config/collection-interval")
    public ResponseEntity<String> updateCollectionInterval(@RequestBody Map<String, String> request) {
        try {
            String intervalStr = request.get("interval");
            if (intervalStr == null || intervalStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Missing 'interval' parameter");
            }

            Duration interval = Duration.parse(intervalStr);
            if (!configUpdater.validateConfigurationChange("collectionInterval", interval)) {
                return ResponseEntity.badRequest().body("Invalid interval format");
            }

            boolean updated = configUpdater.updateCollectionInterval(interval);
            if (updated) {
                return ResponseEntity.ok("收集间隔已更新为: " + interval);
            } else {
                return ResponseEntity.ok("收集间隔无变化");
            }
        } catch (Exception e) {
            logger.error("更新收集间隔失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新启用的类别
     */
    @PutMapping("/config/categories")
    public ResponseEntity<String> updateEnabledCategories(@RequestBody Map<String, Set<String>> request) {
        try {
            Set<String> categories = request.get("categories");
            if (categories == null) {
                return ResponseEntity.badRequest().body("Missing 'categories' parameter");
            }

            if (!configUpdater.validateConfigurationChange("enabledCategories", categories)) {
                return ResponseEntity.badRequest().body("Invalid categories");
            }

            boolean updated = configUpdater.updateEnabledCategories(categories);
            if (updated) {
                return ResponseEntity.ok("启用类别已更新为: " + categories);
            } else {
                return ResponseEntity.ok("启用类别无变化");
            }
        } catch (Exception e) {
            logger.error("更新启用类别失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新自定义标签
     */
    @PutMapping("/config/custom-tags")
    public ResponseEntity<String> updateCustomTags(@RequestBody Map<String, Map<String, String>> request) {
        try {
            Map<String, String> customTags = request.get("customTags");
            if (customTags == null) {
                return ResponseEntity.badRequest().body("Missing 'customTags' parameter");
            }

            boolean updated = configUpdater.updateCustomTags(customTags);
            if (updated) {
                return ResponseEntity.ok("自定义标签已更新");
            } else {
                return ResponseEntity.ok("自定义标签无变化");
            }
        } catch (Exception e) {
            logger.error("更新自定义标签失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置快照
     */
    @GetMapping("/config/snapshot")
    public ResponseEntity<Map<String, Object>> getConfigurationSnapshot() {
        try {
            Map<String, Object> snapshot = configUpdater.getCurrentConfiguration();
            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            logger.error("获取配置快照失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取监控系统健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        try {
            MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", result.isHealthy() ? "UP" : "DOWN");
            response.put("healthy", result.isHealthy());
            response.put("details", result.getDetails());
            
            if (!result.isHealthy()) {
                response.put("issues", result.getIssues());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取监控系统健康状态失败: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("healthy", false);
            errorResponse.put("error", "健康检查失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取错误处理统计信息
     */
    @GetMapping("/errors/stats")
    public ResponseEntity<Map<String, Object>> getErrorStats() {
        try {
            MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("activeErrorComponents", stats.getActiveErrorComponents());
            response.put("degradedComponents", stats.getDegradedComponents());
            response.put("totalErrors", stats.getTotalErrors());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取错误统计信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 重置指定组件的错误状态
     */
    @PostMapping("/errors/reset")
    public ResponseEntity<String> resetErrorState(@RequestBody Map<String, String> request) {
        try {
            String component = request.get("component");
            String operation = request.get("operation");
            
            if (component == null || operation == null) {
                return ResponseEntity.badRequest().body("Missing 'component' or 'operation' parameter");
            }
            
            errorHandler.resetErrorState(component, operation);
            return ResponseEntity.ok("错误状态已重置: " + component + ":" + operation);
        } catch (Exception e) {
            logger.error("重置错误状态失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("重置失败: " + e.getMessage());
        }
    }

    /**
     * 获取降级策略状态
     */
    @GetMapping("/degradation/status")
    public ResponseEntity<Map<String, Object>> getDegradationStatus() {
        try {
            MetricsDegradationStrategy.DegradationStatus status = degradationStrategy.getDegradationStatus();
            
            Map<String, Object> response = new HashMap<>();
            response.put("level", status.getLevel().name());
            response.put("levelDescription", status.getLevel().getDescription());
            response.put("samplingRate", status.getSamplingRate());
            response.put("autoModeEnabled", status.isAutoModeEnabled());
            response.put("timeSinceLastChange", status.getTimeSinceLastChange().toMillis());
            response.put("errorComponentCount", status.getErrorComponentCount());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取降级状态失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 设置降级级别
     */
    @PostMapping("/degradation/level")
    public ResponseEntity<String> setDegradationLevel(@RequestBody Map<String, String> request) {
        try {
            String levelStr = request.get("level");
            if (levelStr == null) {
                return ResponseEntity.badRequest().body("Missing 'level' parameter");
            }
            
            MetricsDegradationStrategy.DegradationLevel level;
            try {
                level = MetricsDegradationStrategy.DegradationLevel.valueOf(levelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Invalid degradation level: " + levelStr);
            }
            
            degradationStrategy.setDegradationLevel(level);
            return ResponseEntity.ok("降级级别已设置为: " + level.getDescription());
        } catch (Exception e) {
            logger.error("设置降级级别失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("设置失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用降级策略自动模式
     */
    @PostMapping("/degradation/auto-mode")
    public ResponseEntity<String> setAutoMode(@RequestBody Map<String, Boolean> request) {
        try {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                return ResponseEntity.badRequest().body("Missing 'enabled' parameter");
            }
            
            degradationStrategy.setAutoModeEnabled(enabled);
            return ResponseEntity.ok("降级策略自动模式已" + (enabled ? "启用" : "禁用"));
        } catch (Exception e) {
            logger.error("设置自动模式失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("设置失败: " + e.getMessage());
        }
    }

    /**
     * 强制恢复到正常模式
     */
    @PostMapping("/degradation/force-recovery")
    public ResponseEntity<String> forceRecovery() {
        try {
            degradationStrategy.forceRecovery();
            return ResponseEntity.ok("已强制恢复到正常模式");
        } catch (Exception e) {
            logger.error("强制恢复失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("恢复失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("currentSize", stats.getCurrentSize());
            response.put("maxSize", stats.getMaxSize());
            response.put("usageRatio", stats.getUsageRatio());
            response.put("activeRetries", stats.getActiveRetries());
            response.put("queuedRetries", stats.getQueuedRetries());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取缓存统计信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清空缓存
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        try {
            cacheAndRetry.clearCache();
            return ResponseEntity.ok("缓存已清空");
        } catch (Exception e) {
            logger.error("清空缓存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("清空失败: " + e.getMessage());
        }
    }

    /**
     * 获取熔断器状态
     */
    @GetMapping("/circuit-breaker/stats")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStats() {
        try {
            MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("state", stats.getState());
            response.put("failureCount", stats.getFailureCount());
            response.put("successCount", stats.getSuccessCount());
            response.put("requestCount", stats.getRequestCount());
            response.put("failureRate", stats.getFailureRate());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取熔断器统计信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 强制开启熔断器
     */
    @PostMapping("/circuit-breaker/force-open")
    public ResponseEntity<String> forceOpenCircuitBreaker() {
        try {
            circuitBreaker.forceOpen();
            return ResponseEntity.ok("熔断器已强制开启");
        } catch (Exception e) {
            logger.error("强制开启熔断器失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("操作失败: " + e.getMessage());
        }
    }

    /**
     * 强制关闭熔断器
     */
    @PostMapping("/circuit-breaker/force-close")
    public ResponseEntity<String> forceCloseCircuitBreaker() {
        try {
            circuitBreaker.forceClose();
            return ResponseEntity.ok("熔断器已强制关闭");
        } catch (Exception e) {
            logger.error("强制关闭熔断器失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("操作失败: " + e.getMessage());
        }
    }

    /**
     * 获取监控系统整体状态概览
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getMonitoringOverview() {
        try {
            Map<String, Object> overview = new HashMap<>();
            
            // 基本配置信息
            overview.put("enabled", monitoringProperties.isEnabled());
            overview.put("prefix", monitoringProperties.getPrefix());
            overview.put("enabledCategories", monitoringProperties.getEnabledCategories());
            
            // 健康状态
            MonitoringHealthChecker.HealthCheckResult healthResult = healthChecker.performHealthCheck();
            overview.put("healthy", healthResult.isHealthy());
            
            // 错误统计
            MetricsErrorHandler.MetricsErrorStats errorStats = errorHandler.getErrorStats();
            overview.put("errorStats", Map.of(
                "activeErrorComponents", errorStats.getActiveErrorComponents(),
                "degradedComponents", errorStats.getDegradedComponents(),
                "totalErrors", errorStats.getTotalErrors()
            ));
            
            // 降级状态
            MetricsDegradationStrategy.DegradationStatus degradationStatus = degradationStrategy.getDegradationStatus();
            overview.put("degradationStatus", Map.of(
                "level", degradationStatus.getLevel().name(),
                "samplingRate", degradationStatus.getSamplingRate(),
                "autoModeEnabled", degradationStatus.isAutoModeEnabled()
            ));
            
            // 缓存状态
            MetricsCacheAndRetry.CacheStats cacheStats = cacheAndRetry.getCacheStats();
            overview.put("cacheStats", Map.of(
                "usageRatio", cacheStats.getUsageRatio(),
                "activeRetries", cacheStats.getActiveRetries()
            ));
            
            // 熔断器状态
            MetricsCircuitBreaker.CircuitBreakerStats cbStats = circuitBreaker.getStats();
            overview.put("circuitBreakerStats", Map.of(
                "state", cbStats.getState(),
                "failureRate", cbStats.getFailureRate()
            ));
            
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            logger.error("获取监控概览失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 监控配置响应对象
     */
    public static class MonitoringConfigResponse {
        public boolean enabled;
        public String prefix;
        public String collectionInterval;
        public Set<String> enabledCategories;
        public Map<String, String> customTags;
        public MonitoringProperties.SamplingConfig sampling;
        public MonitoringProperties.PerformanceConfig performance;
    }
}
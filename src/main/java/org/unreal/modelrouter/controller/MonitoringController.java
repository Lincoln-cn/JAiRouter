package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;
import org.unreal.modelrouter.controller.response.RouterResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    public Mono<RouterResponse<MonitoringConfigResponse>> getConfiguration() {
        return Mono.fromCallable(() -> {
            MonitoringConfigResponse response = new MonitoringConfigResponse();
            response.enabled = monitoringProperties.isEnabled();
            response.prefix = monitoringProperties.getPrefix();
            response.collectionInterval = monitoringProperties.getCollectionInterval().toString();
            response.enabledCategories = monitoringProperties.getEnabledCategories();
            response.customTags = monitoringProperties.getCustomTags();
            response.sampling = monitoringProperties.getSampling();
            response.performance = monitoringProperties.getPerformance();

            return RouterResponse.success(response);
        }).onErrorResume(e -> {
            logger.error("获取监控配置失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<MonitoringConfigResponse>error("获取监控配置失败"));
        });
    }

    /**
     * 更新监控启用状态
     *
     * NOTE: changed return generic to Object to avoid generic inference issues with RouterResponse.success(...) implementations
     */
    @PutMapping("/config/enabled")
    public Mono<RouterResponse<Object>> updateEnabled(@RequestBody Map<String, Boolean> request) {
        return Mono.fromCallable(() -> {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                return RouterResponse.<Object>error("Missing 'enabled' parameter");
            }

            boolean updated = configUpdater.updateBasicConfig(enabled, monitoringProperties.getPrefix(),
                               monitoringProperties.getCollectionInterval(), monitoringProperties.getEnabledCategories());
            if (updated) {
                return RouterResponse.<Object>success("监控状态已更新为: " + enabled);
            } else {
                return RouterResponse.<Object>success("监控状态无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新监控启用状态失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("更新失败: " + e.getMessage()));
        });
    }

    /**
     * 更新指标前缀
     */
    @PutMapping("/config/prefix")
    public Mono<RouterResponse<Object>> updatePrefix(@RequestBody Map<String, String> request) {
        return Mono.fromCallable(() -> {
            String prefix = request.get("prefix");
            if (prefix == null || prefix.trim().isEmpty()) {
                return RouterResponse.<Object>error("Missing or empty 'prefix' parameter");
            }

            if (!configUpdater.validateConfigurationChange("prefix", prefix)) {
                return RouterResponse.<Object>error("Invalid prefix format");
            }

            boolean updated = configUpdater.updateBasicConfig(monitoringProperties.isEnabled(), prefix,
                               monitoringProperties.getCollectionInterval(), monitoringProperties.getEnabledCategories());
            if (updated) {
                return RouterResponse.<Object>success("指标前缀已更新为: " + prefix);
            } else {
                return RouterResponse.<Object>success("指标前缀无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新指标前缀失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("更新失败: " + e.getMessage()));
        });
    }

    /**
     * 更新收集间隔
     */
    @PutMapping("/config/collection-interval")
    public Mono<RouterResponse<Object>> updateCollectionInterval(@RequestBody Map<String, String> request) {
        return Mono.fromCallable(() -> {
            String intervalStr = request.get("interval");
            if (intervalStr == null || intervalStr.trim().isEmpty()) {
                return RouterResponse.<Object>error("Missing 'interval' parameter");
            }

            Duration interval = Duration.parse(intervalStr);
            if (!configUpdater.validateConfigurationChange("collectionInterval", interval)) {
                return RouterResponse.<Object>error("Invalid interval format");
            }

            boolean updated = configUpdater.updateBasicConfig(monitoringProperties.isEnabled(), monitoringProperties.getPrefix(),
                               interval, monitoringProperties.getEnabledCategories());
            if (updated) {
                return RouterResponse.<Object>success("收集间隔已更新为: " + interval);
            } else {
                return RouterResponse.<Object>success("收集间隔无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新收集间隔失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("更新失败: " + e.getMessage()));
        });
    }

    /**
     * 更新启用的类别
     */
    @PutMapping("/config/categories")
    public Mono<RouterResponse<Object>> updateEnabledCategories(@RequestBody Map<String, Set<String>> request) {
        return Mono.fromCallable(() -> {
            Set<String> categories = request.get("categories");
            if (categories == null) {
                return RouterResponse.<Object>error("Missing 'categories' parameter");
            }

            if (!configUpdater.validateConfigurationChange("enabledCategories", categories)) {
                return RouterResponse.<Object>error("Invalid categories");
            }

            boolean updated = configUpdater.updateBasicConfig(monitoringProperties.isEnabled(), monitoringProperties.getPrefix(),
                               monitoringProperties.getCollectionInterval(), categories);
            if (updated) {
                return RouterResponse.<Object>success("启用类别已更新为: " + categories);
            } else {
                return RouterResponse.<Object>success("启用类别无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新启用类别失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("更新失败: " + e.getMessage()));
        });
    }

    /**
     * 更新自定义标签
     */
    @PutMapping("/config/custom-tags")
    public Mono<RouterResponse<Object>> updateCustomTags(@RequestBody Map<String, Map<String, String>> request) {
        return Mono.fromCallable(() -> {
            Map<String, String> customTags = request.get("customTags");
            if (customTags == null) {
                return RouterResponse.<Object>error("Missing 'customTags' parameter");
            }

            // 注意：DynamicMonitoringConfigUpdater 不支持直接更新 customTags，
            // 因此我们只记录日志并返回成功消息
            logger.info("自定义标签更新请求: {}", customTags);
            return RouterResponse.<Object>success("自定义标签更新请求已接收（注意：此功能尚未实现）");
        }).onErrorResume(e -> {
            logger.error("更新自定义标签失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("更新失败: " + e.getMessage()));
        });
    }

    /**
     * 获取配置快照
     */
    @GetMapping("/config/snapshot")
    public Mono<RouterResponse<Map<String, Object>>> getConfigurationSnapshot() {
        return Mono.fromCallable(() -> {
            Map<String, Object> snapshot = configUpdater.getCurrentConfiguration();
            return RouterResponse.success(snapshot);
        }).onErrorResume(e -> {
            logger.error("获取配置快照失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取配置快照失败"));
        });
    }

    /**
     * 获取监控系统健康状态
     */
    @GetMapping("/health")
    public Mono<RouterResponse<Map<String, Object>>> getHealthStatus() {
        return Mono.fromCallable(() -> {
            MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

            Map<String, Object> response = new HashMap<>();
            response.put("status", result.isHealthy() ? "UP" : "DOWN");
            response.put("healthy", result.isHealthy());
            response.put("details", result.getDetails());

            if (!result.isHealthy()) {
                response.put("issues", result.getIssues());
            }

            return RouterResponse.success(response);
        }).onErrorResume(e -> {
            logger.error("获取监控系统健康状态失败: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("healthy", false);
            errorResponse.put("error", "健康检查失败: " + e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("健康检查失败"));
        });
    }

    /**
     * 获取错误处理统计信息
     */
    @GetMapping("/errors/stats")
    public Mono<RouterResponse<Map<String, Object>>> getErrorStats() {
        return Mono.fromCallable(() -> {
            MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();

            Map<String, Object> response = new HashMap<>();
            response.put("activeErrorComponents", stats.getActiveErrorComponents());
            response.put("degradedComponents", stats.getDegradedComponents());
            response.put("totalErrors", stats.getTotalErrors());

            return RouterResponse.success(response);
        }).onErrorResume(e -> {
            logger.error("获取错误统计信息失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取错误统计信息失败"));
        });
    }

    /**
     * 重置指定组件的错误状态
     */
    @PostMapping("/errors/reset")
    public Mono<RouterResponse<Object>> resetErrorState(@RequestBody Map<String, String> request) {
        return Mono.fromCallable(() -> {
            String component = request.get("component");
            String operation = request.get("operation");

            if (component == null || operation == null) {
                return RouterResponse.<Object>error("Missing 'component' or 'operation' parameter");
            }

            errorHandler.resetErrorState(component, operation);
            return RouterResponse.<Object>success("错误状态已重置: " + component + ":" + operation);
        }).onErrorResume(e -> {
            logger.error("重置错误状态失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("重置失败: " + e.getMessage()));
        });
    }

    /**
     * 获取降级策略状态
     */
    @GetMapping("/degradation/status")
    public Mono<RouterResponse<Map<String, Object>>> getDegradationStatus() {
        return Mono.fromCallable(() -> {
            MetricsDegradationStrategy.DegradationStatus status = degradationStrategy.getDegradationStatus();

            Map<String, Object> response = new HashMap<>();
            response.put("level", status.getLevel().name());
            response.put("levelDescription", status.getLevel().getDescription());
            response.put("samplingRate", status.getSamplingRate());
            response.put("autoModeEnabled", status.isAutoModeEnabled());
            response.put("timeSinceLastChange", status.getTimeSinceLastChange().toMillis());
            response.put("errorComponentCount", status.getErrorComponentCount());

            return RouterResponse.success(response);
        }).onErrorResume(e -> {
            logger.error("获取降级状态失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取降级状态失败"));
        });
    }

    /**
     * 设置降级级别
     */
    @PostMapping("/degradation/level")
    public Mono<RouterResponse<Object>> setDegradationLevel(@RequestBody Map<String, String> request) {
        return Mono.fromCallable(() -> {
            String levelStr = request.get("level");
            if (levelStr == null) {
                return RouterResponse.<Object>error("Missing 'level' parameter");
            }

            try {
                MetricsDegradationStrategy.DegradationLevel level = MetricsDegradationStrategy.DegradationLevel.valueOf(levelStr.toUpperCase());
                degradationStrategy.setDegradationLevel(level);
                return RouterResponse.<Object>success("降级级别已设置为: " + level.getDescription());
            } catch (IllegalArgumentException e) {
                return RouterResponse.<Object>error("Invalid degradation level: " + levelStr);
            }
        }).onErrorResume(e -> {
            logger.error("设置降级级别失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("设置失败: " + e.getMessage()));
        });
    }

    /**
     * 启用/禁用降级策略自动模式
     */
    @PostMapping("/degradation/auto-mode")
    public Mono<RouterResponse<Object>> setAutoMode(@RequestBody Map<String, Boolean> request) {
        return Mono.fromCallable(() -> {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                return RouterResponse.<Object>error("Missing 'enabled' parameter");
            }

            degradationStrategy.setAutoModeEnabled(enabled);
            return RouterResponse.<Object>success("降级策略自动模式已" + (enabled ? "启用" : "禁用"));
        }).onErrorResume(e -> {
            logger.error("设置自动模式失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("设置失败: " + e.getMessage()));
        });
    }

    /**
     * 强制恢复到正常模式
     */
    @PostMapping("/degradation/force-recovery")
    public Mono<RouterResponse<Object>> forceRecovery() {
        return Mono.fromCallable(() -> {
            degradationStrategy.forceRecovery();
            return RouterResponse.<Object>success("已强制恢复到正常模式");
        }).onErrorResume(e -> {
            logger.error("强制恢复失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("恢复失败: " + e.getMessage()));
        });
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/cache/stats")
    public Mono<RouterResponse<Map<String, Object>>> getCacheStats() {
        return Mono.fromCallable(() -> {
            MetricsCacheAndRetry.CacheStats stats = cacheAndRetry.getCacheStats();

            Map<String, Object> response = new HashMap<>();
            response.put("currentSize", stats.getCurrentSize());
            response.put("maxSize", stats.getMaxSize());
            response.put("usageRatio", stats.getUsageRatio());
            response.put("activeRetries", stats.getActiveRetries());
            response.put("queuedRetries", stats.getQueuedRetries());

            return RouterResponse.success(response);
        }).onErrorResume(e -> {
            logger.error("获取缓存统计信息失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取缓存统计信息失败"));
        });
    }

    /**
     * 清空缓存
     */
    @PostMapping("/cache/clear")
    public Mono<RouterResponse<Object>> clearCache() {
        return Mono.fromCallable(() -> {
            cacheAndRetry.clearCache();
            return RouterResponse.<Object>success("缓存已清空");
        }).onErrorResume(e -> {
            logger.error("清空缓存失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("清空失败: " + e.getMessage()));
        });
    }

    /**
     * 获取熔断器状态
     */
    @GetMapping("/circuit-breaker/stats")
    public Mono<RouterResponse<Map<String, Object>>> getCircuitBreakerStats() {
        return Mono.fromCallable(() -> {
            MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();

            Map<String, Object> response = new HashMap<>();
            response.put("state", stats.getState());
            response.put("failureCount", stats.getFailureCount());
            response.put("successCount", stats.getSuccessCount());
            response.put("requestCount", stats.getRequestCount());
            response.put("failureRate", stats.getFailureRate());

            return RouterResponse.success(response);
        }).onErrorResume(e -> {
            logger.error("获取熔断器统计信息失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取熔断器统计信息失败"));
        });
    }

    /**
     * 强制开启熔断器
     */
    @PostMapping("/circuit-breaker/force-open")
    public Mono<RouterResponse<Object>> forceOpenCircuitBreaker() {
        return Mono.fromCallable(() -> {
            circuitBreaker.forceOpen();
            return RouterResponse.<Object>success("熔断器已强制开启");
        }).onErrorResume(e -> {
            logger.error("强制开启熔断器失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("操作失败: " + e.getMessage()));
        });
    }

    /**
     * 强制关闭熔断器
     */
    @PostMapping("/circuit-breaker/force-close")
    public Mono<RouterResponse<Object>> forceCloseCircuitBreaker() {
        return Mono.fromCallable(() -> {
            circuitBreaker.forceClose();
            return RouterResponse.<Object>success("熔断器已强制关闭");
        }).onErrorResume(e -> {
            logger.error("强制关闭熔断器失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Object>error("操作失败: " + e.getMessage()));
        });
    }

    /**
     * 获取监控系统整体状态概览
     */
    @GetMapping("/overview")
    public Mono<RouterResponse<Map<String, Object>>> getMonitoringOverview() {
        return Mono.fromCallable(() -> {
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

            return RouterResponse.success(overview);
        }).onErrorResume(e -> {
            logger.error("获取监控概览失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取监控概览失败"));
        });
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
        public MonitoringProperties.Sampling sampling;
        public MonitoringProperties.Performance performance;
    }
}
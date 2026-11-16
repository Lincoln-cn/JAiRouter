package org.unreal.modelrouter.monitor.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.monitor.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitor.monitoring.error.MetricsErrorHandler;
import org.unreal.modelrouter.monitor.monitoring.security.SecurityAlertService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.AuditEventType;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.common.exception.ApiExceptions;

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
    private final ObjectProvider<SecurityAlertService> alertServiceProvider;
    private final SecurityAuditService securityAuditService;

    public MonitoringController(final MonitoringProperties monitoringProperties,
                              final DynamicMonitoringConfigUpdater configUpdater,
                              final MetricsErrorHandler errorHandler,
                              final MonitoringHealthChecker healthChecker,
                              final MetricsCacheAndRetry cacheAndRetry,
                              final MetricsDegradationStrategy degradationStrategy,
                              final MetricsCircuitBreaker circuitBreaker,
                              final ObjectProvider<SecurityAlertService> alertServiceProvider,
                              final SecurityAuditService securityAuditService) {
        this.monitoringProperties = monitoringProperties;
        this.configUpdater = configUpdater;
        this.errorHandler = errorHandler;
        this.healthChecker = healthChecker;
        this.cacheAndRetry = cacheAndRetry;
        this.degradationStrategy = degradationStrategy;
        this.circuitBreaker = circuitBreaker;
        this.alertServiceProvider = alertServiceProvider;
        this.securityAuditService = securityAuditService;
    }

    /**
     * 记录一条监控配置变更 / 应急操作审计。
     *
     * <p>为什么记在控制器：监控配置的写端点没有通用审计切面，而「谁在什么时候改了采样率、
     * 强制降级、清缓存、打开指标熔断」事后必须可追（本轮决策 D4）。审计失败不阻断业务操作，
     * 与调用历史的 FULL_CONTENT_ACCESS 审计同口径。
     *
     * <p>事件类型统一用 {@link AuditEventType#MONITORING_CONFIG_CHANGED}，靠 {@code action}
     * 区分具体动作、{@code additionalData.detail} 记录目标值。
     */
    private void auditChange(final Authentication authentication, final String action, final String detail) {
        if (securityAuditService == null) {
            return;
        }
        try {
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventType(AuditEventType.MONITORING_CONFIG_CHANGED.name())
                    .userId(authentication != null ? authentication.getName() : null)
                    .resource("monitoring")
                    .action(action)
                    .success(true)
                    .additionalData(Map.of("detail", detail))
                    .build();
            securityAuditService.recordEvent(event)
                    .subscribe(null,
                            error -> logger.warn("记录监控配置审计失败: action={}, error={}", action, error.getMessage()));
        } catch (Exception e) {
            logger.warn("记录监控配置审计失败: action={}, error={}", action, e.getMessage());
        }
    }

    private static double valueOr(final Double value, final double fallback) {
        return value != null ? value : fallback;
    }

    private static void requireSamplingRate(final String name, final double value) {
        if (value < 0.0 || value > 1.0) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST.code(),
                    "采样率必须在 0~1 之间: " + name + "=" + value);
        }
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
            return Mono.error(ApiException.of(ErrorCode.INTERNAL_ERROR.code(), "获取监控配置失败"));
        });
    }

    /**
     * 更新监控启用状态
     *
     * NOTE: changed return generic to Object to avoid generic inference issues
     * with RouterResponse.success(...) implementations
     */
    @PutMapping("/config/enabled")
    public Mono<RouterResponse<Object>> updateEnabled(@RequestBody final Map<String, Boolean> request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Missing 'enabled' parameter");
            }

            boolean updated = configUpdater.updateBasicConfig(
                    enabled, monitoringProperties.getPrefix(),
                    monitoringProperties.getCollectionInterval(),
                    monitoringProperties.getEnabledCategories());
            if (updated) {
                auditChange(authentication, "CONFIG_ENABLED", "enabled=" + enabled);
                return RouterResponse.<Object>success("监控状态已更新为: " + enabled);
            } else {
                return RouterResponse.<Object>success("监控状态无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新监控启用状态失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "更新失败"));
        });
    }

    /**
     * 更新指标前缀
     */
    @PutMapping("/config/prefix")
    public Mono<RouterResponse<Object>> updatePrefix(@RequestBody final Map<String, String> request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            String prefix = request.get("prefix");
            if (prefix == null || prefix.trim().isEmpty()) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Missing or empty 'prefix' parameter");
            }

            if (!configUpdater.validateConfigurationChange("prefix", prefix)) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Invalid prefix format");
            }

            boolean updated = configUpdater.updateBasicConfig(
                    monitoringProperties.isEnabled(), prefix,
                    monitoringProperties.getCollectionInterval(),
                    monitoringProperties.getEnabledCategories());
            if (updated) {
                auditChange(authentication, "CONFIG_PREFIX", "prefix=" + prefix);
                return RouterResponse.<Object>success("指标前缀已更新为: " + prefix);
            } else {
                return RouterResponse.<Object>success("指标前缀无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新指标前缀失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "更新失败"));
        });
    }

    /**
     * 更新收集间隔
     */
    @PutMapping("/config/collection-interval")
    public Mono<RouterResponse<Object>> updateCollectionInterval(@RequestBody final Map<String, String> request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            String intervalStr = request.get("interval");
            if (intervalStr == null || intervalStr.trim().isEmpty()) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Missing 'interval' parameter");
            }

            Duration interval = Duration.parse(intervalStr);
            if (!configUpdater.validateConfigurationChange("collectionInterval", interval)) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Invalid interval format");
            }

            boolean updated = configUpdater.updateBasicConfig(
                    monitoringProperties.isEnabled(), monitoringProperties.getPrefix(),
                    interval, monitoringProperties.getEnabledCategories());
            if (updated) {
                auditChange(authentication, "CONFIG_COLLECTION_INTERVAL", "interval=" + interval);
                return RouterResponse.<Object>success("收集间隔已更新为: " + interval);
            } else {
                return RouterResponse.<Object>success("收集间隔无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新收集间隔失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "更新失败"));
        });
    }

    /**
     * 更新启用的类别
     */
    @PutMapping("/config/categories")
    public Mono<RouterResponse<Object>> updateEnabledCategories(@RequestBody final Map<String, Set<String>> request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            Set<String> categories = request.get("categories");
            if (categories == null) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Missing 'categories' parameter");
            }

            if (!configUpdater.validateConfigurationChange("enabledCategories", categories)) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Invalid categories");
            }

            boolean updated = configUpdater.updateBasicConfig(
                    monitoringProperties.isEnabled(), monitoringProperties.getPrefix(),
                    monitoringProperties.getCollectionInterval(), categories);
            if (updated) {
                auditChange(authentication, "CONFIG_CATEGORIES", "categories=" + categories);
                return RouterResponse.<Object>success("启用类别已更新为: " + categories);
            } else {
                return RouterResponse.<Object>success("启用类别无变化");
            }
        }).onErrorResume(e -> {
            logger.error("更新启用类别失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "更新失败"));
        });
    }

    /**
     * 更新采样率配置（热改，只改内存；重启回到配置文件值）。
     *
     * <p>为什么不用 {@code POST /actuator/metrics/sampling}：那个端点整族在
     * {@code /actuator/**} 的 {@code hasRole('ADMIN')} 之下，运维角色调不了；本端点走
     * {@code monitoring:config:write}，与控制台其余监控配置同一权限面。
     *
     * <p>字段为 {@code null} 表示「不改该项」，未传的项保留当前值。取值必须落在 0~1。
     */
    @PutMapping("/config/sampling")
    public Mono<RouterResponse<Object>> updateSampling(
            @RequestBody final SamplingUpdateRequest request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            MonitoringProperties.Sampling current = monitoringProperties.getSampling();

            MonitoringProperties.Sampling target = new MonitoringProperties.Sampling();
            target.setRequestMetrics(valueOr(request.getRequestMetrics(), current.getRequestMetrics()));
            target.setBackendMetrics(valueOr(request.getBackendMetrics(), current.getBackendMetrics()));
            target.setInfrastructureMetrics(valueOr(request.getInfrastructureMetrics(), current.getInfrastructureMetrics()));
            target.setTraceMetrics(valueOr(request.getTraceMetrics(), current.getTraceMetrics()));
            target.setTraceProcessingMetrics(valueOr(request.getTraceProcessingMetrics(), current.getTraceProcessingMetrics()));
            target.setTraceAnalysisMetrics(valueOr(request.getTraceAnalysisMetrics(), current.getTraceAnalysisMetrics()));

            requireSamplingRate("request-metrics", target.getRequestMetrics());
            requireSamplingRate("backend-metrics", target.getBackendMetrics());
            requireSamplingRate("infrastructure-metrics", target.getInfrastructureMetrics());
            requireSamplingRate("trace-metrics", target.getTraceMetrics());
            requireSamplingRate("trace-processing-metrics", target.getTraceProcessingMetrics());
            requireSamplingRate("trace-analysis-metrics", target.getTraceAnalysisMetrics());

            boolean updated = configUpdater.updateSamplingConfig(target);
            if (updated) {
                auditChange(authentication, "CONFIG_SAMPLING",
                        "request=" + target.getRequestMetrics()
                                + ",backend=" + target.getBackendMetrics()
                                + ",infrastructure=" + target.getInfrastructureMetrics()
                                + ",trace=" + target.getTraceMetrics()
                                + ",traceProcessing=" + target.getTraceProcessingMetrics()
                                + ",traceAnalysis=" + target.getTraceAnalysisMetrics());
                return RouterResponse.<Object>success("采样率已更新");
            }
            return RouterResponse.<Object>success("采样率无变化");
        }).onErrorResume(e -> {
            logger.error("更新采样率失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "更新失败"));
        });
    }

    /**
     * 更新采集性能配置（异步处理 / 批大小 / 缓冲区），热改且只改内存。
     *
     * <p>约束：批大小 ≥ 1，缓冲区 ≥ 批大小（与启动期 {@code MonitoringConfigurationValidator}
     * 的边界一致）；字段为 {@code null} 表示不改该项。
     */
    @PutMapping("/config/performance")
    public Mono<RouterResponse<Object>> updatePerformance(
            @RequestBody final PerformanceUpdateRequest request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            MonitoringProperties.Performance current = monitoringProperties.getPerformance();

            MonitoringProperties.Performance target = new MonitoringProperties.Performance();
            target.setAsyncProcessing(request.getAsyncProcessing() != null
                    ? request.getAsyncProcessing() : current.isAsyncProcessing());
            int batchSize = request.getBatchSize() != null ? request.getBatchSize() : current.getBatchSize();
            int bufferSize = request.getBufferSize() != null ? request.getBufferSize() : current.getBufferSize();

            if (batchSize < 1) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "批大小必须 ≥ 1: " + batchSize);
            }
            if (bufferSize < batchSize) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(),
                        "缓冲区必须 ≥ 批大小: bufferSize=" + bufferSize + ", batchSize=" + batchSize);
            }
            target.setBatchSize(batchSize);
            target.setBufferSize(bufferSize);

            boolean updated = configUpdater.updatePerformanceConfig(target);
            if (updated) {
                auditChange(authentication, "CONFIG_PERFORMANCE",
                        "async=" + target.isAsyncProcessing()
                                + ",batchSize=" + target.getBatchSize()
                                + ",bufferSize=" + target.getBufferSize());
                return RouterResponse.<Object>success("采集性能配置已更新");
            }
            return RouterResponse.<Object>success("采集性能配置无变化");
        }).onErrorResume(e -> {
            logger.error("更新采集性能配置失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "更新失败"));
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
    public Mono<RouterResponse<Object>> resetErrorState(@RequestBody final Map<String, String> request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            String component = request.get("component");
            String operation = request.get("operation");

            if (component == null || operation == null) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Missing 'component' or 'operation' parameter");
            }

            errorHandler.resetErrorState(component, operation);
            auditChange(authentication, "EMERGENCY_ERROR_RESET", component + ":" + operation);
            return RouterResponse.<Object>success("错误状态已重置: " + component + ":" + operation);
        }).onErrorResume(e -> {
            logger.error("重置错误状态失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "重置失败"));
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
    public Mono<RouterResponse<Object>> setDegradationLevel(@RequestBody final Map<String, String> request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            String levelStr = request.get("level");
            if (levelStr == null) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Missing 'level' parameter");
            }

            try {
                MetricsDegradationStrategy.DegradationLevel level =
                        MetricsDegradationStrategy.DegradationLevel.valueOf(levelStr.toUpperCase());
                degradationStrategy.setDegradationLevel(level);
                auditChange(authentication, "EMERGENCY_DEGRADATION_LEVEL", level.name());
                return RouterResponse.<Object>success("降级级别已设置为: " + level.getDescription());
            } catch (IllegalArgumentException e) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Invalid degradation level: " + levelStr);
            }
        }).onErrorResume(e -> {
            logger.error("设置降级级别失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "设置失败"));
        });
    }

    /**
     * 启用/禁用降级策略自动模式
     */
    @PostMapping("/degradation/auto-mode")
    public Mono<RouterResponse<Object>> setAutoMode(@RequestBody final Map<String, Boolean> request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "Missing 'enabled' parameter");
            }

            degradationStrategy.setAutoModeEnabled(enabled);
            auditChange(authentication, "EMERGENCY_DEGRADATION_AUTO_MODE", "enabled=" + enabled);
            return RouterResponse.<Object>success("降级策略自动模式已" + (enabled ? "启用" : "禁用"));
        }).onErrorResume(e -> {
            logger.error("设置自动模式失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "设置失败"));
        });
    }

    /**
     * 强制恢复到正常模式
     */
    @PostMapping("/degradation/force-recovery")
    public Mono<RouterResponse<Object>> forceRecovery(final Authentication authentication) {
        return Mono.fromCallable(() -> {
            degradationStrategy.forceRecovery();
            auditChange(authentication, "EMERGENCY_DEGRADATION_RECOVERY", "force-recovery");
            return RouterResponse.<Object>success("已强制恢复到正常模式");
        }).onErrorResume(e -> {
            logger.error("强制恢复失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "恢复失败"));
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
    public Mono<RouterResponse<Object>> clearCache(final Authentication authentication) {
        return Mono.fromCallable(() -> {
            cacheAndRetry.clearCache();
            auditChange(authentication, "EMERGENCY_CACHE_CLEAR", "clear");
            return RouterResponse.<Object>success("缓存已清空");
        }).onErrorResume(e -> {
            logger.error("清空缓存失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "清空失败"));
        });
    }

    // ==================== 指标处理链路熔断器（#555） ====================
    //
    // 作用域说明：下面这一组端点操作的都是 MetricsCircuitBreaker —— 它保护的是监控指标
    // 异步处理链路（AsyncMetricsProcessor），与实例路由使用的 CircuitBreakerManager
    // 是两个不同的对象。因此 force-open 只会让指标处理被拒绝，不会熔断任何实例。

    /**
     * 获取指标处理链路熔断器状态（规范路径）。
     *
     * <p>作用域：{@link MetricsCircuitBreaker} 保护的是监控指标异步处理链路，与实例路由使用的
     * {@code CircuitBreakerManager} 无关。这里返回的统计只描述指标处理链路，不描述任何实例的
     * 路由熔断状态。</p>
     */
    @GetMapping("/metrics-pipeline/circuit-breaker/stats")
    public Mono<RouterResponse<Map<String, Object>>> getMetricsPipelineCircuitBreakerStats() {
        return getCircuitBreakerStatsInternal();
    }

    /**
     * 获取指标处理链路熔断器状态。
     *
     * @deprecated 兼容别名，作用于指标处理链路，不是实例路由熔断器；请改用
     *             {@code GET /api/monitoring/metrics-pipeline/circuit-breaker/stats}。
     *             旧路径保留只为不破坏既有前端与脚本，行为与新路径完全一致。
     */
    @Deprecated
    @GetMapping("/circuit-breaker/stats")
    public Mono<RouterResponse<Map<String, Object>>> getCircuitBreakerStats() {
        return getCircuitBreakerStatsInternal();
    }

    /**
     * 指标处理链路熔断器状态的共用实现，新旧路径都委派到这里。
     */
    private Mono<RouterResponse<Map<String, Object>>> getCircuitBreakerStatsInternal() {
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
            logger.error("获取指标处理链路熔断器统计信息失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取指标处理链路熔断器统计信息失败"));
        });
    }

    /**
     * 强制打开指标处理链路熔断器（规范路径）。
     *
     * <p>作用域：仅作用于监控指标处理链路（{@link MetricsCircuitBreaker}），不影响实例路由熔断器，
     * 也不会使任何实例进入熔断状态。</p>
     */
    @PostMapping("/metrics-pipeline/circuit-breaker/force-open")
    public Mono<RouterResponse<Object>> forceOpenMetricsPipelineCircuitBreaker(
            final Authentication authentication) {
        return forceOpenCircuitBreakerInternal(authentication);
    }

    /**
     * 强制打开指标处理链路熔断器。
     *
     * @deprecated 兼容别名，作用于指标处理链路，不是实例路由熔断器；请改用
     *             {@code POST /api/monitoring/metrics-pipeline/circuit-breaker/force-open}。
     */
    @Deprecated
    @PostMapping("/circuit-breaker/force-open")
    public Mono<RouterResponse<Object>> forceOpenCircuitBreaker(
            final Authentication authentication) {
        return forceOpenCircuitBreakerInternal(authentication);
    }

    private Mono<RouterResponse<Object>> forceOpenCircuitBreakerInternal(
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            circuitBreaker.forceOpen();
            auditChange(authentication, "EMERGENCY_METRICS_BREAKER_OPEN", "force-open");
            // 文案必须写明作用域：旧文案「熔断器已强制开启」会被误读为「强制熔断某个实例」
            return RouterResponse.<Object>success("已强制打开指标处理链路熔断器（仅影响监控指标处理，不影响实例路由熔断器）");
        }).onErrorResume(e -> {
            logger.error("强制打开指标处理链路熔断器失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "操作失败"));
        });
    }

    /**
     * 强制关闭指标处理链路熔断器（规范路径）。
     *
     * <p>作用域：仅作用于监控指标处理链路（{@link MetricsCircuitBreaker}），不影响实例路由熔断器。</p>
     */
    @PostMapping("/metrics-pipeline/circuit-breaker/force-close")
    public Mono<RouterResponse<Object>> forceCloseMetricsPipelineCircuitBreaker(
            final Authentication authentication) {
        return forceCloseCircuitBreakerInternal(authentication);
    }

    /**
     * 强制关闭指标处理链路熔断器。
     *
     * @deprecated 兼容别名，作用于指标处理链路，不是实例路由熔断器；请改用
     *             {@code POST /api/monitoring/metrics-pipeline/circuit-breaker/force-close}。
     */
    @Deprecated
    @PostMapping("/circuit-breaker/force-close")
    public Mono<RouterResponse<Object>> forceCloseCircuitBreaker(
            final Authentication authentication) {
        return forceCloseCircuitBreakerInternal(authentication);
    }

    private Mono<RouterResponse<Object>> forceCloseCircuitBreakerInternal(
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
            circuitBreaker.forceClose();
            auditChange(authentication, "EMERGENCY_METRICS_BREAKER_CLOSE", "force-close");
            return RouterResponse.<Object>success("已强制关闭指标处理链路熔断器（仅影响监控指标处理，不影响实例路由熔断器）");
        }).onErrorResume(e -> {
            logger.error("强制关闭指标处理链路熔断器失败: {}", e.getMessage());
            return Mono.error(ApiExceptions.wrap(e, "操作失败"));
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
        })
        // 近 24 小时安全告警数（#291）：告警服务在 alerts 关闭时不存在，此时不返回该键
        .flatMap(response -> {
            SecurityAlertService alertService = alertServiceProvider.getIfAvailable();
            if (alertService == null || !response.isSuccess() || response.getData() == null) {
                return Mono.just(response);
            }
            return alertService.countAlertsLast24Hours()
                    .doOnNext(count -> response.getData().put("alertsLast24Hours", count))
                    .onErrorResume(e -> {
                        logger.warn("获取近 24 小时安全告警数失败: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .thenReturn(response);
        })
        .onErrorResume(e -> {
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


    /**
     * 采样率更新请求：字段为 {@code null} 表示「不改该项」，未传的项保留当前值。
     */
    public static final class SamplingUpdateRequest {
        private Double requestMetrics;
        private Double backendMetrics;
        private Double infrastructureMetrics;
        private Double traceMetrics;
        private Double traceProcessingMetrics;
        private Double traceAnalysisMetrics;

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

    /**
     * 采集性能更新请求：字段为 {@code null} 表示「不改该项」。
     */
    public static final class PerformanceUpdateRequest {
        private Boolean asyncProcessing;
        private Integer batchSize;
        private Integer bufferSize;

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

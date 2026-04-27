package org.unreal.modelrouter.monitoring.error;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ErrorTrackerProperties;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误指标收集器
 *
 * 负责收集和暴露错误相关的 Micrometer 指标。
 * 支持多维度标签：错误代码、严重级别、模块、模型、错误类型、操作等。
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ErrorMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final ErrorTrackerProperties properties;
    private final ErrorCodeResolver errorCodeResolver;

    // 指标缓存
    private final ConcurrentHashMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> errorTimers = new ConcurrentHashMap<>();

    public ErrorMetricsCollector(final MeterRegistry meterRegistry,
                                final ErrorTrackerProperties properties,
                                final ErrorCodeResolver errorCodeResolver) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.errorCodeResolver = errorCodeResolver;

        // 注册基础错误指标
        registerBaseErrorMetrics();
    }

    /**
     * 注册基础错误指标
     */
    private void registerBaseErrorMetrics() {
        if (!properties.getMetrics().isEnabled()) {
            return;
        }

        try {
            // 总错误计数
            Counter.builder(properties.getMetrics().getCounterPrefix() + ".total")
                .description("Total number of errors")
                .register(meterRegistry);

            // 按错误类型分组的错误计数
            if (properties.getMetrics().isGroupByErrorType()) {
                // 这些指标会在记录错误时动态创建
                log.debug("启用按错误类型分组的错误指标");
            }

            // 按操作分组的错误计数
            if (properties.getMetrics().isGroupByOperation()) {
                // 这些指标会在记录错误时动态创建
                log.debug("启用按操作分组的错误指标");
            }

        } catch (Exception e) {
            log.warn("注册基础错误指标失败", e);
        }
    }

    /**
     * 记录错误指标（完整版）
     *
     * @param errorType 错误类型
     * @param operation 操作名称
     * @param errorCode 错误代码（MR-XXX-YYY 格式）
     * @param severity 严重级别（FATAL/ERROR/WARN/INFO）
     * @param module 模块名称（adapter/service/controller 等）
     * @param model 模型名称
     * @param duration 错误处理耗时
     */
    public void recordError(final String errorType,
                           final String operation,
                           final String errorCode,
                           final String severity,
                           final String module,
                           final String model,
                           final Duration duration) {
        if (!properties.getMetrics().isEnabled()) {
            return;
        }

        try {
            // 记录总错误计数
            getOrCreateErrorCounter("total", "all", "all", "all", "all", "all", "all").increment();

            // 按错误代码记录
            if (errorCode != null && !errorCode.isEmpty()) {
                getOrCreateErrorCounter("by_error_code", errorCode, "all", "all", "all", "all", "all").increment();
            }

            // 按严重级别记录
            if (severity != null && !severity.isEmpty()) {
                getOrCreateErrorCounter("by_severity", "all", severity, "all", "all", "all", "all").increment();
            }

            // 按模块记录
            if (module != null && !module.isEmpty()) {
                getOrCreateErrorCounter("by_module", "all", "all", module, "all", "all", "all").increment();
            }

            // 按模型记录
            if (model != null && !model.isEmpty()) {
                getOrCreateErrorCounter("by_model", "all", "all", "all", model, "all", "all").increment();
            }

            // 按错误类型记录
            if (properties.getMetrics().isGroupByErrorType()) {
                getOrCreateErrorCounter("by_type", errorType, "all", "all", "all", "all", "all").increment();
            }

            // 按操作记录
            if (properties.getMetrics().isGroupByOperation()) {
                getOrCreateErrorCounter("by_operation", "all", "all", "all", "all", operation, "all").increment();
            }

            // 组合维度：错误代码 + 严重级别
            if (errorCode != null && severity != null) {
                getOrCreateErrorCounter("by_code_severity", errorCode, severity, "all", "all", "all", "all").increment();
            }

            // 组合维度：模块 + 错误类型
            if (module != null && properties.getMetrics().isGroupByErrorType()) {
                getOrCreateErrorCounter("by_module_type", errorType, "all", module, "all", "all", "all").increment();
            }

            // 组合维度：模型 + 错误代码
            if (model != null && errorCode != null) {
                getOrCreateErrorCounter("by_model_code", errorCode, "all", "all", model, "all", "all").increment();
            }

            // 记录错误处理耗时
            if (properties.getMetrics().isRecordDuration() && duration != null) {
                getOrCreateErrorTimer(errorType, operation, errorCode, severity, module, model).record(duration);
            }

        } catch (Exception e) {
            log.debug("记录错误指标失败：errorType={}, operation={}", errorType, operation, e);
        }
    }

    /**
     * 记录错误指标（简化版）
     *
     * @param errorType 错误类型
     * @param operation 操作名称
     */
    public void recordError(final String errorType, final String operation) {
        recordError(errorType, operation, null);
    }

    /**
     * 记录错误指标（带时长）
     *
     * @deprecated 建议使用完整版本 {@link #recordError(String, String, String, String, String, String, Duration)}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>此版本虽然包含时长，但缺少模块和模型信息</li>
     *               <li>完整版本提供更丰富的上下文追踪</li>
     *             </ul>
     *             此方法将在 v3.0 版本中移除。
     * @param errorType 错误类型
     * @param operation 操作名称
     * @param duration 错误处理耗时
     * @see #recordError(String, String, String, String, String, String, Duration)
     * @since v2.5.8 标注废弃
     */
    @Deprecated(since = "2.5.8", forRemoval = true)
    public void recordError(final String errorType, final String operation, final Duration duration) {
        recordError(errorType, operation, null, "ERROR", null, null, duration);
    }

    /**
     * 记录错误指标（带错误代码和严重级别）
     *
     * @deprecated 建议使用完整版本 {@link #recordErrorWithContext(String, String, String, String, String, String)}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>此版本缺少模块和模型信息</li>
     *               <li>recordErrorWithContext 提供更完整的上下文</li>
     *             </ul>
     *             此方法将在 v3.0 版本中移除。
     * @param errorType 错误类型
     * @param operation 操作名称
     * @param errorCode 错误代码
     * @param severity 严重级别
     * @see #recordErrorWithContext(String, String, String, String, String, String)
     * @since v2.5.8 标注废弃
     */
    @Deprecated(since = "2.5.8", forRemoval = true)
    public void recordError(final String errorType, final String operation, final String errorCode,final String severity) {
        recordError(errorType, operation, errorCode, severity, null, null, null);
    }

    /**
     * 记录错误指标（带完整上下文）
     *
     * @param errorType 错误类型
     * @param operation 操作名称
     * @param errorCode 错误代码
     * @param severity 严重级别
     * @param module 模块名称
     * @param model 模型名称
     */
    public void recordErrorWithContext(final String errorType,
                                       final String operation,
                                       final String errorCode,
                                       final String severity,
                                       final String module,
                                       final String model) {
        recordError(errorType, operation, errorCode, severity, module, model, null);
    }

    /**
     * 从异常对象记录错误指标（自动解析错误代码和严重级别）
     *
     * @param throwable 异常对象
     * @param operation 操作名称
     * @param module 模块名称
     * @param model 模型名称
     */
    public void recordErrorFromThrowable(final Throwable throwable,
                                         final String operation,
                                         final String module,
                                         final String model) {
        String errorCode = errorCodeResolver != null ? errorCodeResolver.resolveErrorCode(throwable) : "UNK_000";
        ErrorCodeResolver.ErrorCategory category = errorCodeResolver != null ?
            errorCodeResolver.resolveErrorCategory(throwable) : ErrorCodeResolver.ErrorCategory.UNKNOWN;
        String severity = mapCategoryToSeverity(category);

        recordError(
            throwable.getClass().getSimpleName(),
            operation != null ? operation : "unknown",
            errorCode,
            severity,
            module != null ? module : "unknown",
            model != null ? model : "unknown",
            null
        );
    }

    /**
     * 将错误分类映射到严重级别
     */
    private String mapCategoryToSeverity(final ErrorCodeResolver.ErrorCategory category) {
        return switch (category) {
            case AUTHENTICATION, AUTHORIZATION -> "WARN";
            case VALIDATION -> "INFO";
            case DOWNSTREAM, NETWORK, TIMEOUT -> "ERROR";
            case SANITIZATION, SYSTEM -> "ERROR";
            case RATE_LIMIT, CIRCUIT_BREAKER -> "WARN";
            default -> "ERROR";
        };
    }

    /**
     * 获取或创建错误计数器
     *
     * @param category 分类
     * @param errorCode 错误代码
     * @param severity 严重级别
     * @param module 模块
     * @param model 模型
     * @param operation 操作
     * @param errorType 错误类型
     * @return 错误计数器
     */
    private Counter getOrCreateErrorCounter(final String category,
                                            final String errorCode,
                                            final String severity,
                                            final String module,
                                            final String model,
                                            final String operation,
                                            final String errorType) {
        String key = String.format("%s:%s:%s:%s:%s:%s:%s",
            category, errorCode, severity, module, model, operation, errorType);

        return errorCounters.computeIfAbsent(key, k -> {
            Counter.Builder builder = Counter.builder(properties.getMetrics().getCounterPrefix() + "." + category)
                .description("Error count by " + category);

            // 添加标签
            if (!"all".equals(errorCode) && errorCode != null) {
                builder.tag("error_code", errorCode);
            }
            if (!"all".equals(severity) && severity != null) {
                builder.tag("severity", severity);
            }
            if (!"all".equals(module) && module != null) {
                builder.tag("module", module);
            }
            if (!"all".equals(model) && model != null) {
                builder.tag("model", model);
            }
            if (!"all".equals(operation) && operation != null) {
                builder.tag("operation", operation);
            }
            if (!"all".equals(errorType) && errorType != null) {
                builder.tag("error_type", errorType);
            }

            return builder.register(meterRegistry);
        });
    }

    /**
     * 获取或创建错误计时器
     *
     * @param errorType 错误类型
     * @param operation 操作名称
     * @param errorCode 错误代码
     * @param severity 严重级别
     * @param module 模块
     * @param model 模型
     * @return 错误计时器
     */
    private Timer getOrCreateErrorTimer(final String errorType,
                                        final String operation,
                                        final String errorCode,
                                        final String severity,
                                        final String module,
                                        final String model) {
        String key = String.format("duration:%s:%s:%s:%s:%s:%s",
            errorType, operation, errorCode, severity, module, model);

        return errorTimers.computeIfAbsent(key, k -> {
            Timer.Builder builder = Timer.builder(properties.getMetrics().getCounterPrefix() + ".duration")
                .description("Error handling duration");

            // 添加标签
            if (errorType != null && !"all".equals(errorType)) {
                builder.tag("error_type", errorType);
            }
            if (operation != null && !"all".equals(operation)) {
                builder.tag("operation", operation);
            }
            if (errorCode != null && !"all".equals(errorCode)) {
                builder.tag("error_code", errorCode);
            }
            if (severity != null && !"all".equals(severity)) {
                builder.tag("severity", severity);
            }
            if (module != null && !"all".equals(module)) {
                builder.tag("module", module);
            }
            if (model != null && !"all".equals(model)) {
                builder.tag("model", model);
            }

            // 设置直方图桶
            if (properties.getMetrics().getHistogramBuckets() != null) {
                Duration[] buckets = java.util.Arrays.stream(properties.getMetrics().getHistogramBuckets())
                    .mapToObj(seconds -> Duration.ofMillis((long) (seconds * 1000)))
                    .toArray(Duration[]::new);
                builder.serviceLevelObjectives(buckets);
            }

            return builder.register(meterRegistry);
        });
    }

    /**
     * 获取错误指标统计信息
     *
     * @return 错误指标统计信息
     */
    public ErrorMetricsStats getErrorMetricsStats() {
        return ErrorMetricsStats.builder()
                .totalErrorCounters(errorCounters.size())
                .totalErrorTimers(errorTimers.size())
                .build();
    }

    /**
     * 错误指标统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class ErrorMetricsStats {
        private int totalErrorCounters;
        private int totalErrorTimers;

        /**
         * 获取错误类型统计信息
         *
         * @return 错误类型统计信息
         */
        public Map<String, Counter> getErrorTypeStats() {
            return Map.of();
        }

        /**
         * 获取错误位置统计信息
         *
         * @return 错误位置统计信息
         */
        public Map<String, Counter> getErrorLocationStats() {
            return Map.of();
        }
    }
}

/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.monitor.tracing.logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.encryption.TracingEncryptionService;
import org.unreal.modelrouter.monitor.tracing.sanitization.TracingSanitizationService;
import org.unreal.modelrouter.monitor.tracing.security.TracingSecurityManager;
import reactor.core.publisher.Mono;
import org.unreal.modelrouter.monitor.tracing.logger.builder.BusinessEventLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.PerformanceLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.RequestLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.ResponseLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.SecurityEventLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.dto.LogType;
import org.unreal.modelrouter.monitor.tracing.logger.dto.SecurityEventFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.PerformanceFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.BusinessEventFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.StructuredLogEntry;
import org.unreal.modelrouter.monitor.tracing.logger.dto.RequestLogFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.ResponseLogFields;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认结构化日志记录器实现 - v2.16.4重构
 *
 * 实现结构化的JSON格式日志输出，包括：
 * - 自动集成Logback MDC，添加traceId和spanId
 * - JSON格式的日志输出
 * - 敏感数据脱敏处理
 * - 异步日志处理
 * - 性能优化的日志缓存
 *
 * 重构历史：
 * - v2.16.4: 提取RequestLogBuilder和ResponseLogBuilder，委托调用
 * - v2.16.5: 使用DTO替代Map<String, Object>弱约束
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultStructuredLogger implements StructuredLogger {

    private final ObjectMapper objectMapper;
    private final SanitizationService sanitizationService;
    private final TracingConfiguration tracingConfiguration;
    private final TracingMDCManager tracingMDCManager;

    // 追踪安全组件
    private final TracingSanitizationService tracingSanitizationService;
    private final TracingSecurityManager tracingSecurityManager;
    private final TracingEncryptionService tracingEncryptionService;

    // Builder组件 - v2.16.4 委托调用
    private final SecurityEventLogBuilder securityEventLogBuilder;
    private final PerformanceLogBuilder performanceLogBuilder;
    private final BusinessEventLogBuilder businessEventLogBuilder;
    private final RequestLogBuilder requestLogBuilder;
    private final ResponseLogBuilder responseLogBuilder;

    // 缓存常用的日志字段
    private final Map<String, Object> commonFields = new ConcurrentHashMap<>();

    // ========================================
    // 请求/响应日志方法 - v2.16.4 委托Builder
    // ========================================

    @Override
    public void logRequest(final ServerHttpRequest request, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }

        // 检查访问权限并委托RequestLogBuilder构建日志
        tracingSecurityManager.canAccessTraceContext(context)
                .filter(hasPermission -> hasPermission)
                .flatMap(hasPermission -> requestLogBuilder.buildRequestFields(request, context))
                .doOnNext(fields -> {
                    try {
                        setMDC(context);

                        StructuredLogEntry logEntry = createStructuredLogEntry(LogType.REQUEST, context);
                        logEntry.setFields(fields);
                        logEntry.setMessage(requestLogBuilder.buildRequestMessage());

                        logStructuredLogEntry(logEntry);

                    } catch (Exception e) {
                        log.debug("记录请求日志时发生错误", e);
                    } finally {
                        clearMDC();
                    }
                })
                .subscribe();
    }

    @Override
    public void logResponse(final ServerHttpResponse response, final TracingContext context, final long duration) {
        if (!isLoggingEnabled()) {
            return;
        }

        try {
            setMDC(context);

            // 委托ResponseLogBuilder构建日志 - v2.16.4
            ResponseLogFields fields = responseLogBuilder.buildResponseFields(response, duration);
            StructuredLogEntry logEntry = createStructuredLogEntry(LogType.RESPONSE, context);
            logEntry.setFields(fields);
            logEntry.setMessage(responseLogBuilder.buildResponseMessage(duration));

            logStructuredLogEntry(logEntry);

        } catch (Exception e) {
            log.debug("记录响应日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    // ========================================
    // 后端调用日志方法
    // ========================================

    @Override
    public void logBackendCall(final String adapter, final String instance, final long duration,
                               final boolean success, final TracingContext context) {
        logBackendCallDetails(adapter, instance, null, null, duration, success ? 200 : 500, success, context);
    }

    @Override
    public void logBackendCallDetails(final String adapter, final String instance, final String url,
                                      final String method, final long duration, final int statusCode,
                                      final boolean success, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }

        try {
            setMDC(context);

            Map<String, Object> logEntry = createBaseLogEntry(LogType.BACKEND_CALL.getValue(), context);
            Map<String, Object> fields = new HashMap<>();

            fields.put("adapter", adapter);
            fields.put("instance", instance);
            fields.put("duration", duration);
            fields.put("success", success);
            fields.put("statusCode", statusCode);

            if (url != null) {
                fields.put("url", url);
            }
            if (method != null) {
                fields.put("method", method);
            }

            logEntry.put("fields", fields);
            logEntry.put("message", String.format("后端服务调用%s，适配器: %s，实例: %s，耗时: %dms",
                    success ? "成功" : "失败", adapter, instance, duration));

            logStructuredEntry(logEntry);

        } catch (Exception e) {
            log.debug("记录后端调用日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    // ========================================
    // 错误日志方法
    // ========================================

    @Override
    public void logError(final Throwable error, final TracingContext context) {
        logError(error, context, null);
    }

    @Override
    public void logError(final Throwable error, final TracingContext context,
                        final Map<String, Object> additionalInfo) {
        if (!isLoggingEnabled() || error == null) {
            return;
        }

        try {
            setMDC(context);

            Map<String, Object> logEntry = createBaseLogEntry(LogType.ERROR.getValue(), context);
            Map<String, Object> fields = new HashMap<>();

            fields.put("errorType", error.getClass().getSimpleName());
            fields.put("errorMessage", error.getMessage());

            // 堆栈信息（截断处理）
            if (tracingConfiguration.getLogging().isIncludeStackTrace()) {
                String stackTrace = getStackTrace(error);
                if (stackTrace != null) {
                    fields.put("stackTrace", stackTrace);
                }
            }

            if (additionalInfo != null && !additionalInfo.isEmpty()) {
                fields.put("additionalInfo", additionalInfo);
            }

            logEntry.put("fields", fields);
            logEntry.put("message", String.format("发生错误: %s", error.getMessage()));
            logEntry.put("level", "ERROR");

            logStructuredEntry(logEntry);

        } catch (Exception e) {
            log.debug("记录错误日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    // ========================================
    // 业务事件日志方法 - v2.16.5 委托Builder
    // ========================================

    @Override
    public void logBusinessEvent(final String event, final Map<String, Object> data, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }

        try {
            setMDC(context);

            // 使用DTO版本 - v2.16.5
            BusinessEventFields fields = businessEventLogBuilder.buildBusinessEvent(event, data);
            StructuredLogEntry logEntry = createStructuredLogEntry(LogType.BUSINESS_EVENT, context);
            logEntry.setFields(fields);
            logEntry.setMessage(businessEventLogBuilder.buildBusinessEventMessage(event));

            logStructuredLogEntry(logEntry);

        } catch (Exception e) {
            log.debug("记录业务事件日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    @Override
    public void logLoadBalancerDecision(final String strategy, final String selectedInstance,
                                        final int availableInstances, final TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("strategy", strategy);
        data.put("selectedInstance", selectedInstance);
        data.put("availableInstances", availableInstances);

        logBusinessEvent("load_balancer_decision", data, context);
    }

    @Override
    public void logRateLimitCheck(final String algorithm, final boolean allowed,
                                 final long remainingTokens, final TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", algorithm);
        data.put("allowed", allowed);
        data.put("remainingTokens", remainingTokens);

        logBusinessEvent("rate_limit_check", data, context);
    }

    @Override
    public void logCircuitBreakerStateChange(final String previousState, final String currentState,
                                             final String reason, final TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("previousState", previousState);
        data.put("currentState", currentState);
        data.put("reason", reason);

        logBusinessEvent("circuit_breaker_state_change", data, context);
    }

    // ========================================
    // 性能日志方法 - v2.16.5 委托Builder
    // ========================================

    @Override
    public void logPerformance(final String operation, final long duration,
                              final Map<String, Object> metrics, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }

        try {
            setMDC(context);

            // 使用DTO版本 - v2.16.5
            PerformanceFields fields = performanceLogBuilder.buildPerformance(operation, duration, metrics);
            StructuredLogEntry logEntry = createStructuredLogEntry(LogType.PERFORMANCE, context);
            logEntry.setFields(fields);
            logEntry.setMessage(performanceLogBuilder.buildPerformanceMessage(operation, duration));

            logStructuredLogEntry(logEntry);

        } catch (Exception e) {
            log.debug("记录性能日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    @Override
    public void logSlowQuery(final String operation, final long duration,
                            final long threshold, final TracingContext context) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("threshold", threshold);
        metrics.put("slowQueryDetected", true);

        logPerformance(operation, duration, metrics, context);
    }

    // ========================================
    // 安全事件日志方法 - v2.16.5 委托Builder
    // ========================================

    @Override
    public void logSecurityEvent(final String event, final String user,
                                final String ip, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }

        try {
            setMDC(context);

            // 使用DTO版本 - v2.16.5
            SecurityEventFields fields = securityEventLogBuilder.buildSecurityEvent(event, user, ip);
            StructuredLogEntry logEntry = createStructuredLogEntry(LogType.SECURITY, context);
            logEntry.setFields(fields);
            logEntry.setMessage(securityEventLogBuilder.buildSecurityEventMessage(event, user, ip));
            logEntry.setLevel(securityEventLogBuilder.getSecurityLogLevel(event));

            logStructuredLogEntry(logEntry);

        } catch (Exception e) {
            log.debug("记录安全事件日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    public void logAuthenticationEvent(final boolean success, final String authMethod,
                                       final String user, final String ip, final TracingContext context) {
        String event = success ? "authentication_success" : "authentication_failure";
        Map<String, Object> data = new HashMap<>();
        data.put("authMethod", authMethod);
        data.put("success", success);

        logSecurityEvent(event, user, ip, context);
    }

    @Override
    public void logSanitization(final String field, final String action,
                               final String ruleId, final TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("field", field);
        data.put("action", action);
        data.put("ruleId", ruleId);

        logBusinessEvent("data_sanitization", data, context);
    }

    @Override
    public void logConfigurationChange(final String configType, final String action,
                                       final Map<String, Object> details, final TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("configType", configType);
        data.put("action", action);
        if (details != null) {
            data.put("details", details);
        }

        logBusinessEvent("configuration_change", data, context);
    }

    // ========================================
    // 系统事件日志方法
    // ========================================

    @Override
    public void logSystemEvent(final String event, final String level,
                              final Map<String, Object> details, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }

        try {
            setMDC(context);

            Map<String, Object> logEntry = createBaseLogEntry(LogType.SYSTEM.getValue(), context);
            Map<String, Object> fields = new HashMap<>();

            fields.put("event", event);
            if (details != null && !details.isEmpty()) {
                fields.put("details", details);
            }

            logEntry.put("fields", fields);
            logEntry.put("message", String.format("系统事件: %s", event));
            logEntry.put("level", level != null ? level : "INFO");

            logStructuredEntry(logEntry);

        } catch (Exception e) {
            log.debug("记录系统事件日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    // ========================================
    // 私有辅助方法 - 基础构建
    // ========================================

    /**
     * 创建基础日志条目（Map版本，用于向后兼容）
     */
    private Map<String, Object> createBaseLogEntry(final String type, final TracingContext context) {
        Map<String, Object> logEntry = new HashMap<>();

        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("type", type);
        logEntry.put("serviceName", getServiceName());
        logEntry.put("serviceVersion", getServiceVersion());
        logEntry.put("environment", getEnvironment());

        if (context != null) {
            logEntry.put("traceId", context.getTraceId());
            logEntry.put("spanId", context.getSpanId());
        }

        return logEntry;
    }

    /**
     * 创建结构化日志条目（DTO版本） - v2.16.5
     */
    private StructuredLogEntry createStructuredLogEntry(final LogType type, final TracingContext context) {
        return StructuredLogEntry.builder()
                .timestamp(Instant.now())
                .type(type)
                .serviceName(getServiceName())
                .serviceVersion(getServiceVersion())
                .environment(getEnvironment())
                .traceId(context != null ? context.getTraceId() : null)
                .spanId(context != null ? context.getSpanId() : null)
                .build();
    }

    /**
     * 设置MDC上下文
     */
    private void setMDC(final TracingContext context) {
        if (context != null && tracingConfiguration.getLogging().isIncludeTraceId()) {
            tracingMDCManager.setMDC(context);
        }
    }

    /**
     * 清理MDC上下文
     */
    private void clearMDC() {
        tracingMDCManager.clearMDC();
    }

    // ========================================
    // 私有辅助方法 - 日志输出
    // ========================================

    /**
     * 输出结构化日志条目（Map版本）
     */
    private void logStructuredEntry(final Map<String, Object> logEntry) {
        try {
            String jsonLog = objectMapper.writeValueAsString(logEntry);

            // 根据日志级别输出
            String level = (String) logEntry.get("level");
            if ("ERROR".equals(level)) {
                log.error(jsonLog);
            } else if ("WARN".equals(level)) {
                log.warn(jsonLog);
            } else if ("DEBUG".equals(level)) {
                log.debug(jsonLog);
            } else {
                log.info(jsonLog);
            }

        } catch (JsonProcessingException e) {
            log.debug("序列化日志条目失败", e);
        }
    }

    /**
     * 输出结构化日志条目（DTO版本） - v2.16.5
     */
    private void logStructuredLogEntry(final StructuredLogEntry logEntry) {
        try {
            String jsonLog = objectMapper.writeValueAsString(logEntry);

            // 根据日志级别输出
            String level = logEntry.getLevel();
            if ("ERROR".equals(level)) {
                log.error(jsonLog);
            } else if ("WARN".equals(level)) {
                log.warn(jsonLog);
            } else if ("DEBUG".equals(level)) {
                log.debug(jsonLog);
            } else {
                log.info(jsonLog);
            }

        } catch (JsonProcessingException e) {
            log.debug("序列化日志条目失败", e);
        }
    }

    // ========================================
    // 私有辅助方法 - 配置获取
    // ========================================

    /**
     * 检查是否启用日志记录
     */
    private boolean isLoggingEnabled() {
        return tracingConfiguration.getLogging().isStructuredLogging();
    }

    /**
     * 获取服务名称
     */
    private String getServiceName() {
        return tracingConfiguration.getServiceName();
    }

    /**
     * 获取服务版本
     */
    private String getServiceVersion() {
        return tracingConfiguration.getServiceVersion();
    }

    /**
     * 获取环境名称
     */
    private String getEnvironment() {
        return tracingConfiguration.getServiceNamespace();
    }

    // ========================================
    // 私有辅助方法 - 错误处理
    // ========================================

    /**
     * 获取异常堆栈信息（截断处理）
     */
    private String getStackTrace(final Throwable error) {
        if (error == null) {
            return null;
        }

        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            error.printStackTrace(pw);
            String stackTrace = sw.toString();

            // 截断过长的堆栈信息
            int maxLength = 2000;
            if (stackTrace.length() > maxLength) {
                stackTrace = stackTrace.substring(0, maxLength) + "... (truncated)";
            }

            return stackTrace;
        } catch (Exception e) {
            return error.toString();
        }
    }
}
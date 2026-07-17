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
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.logger.builder.BackendCallLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.BusinessEventLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.ErrorLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.PerformanceLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.RequestLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.ResponseLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.SecurityEventLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.builder.SystemEventLogBuilder;
import org.unreal.modelrouter.monitor.tracing.logger.dto.BackendCallFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.BusinessEventFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.ErrorLogFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.LogType;
import org.unreal.modelrouter.monitor.tracing.logger.dto.PerformanceFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.ResponseLogFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.SecurityEventFields;
import org.unreal.modelrouter.monitor.tracing.logger.dto.StructuredLogEntry;
import org.unreal.modelrouter.monitor.tracing.logger.dto.SystemEventFields;
import org.unreal.modelrouter.monitor.tracing.sanitization.TracingSanitizationService;
import org.unreal.modelrouter.monitor.tracing.security.TracingSecurityManager;

import java.time.Instant;
import java.util.Map;

/**
 * 默认结构化日志记录器实现 - v2.16.6重构
 *
 * 实现结构化的JSON格式日志输出：
 * - 自动集成Logback MDC，添加traceId和spanId
 * - JSON格式的日志输出
 * - 敏感数据脱敏处理
 * - 全部委托Builder模式构建
 *
 * 重构历史：
 * - v2.16.4: 提取RequestLogBuilder和ResponseLogBuilder
 * - v2.16.5: 使用DTO替代Map<String, Object>弱约束
 * - v2.16.6: 提取BackendCallLogBuilder、ErrorLogBuilder、SystemEventLogBuilder，删除Map版本方法
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultStructuredLogger implements StructuredLogger {

    private final ObjectMapper objectMapper;
    private final TracingConfiguration tracingConfiguration;
    private final TracingMDCManager tracingMDCManager;
    private final TracingSanitizationService tracingSanitizationService;
    private final TracingSecurityManager tracingSecurityManager;

    // Builder组件 - v2.16.4~v2.16.6 委托调用
    private final SecurityEventLogBuilder securityEventLogBuilder;
    private final PerformanceLogBuilder performanceLogBuilder;
    private final BusinessEventLogBuilder businessEventLogBuilder;
    private final RequestLogBuilder requestLogBuilder;
    private final ResponseLogBuilder responseLogBuilder;
    private final BackendCallLogBuilder backendCallLogBuilder;
    private final ErrorLogBuilder errorLogBuilder;
    private final SystemEventLogBuilder systemEventLogBuilder;

    // ========================================
    // 请求/响应日志方法
    // ========================================

    @Override
    public void logRequest(final ServerHttpRequest request, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        tracingSecurityManager.canAccessTraceContext(context)
                .filter(Boolean::booleanValue)
                .flatMap(hasPermission -> requestLogBuilder.buildRequestFields(request, context))
                .doOnNext(fields -> executeWithMDC(context, () -> {
                    StructuredLogEntry entry = createLogEntry(LogType.REQUEST, context);
                    entry.setFields(fields);
                    entry.setMessage(requestLogBuilder.buildRequestMessage());
                    logEntry(entry);
                }))
                .subscribe();
    }

    @Override
    public void logResponse(final ServerHttpResponse response, final TracingContext context, final long duration) {
        if (!isLoggingEnabled()) {
            return;
        }
        executeWithMDC(context, () -> {
            ResponseLogFields fields = responseLogBuilder.buildResponseFields(response, duration);
            StructuredLogEntry entry = createLogEntry(LogType.RESPONSE, context);
            entry.setFields(fields);
            entry.setMessage(responseLogBuilder.buildResponseMessage(duration));
            logEntry(entry);
        });
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
        executeWithMDC(context, () -> {
            BackendCallFields fields = backendCallLogBuilder.buildBackendCallDetails(
                    adapter, instance, url, method, duration, statusCode, success);
            StructuredLogEntry entry = createLogEntry(LogType.BACKEND_CALL, context);
            entry.setFields(fields);
            entry.setMessage(backendCallLogBuilder.buildBackendCallMessage(adapter, instance, duration, success));
            logEntry(entry);
        });
    }

    // ========================================
    // 错误日志方法
    // ========================================

    @Override
    public void logError(final Throwable error, final TracingContext context) {
        logError(error, context, null);
    }

    @Override
    public void logError(final Throwable error, final TracingContext context, final Map<String, Object> additionalInfo) {
        if (!isLoggingEnabled() || error == null) {
            return;
        }
        executeWithMDC(context, () -> {
            ErrorLogFields fields = errorLogBuilder.buildErrorWithInfo(error, additionalInfo);
            StructuredLogEntry entry = createLogEntry(LogType.ERROR, context);
            entry.setFields(fields);
            entry.setMessage(errorLogBuilder.buildErrorMessage(error));
            entry.setLevel("ERROR");
            logEntry(entry);
        });
    }

    // ========================================
    // 业务事件日志方法
    // ========================================

    @Override
    public void logBusinessEvent(final String event, final Map<String, Object> data, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        executeWithMDC(context, () -> {
            BusinessEventFields fields = businessEventLogBuilder.buildBusinessEvent(event, data);
            StructuredLogEntry entry = createLogEntry(LogType.BUSINESS_EVENT, context);
            entry.setFields(fields);
            entry.setMessage(businessEventLogBuilder.buildBusinessEventMessage(event));
            logEntry(entry);
        });
    }

    @Override
    public void logLoadBalancerDecision(final String strategy, final String selectedInstance,
                                        final int availableInstances, final TracingContext context) {
        logBusinessEvent("load_balancer_decision",
                Map.of("strategy", strategy, "selectedInstance", selectedInstance, "availableInstances", availableInstances),
                context);
    }

    @Override
    public void logRateLimitCheck(final String algorithm, final boolean allowed,
                                 final long remainingTokens, final TracingContext context) {
        logBusinessEvent("rate_limit_check",
                Map.of("algorithm", algorithm, "allowed", allowed, "remainingTokens", remainingTokens),
                context);
    }

    @Override
    public void logCircuitBreakerStateChange(final String previousState, final String currentState,
                                             final String reason, final TracingContext context) {
        logBusinessEvent("circuit_breaker_state_change",
                Map.of("previousState", previousState, "currentState", currentState, "reason", reason),
                context);
    }

    // ========================================
    // 性能日志方法
    // ========================================

    @Override
    public void logPerformance(final String operation, final long duration,
                              final Map<String, Object> metrics, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        executeWithMDC(context, () -> {
            PerformanceFields fields = performanceLogBuilder.buildPerformance(operation, duration, metrics);
            StructuredLogEntry entry = createLogEntry(LogType.PERFORMANCE, context);
            entry.setFields(fields);
            entry.setMessage(performanceLogBuilder.buildPerformanceMessage(operation, duration));
            logEntry(entry);
        });
    }

    @Override
    public void logSlowQuery(final String operation, final long duration, final long threshold, final TracingContext context) {
        logPerformance(operation, duration, Map.of("threshold", threshold, "slowQueryDetected", true), context);
    }

    // ========================================
    // 安全事件日志方法
    // ========================================

    @Override
    public void logSecurityEvent(final String event, final String user, final String ip, final TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        executeWithMDC(context, () -> {
            SecurityEventFields fields = securityEventLogBuilder.buildSecurityEvent(event, user, ip);
            StructuredLogEntry entry = createLogEntry(LogType.SECURITY, context);
            entry.setFields(fields);
            entry.setMessage(securityEventLogBuilder.buildSecurityEventMessage(event, user, ip));
            entry.setLevel(securityEventLogBuilder.getSecurityLogLevel(event));
            logEntry(entry);
        });
    }

    public void logAuthenticationEvent(final boolean success, final String authMethod,
                                       final String user, final String ip, final TracingContext context) {
        logSecurityEvent(success ? "authentication_success" : "authentication_failure", user, ip, context);
    }

    @Override
    public void logSanitization(final String field, final String action, final String ruleId, final TracingContext context) {
        logBusinessEvent("data_sanitization", Map.of("field", field, "action", action, "ruleId", ruleId), context);
    }

    @Override
    public void logConfigurationChange(final String configType, final String action,
                                       final Map<String, Object> details, final TracingContext context) {
        Map<String, Object> data = details != null
                ? Map.of("configType", configType, "action", action, "details", details)
                : Map.of("configType", configType, "action", action);
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
        executeWithMDC(context, () -> {
            SystemEventFields fields = systemEventLogBuilder.buildSystemEvent(event, details);
            StructuredLogEntry entry = createLogEntry(LogType.SYSTEM, context);
            entry.setFields(fields);
            entry.setMessage(systemEventLogBuilder.buildSystemEventMessage(event));
            entry.setLevel(systemEventLogBuilder.getSystemEventLogLevel(level));
            logEntry(entry);
        });
    }

    // ========================================
    // 私有辅助方法 - MDC管理
    // ========================================

    /**
     * 在MDC上下文中执行操作
     */
    private void executeWithMDC(final TracingContext context, final Runnable action) {
        try {
            setMDC(context);
            action.run();
        } catch (Exception e) {
            log.debug("记录日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }

    private void setMDC(final TracingContext context) {
        if (context != null && tracingConfiguration.getLogging().isIncludeTraceId()) {
            tracingMDCManager.setMDC(context);
        }
    }

    private void clearMDC() {
        tracingMDCManager.clearMDC();
    }

    // ========================================
    // 私有辅助方法 - 日志构建与输出
    // ========================================

    private StructuredLogEntry createLogEntry(final LogType type, final TracingContext context) {
        return StructuredLogEntry.builder()
                .timestamp(Instant.now())
                .type(type)
                .serviceName(tracingConfiguration.getServiceName())
                .serviceVersion(tracingConfiguration.getServiceVersion())
                .environment(tracingConfiguration.getServiceNamespace())
                .traceId(context != null ? context.getTraceId() : null)
                .spanId(context != null ? context.getSpanId() : null)
                .build();
    }

    private void logEntry(final StructuredLogEntry entry) {
        try {
            String jsonLog = objectMapper.writeValueAsString(entry);
            String level = entry.getLevel();
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

    private boolean isLoggingEnabled() {
        return tracingConfiguration.getLogging().isStructuredLogging();
    }
}
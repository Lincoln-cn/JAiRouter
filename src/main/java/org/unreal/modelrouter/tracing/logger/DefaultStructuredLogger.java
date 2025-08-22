package org.unreal.modelrouter.tracing.logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.sanitization.SanitizationService;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认结构化日志记录器实现
 * 
 * 实现结构化的JSON格式日志输出，包括：
 * - 自动集成Logback MDC，添加traceId和spanId
 * - JSON格式的日志输出
 * - 敏感数据脱敏处理
 * - 异步日志处理
 * - 性能优化的日志缓存
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
    private final TracingMDCManager tracingMDCManager; // 添加TracingMDCManager依赖
    
    // 日志类型常量
    private static final String LOG_TYPE_REQUEST = "request";
    private static final String LOG_TYPE_RESPONSE = "response";
    private static final String LOG_TYPE_BACKEND_CALL = "backend_call";
    private static final String LOG_TYPE_ERROR = "error";
    private static final String LOG_TYPE_BUSINESS_EVENT = "business_event";
    private static final String LOG_TYPE_PERFORMANCE = "performance";
    private static final String LOG_TYPE_SECURITY = "security";
    private static final String LOG_TYPE_SYSTEM = "system";
    
    // 缓存常用的日志字段，避免重复创建
    private final Map<String, Object> commonFields = new ConcurrentHashMap<>();
    
    @Override
    public void logRequest(ServerHttpRequest request, TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_REQUEST, context);
            Map<String, Object> fields = new HashMap<>();
            
            // HTTP请求信息
            fields.put("method", request.getMethod() != null ? request.getMethod().name() : "UNKNOWN");
            fields.put("path", request.getPath().value());
            fields.put("url", request.getURI().toString());
            
            // 客户端信息
            String clientIp = getClientIp(request);
            if (clientIp != null) {
                fields.put("clientIp", clientIp);
            }
            
            String userAgent = request.getHeaders().getFirst("User-Agent");
            if (userAgent != null) {
                fields.put("userAgent", sanitizeIfNeeded(userAgent));
            }
            
            // 请求大小
            String contentLength = request.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                try {
                    fields.put("requestSize", Long.parseLong(contentLength));
                } catch (NumberFormatException e) {
                    // 忽略无效的Content-Length
                }
            }
            
            // 请求头（脱敏处理）
            if (tracingConfiguration.getLogging().isCaptureHeaders()) {
                Map<String, String> sanitizedHeaders = sanitizeHeaders(request.getHeaders().toSingleValueMap());
                fields.put("headers", sanitizedHeaders);
            }
            
            logEntry.put("fields", fields);
            logEntry.put("message", "HTTP请求开始");
            
            logStructuredEntry(logEntry);
            
        } catch (Exception e) {
            log.debug("记录请求日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }
    
    @Override
    public void logResponse(ServerHttpResponse response, TracingContext context, long duration) {
        if (!isLoggingEnabled()) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_RESPONSE, context);
            Map<String, Object> fields = new HashMap<>();
            
            // 响应信息
            if (response.getStatusCode() != null) {
                fields.put("statusCode", response.getStatusCode().value());
                // HttpStatusCode接口没有getReasonPhrase方法，需要转换为HttpStatus
                if (response.getStatusCode() instanceof org.springframework.http.HttpStatus) {
                    org.springframework.http.HttpStatus httpStatus = (org.springframework.http.HttpStatus) response.getStatusCode();
                    fields.put("statusText", httpStatus.getReasonPhrase());
                }
            }
            
            // 响应大小
            long contentLength = response.getHeaders().getContentLength();
            if (contentLength > 0) {
                fields.put("responseSize", contentLength);
            }
            
            // 处理时长
            fields.put("duration", duration);
            
            // 响应头（脱敏处理）
            if (tracingConfiguration.getLogging().isCaptureHeaders()) {
                Map<String, String> sanitizedHeaders = sanitizeHeaders(response.getHeaders().toSingleValueMap());
                fields.put("headers", sanitizedHeaders);
            }
            
            logEntry.put("fields", fields);
            logEntry.put("message", String.format("HTTP请求完成，耗时: %dms", duration));
            
            logStructuredEntry(logEntry);
            
        } catch (Exception e) {
            log.debug("记录响应日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }
    
    @Override
    public void logBackendCall(String adapter, String instance, long duration, boolean success, TracingContext context) {
        logBackendCallDetails(adapter, instance, null, null, duration, success ? 200 : 500, success, context);
    }
    
    @Override
    public void logBackendCallDetails(String adapter, String instance, String url, String method, 
                                     long duration, int statusCode, boolean success, TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_BACKEND_CALL, context);
            Map<String, Object> fields = new HashMap<>();
            
            fields.put("adapter", adapter);
            fields.put("instance", instance);
            fields.put("duration", duration);
            fields.put("success", success);
            fields.put("statusCode", statusCode);
            
            if (url != null) {
                fields.put("url", sanitizeIfNeeded(url));
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
    
    @Override
    public void logError(Throwable error, TracingContext context) {
        logError(error, context, null);
    }
    
    @Override
    public void logError(Throwable error, TracingContext context, Map<String, Object> additionalInfo) {
        if (!isLoggingEnabled() || error == null) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_ERROR, context);
            Map<String, Object> fields = new HashMap<>();
            
            fields.put("errorType", error.getClass().getSimpleName());
            fields.put("errorMessage", error.getMessage());
            
            // 堆栈信息（截断处理）
            String stackTrace = getStackTrace(error);
            if (stackTrace != null) {
                fields.put("stackTrace", stackTrace);
            }
            
            // 额外信息
            if (additionalInfo != null && !additionalInfo.isEmpty()) {
                fields.put("additionalInfo", sanitizeMap(additionalInfo));
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
    
    @Override
    public void logBusinessEvent(String event, Map<String, Object> data, TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_BUSINESS_EVENT, context);
            Map<String, Object> fields = new HashMap<>();
            
            fields.put("event", event);
            if (data != null && !data.isEmpty()) {
                fields.put("data", sanitizeMap(data));
            }
            
            logEntry.put("fields", fields);
            logEntry.put("message", String.format("业务事件: %s", event));
            
            logStructuredEntry(logEntry);
            
        } catch (Exception e) {
            log.debug("记录业务事件日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }
    
    @Override
    public void logLoadBalancerDecision(String strategy, String selectedInstance, int availableInstances, TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("strategy", strategy);
        data.put("selectedInstance", selectedInstance);
        data.put("availableInstances", availableInstances);
        
        logBusinessEvent("load_balancer_decision", data, context);
    }
    
    @Override
    public void logRateLimitCheck(String algorithm, boolean allowed, long remainingTokens, TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", algorithm);
        data.put("allowed", allowed);
        data.put("remainingTokens", remainingTokens);
        
        logBusinessEvent("rate_limit_check", data, context);
    }
    
    @Override
    public void logCircuitBreakerStateChange(String previousState, String currentState, String reason, TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("previousState", previousState);
        data.put("currentState", currentState);
        data.put("reason", reason);
        
        logBusinessEvent("circuit_breaker_state_change", data, context);
    }
    
    @Override
    public void logPerformance(String operation, long duration, Map<String, Object> metrics, TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_PERFORMANCE, context);
            Map<String, Object> fields = new HashMap<>();
            
            fields.put("operation", operation);
            fields.put("duration", duration);
            
            if (metrics != null && !metrics.isEmpty()) {
                fields.put("metrics", metrics);
            }
            
            logEntry.put("fields", fields);
            logEntry.put("message", String.format("性能指标: %s，耗时: %dms", operation, duration));
            
            logStructuredEntry(logEntry);
            
        } catch (Exception e) {
            log.debug("记录性能日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }
    
    @Override
    public void logSlowQuery(String operation, long duration, long threshold, TracingContext context) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("threshold", threshold);
        metrics.put("slowQueryDetected", true);
        
        logPerformance(operation, duration, metrics, context);
    }
    
    @Override
    public void logSecurityEvent(String event, String user, String ip, TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_SECURITY, context);
            Map<String, Object> fields = new HashMap<>();
            
            fields.put("event", event);
            fields.put("user", sanitizeIfNeeded(user));
            fields.put("ip", ip);
            
            logEntry.put("fields", fields);
            logEntry.put("message", String.format("安全事件: %s，用户: %s，IP: %s", event, user, ip));
            logEntry.put("level", "WARN");
            
            logStructuredEntry(logEntry);
            
        } catch (Exception e) {
            log.debug("记录安全事件日志时发生错误", e);
        } finally {
            clearMDC();
        }
    }
    
    @Override
    public void logAuthenticationEvent(boolean success, String authMethod, String user, String ip, TracingContext context) {
        String event = success ? "authentication_success" : "authentication_failure";
        Map<String, Object> data = new HashMap<>();
        data.put("authMethod", authMethod);
        data.put("success", success);
        
        logSecurityEvent(event, user, ip, context);
    }
    
    @Override
    public void logSanitization(String field, String action, String ruleId, TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("field", field);
        data.put("action", action);
        data.put("ruleId", ruleId);
        
        logBusinessEvent("data_sanitization", data, context);
    }
    
    @Override
    public void logConfigurationChange(String configType, String action, Map<String, Object> details, TracingContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("configType", configType);
        data.put("action", action);
        if (details != null) {
            data.put("details", sanitizeMap(details));
        }
        
        logBusinessEvent("configuration_change", data, context);
    }
    
    @Override
    public void logSystemEvent(String event, String level, Map<String, Object> details, TracingContext context) {
        if (!isLoggingEnabled()) {
            return;
        }
        
        try {
            // 设置MDC
            setMDC(context);
            
            Map<String, Object> logEntry = createBaseLogEntry(LOG_TYPE_SYSTEM, context);
            Map<String, Object> fields = new HashMap<>();
            
            fields.put("event", event);
            if (details != null && !details.isEmpty()) {
                fields.put("details", sanitizeMap(details));
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
    // 私有辅助方法
    // ========================================
    
    /**
     * 创建基础日志条目
     */
    private Map<String, Object> createBaseLogEntry(String type, TracingContext context) {
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
     * 设置MDC上下文
     */
    private void setMDC(TracingContext context) {
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
    
    /**
     * 输出结构化日志条目
     */
    private void logStructuredEntry(Map<String, Object> logEntry) {
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
     * 脱敏处理字符串
     */
    private String sanitizeIfNeeded(String value) {
        if (value == null || !tracingConfiguration.getLogging().isSanitizeEnabled()) {
            return value;
        }
        
        try {
            // 使用脱敏服务进行异步脱敏，这里使用同步方式获取结果
            return sanitizationService.sanitizeRequest(value, "text/plain", null)
                    .onErrorReturn(value) // 脱敏失败时返回原值
                    .block();
        } catch (Exception e) {
            log.debug("脱敏处理失败，返回原值", e);
            return value;
        }
    }
    
    /**
     * 脱敏处理Map
     */
    private Map<String, Object> sanitizeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty() || !tracingConfiguration.getLogging().isSanitizeEnabled()) {
            return map;
        }
        
        Map<String, Object> sanitizedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                sanitizedMap.put(key, sanitizeIfNeeded((String) value));
            } else {
                sanitizedMap.put(key, value);
            }
        }
        
        return sanitizedMap;
    }
    
    /**
     * 脱敏处理HTTP头部
     */
    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }
        
        Map<String, String> sanitizedHeaders = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // 敏感头部直接脱敏
            if (isSensitiveHeader(key)) {
                sanitizedHeaders.put(key, "***");
            } else {
                sanitizedHeaders.put(key, sanitizeIfNeeded(value));
            }
        }
        
        return sanitizedHeaders;
    }
    
    /**
     * 检查是否为敏感头部
     */
    private boolean isSensitiveHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        
        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization") ||
               lowerName.contains("token") ||
               lowerName.contains("key") ||
               lowerName.contains("secret") ||
               lowerName.contains("password");
    }
    
    /**
     * 获取异常堆栈信息（截断处理）
     */
    private String getStackTrace(Throwable error) {
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
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ServerHttpRequest request) {
        // 检查X-Forwarded-For头部
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 检查X-Real-IP头部
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 使用远程地址
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return null;
    }
    
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
}
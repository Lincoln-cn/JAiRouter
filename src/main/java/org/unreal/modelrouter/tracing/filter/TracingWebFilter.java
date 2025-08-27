package org.unreal.modelrouter.tracing.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.tracing.performance.TracingPerformanceMonitor;
import org.unreal.modelrouter.tracing.TracingConstants;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingService;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 追踪Web过滤器
 * 
 * 在WebFlux过滤器链的最前端拦截HTTP请求，实现：
 * - 创建根Span并设置HTTP相关属性
 * - 提取和注入追踪上下文
 * - 记录请求开始和结束时间
 * - 处理异常情况下的Span状态设置
 * - 集成结构化日志记录
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingWebFilter implements WebFilter, Ordered {
    
    private final TracingService tracingService;
    private final StructuredLogger structuredLogger;
    private final TracingPerformanceMonitor performanceMonitor;
    
    /**
     * 设置过滤器优先级为最高，确保在安全过滤器之前执行
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 检查是否需要跳过追踪
        if (shouldSkipTracing(exchange)) {
            return chain.filter(exchange);
        }
        
        long startTime = System.currentTimeMillis();
        
        return tracingService.createRootSpan(exchange)
            .flatMap(context -> {
                // 记录请求开始
                structuredLogger.logRequest(exchange.getRequest(), context);
                
                // 设置追踪上下文到exchange属性中
                exchange.getAttributes().put(TracingConstants.ContextKeys.TRACING_CONTEXT, context);
                
                // 在Reactor上下文中传播追踪信息
                Context reactorContext = Context.of(
                    TracingConstants.ContextKeys.TRACING_CONTEXT, context,
                    TracingConstants.ContextKeys.TRACE_ID, context.getTraceId(),
                    TracingConstants.ContextKeys.SPAN_ID, context.getSpanId()
                );
                
                // 继续处理链，并在完成时记录响应
                return chain.filter(exchange)
                    .contextWrite(reactorContext)
                    .doOnSuccess(v -> handleSuccess(exchange, context, startTime))
                    .doOnError(error -> handleError(exchange, context, error, startTime))
                    .doFinally(signal -> finishSpan(context, startTime));
            })
            .onErrorResume(tracingError -> {
                // 追踪系统本身出错时，不应影响主业务流程
                log.warn("追踪过滤器处理失败，继续执行主业务流程", tracingError);
                return chain.filter(exchange);
            });
    }
    
    /**
     * 检查是否应该跳过追踪
     */
    private boolean shouldSkipTracing(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        
        // 跳过健康检查和监控端点
        if (path.startsWith("/actuator/health") || 
            path.startsWith("/actuator/metrics") ||
            path.startsWith("/actuator/prometheus")) {
            return true;
        }
        
        // 跳过静态资源
        if (path.startsWith("/swagger-ui/") || 
            path.startsWith("/webjars/") ||
            path.startsWith("/v3/api-docs/")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理请求成功完成
     */
    private void handleSuccess(ServerWebExchange exchange, TracingContext context, long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            
            // 设置响应相关的Span属性
            setResponseAttributes(context, exchange, duration);
            
            // 记录响应日志
            structuredLogger.logResponse(exchange.getResponse(), context, duration);
            
            // 记录性能指标
            recordPerformanceMetrics(exchange, context, startTime, duration, true);
            
            // 检查是否为慢请求并触发优化
            if (duration > 5000) { // 超过5秒的请求
                String operationName = buildOperationName(exchange.getRequest());
                log.warn("检测到慢请求: {} 耗时 {}ms", operationName, duration);
                triggerSlowRequestOptimization(operationName, duration, context);
            }
            
            log.debug("HTTP请求处理成功，耗时: {}ms (traceId: {}, spanId: {})", 
                    duration, context.getTraceId(), context.getSpanId());
        } catch (Exception e) {
            log.debug("处理成功响应时发生错误", e);
        }
    }
    
    /**
     * 处理请求错误
     */
    private void handleError(ServerWebExchange exchange, TracingContext context, Throwable error, long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            
            // 设置错误相关的Span属性
            setErrorAttributes(context, error);
            setResponseAttributes(context, exchange, duration);
            
            // 记录错误日志
            structuredLogger.logError(error, context);
            
            // 记录错误性能指标
            recordErrorPerformanceMetrics(exchange, context, startTime, duration, error);
            
            log.debug("HTTP请求处理失败，耗时: {}ms (traceId: {}, spanId: {})", 
                    duration, context.getTraceId(), context.getSpanId(), error);
        } catch (Exception e) {
            log.debug("处理错误响应时发生错误", e);
        }
    }
    
    /**
     * 完成Span
     */
    private void finishSpan(TracingContext context, long startTime) {
        try {
            if (context != null && context.isActive()) {
                Span currentSpan = context.getCurrentSpan();
                if (currentSpan != null) {
                    // 设置总耗时
                    long totalDuration = System.currentTimeMillis() - startTime;
                    currentSpan.setAttribute(TracingConstants.HttpAttributes.RESPONSE_TIME, totalDuration);
                    
                    // 完成Span
                    context.finishSpan(currentSpan);
                }
            }
        } catch (Exception e) {
            log.debug("完成Span时发生错误", e);
        }
    }
    
    /**
     * 设置响应相关属性
     */
    private void setResponseAttributes(TracingContext context, ServerWebExchange exchange, long duration) {
        if (context == null || !context.isActive()) {
            return;
        }
        
        try {
            Span currentSpan = context.getCurrentSpan();
            ServerHttpResponse response = exchange.getResponse();
            
            // 响应状态码
            org.springframework.http.HttpStatusCode statusCode = response.getStatusCode();
            if (statusCode != null) {
                currentSpan.setAttribute(TracingConstants.HttpAttributes.STATUS_CODE, statusCode.value());
                
                // 根据状态码设置Span状态
                if (statusCode.is4xxClientError() || statusCode.is5xxServerError()) {
                    currentSpan.setStatus(StatusCode.ERROR, "HTTP " + statusCode.value());
                } else {
                    currentSpan.setStatus(StatusCode.OK);
                }
            }
            
            // 响应大小
            long contentLength = response.getHeaders().getContentLength();
            if (contentLength > 0) {
                currentSpan.setAttribute(TracingConstants.HttpAttributes.RESPONSE_SIZE, contentLength);
            }
            
            // 响应时间
            currentSpan.setAttribute(TracingConstants.HttpAttributes.RESPONSE_TIME, duration);
            
        } catch (Exception e) {
            log.debug("设置响应属性时发生错误", e);
        }
    }
    
    /**
     * 设置错误相关属性
     */
    private void setErrorAttributes(TracingContext context, Throwable error) {
        if (context == null || !context.isActive() || error == null) {
            return;
        }
        
        try {
            Span currentSpan = context.getCurrentSpan();
            
            // 设置错误状态
            currentSpan.setStatus(StatusCode.ERROR, error.getMessage());
            
            // 设置错误属性
            currentSpan.setAttribute(TracingConstants.ErrorAttributes.ERROR, true);
            currentSpan.setAttribute(TracingConstants.ErrorAttributes.ERROR_TYPE, error.getClass().getSimpleName());
            currentSpan.setAttribute(TracingConstants.ErrorAttributes.ERROR_MESSAGE, error.getMessage());
            
            // 添加错误事件
            context.addEvent(TracingConstants.Events.ERROR_OCCURRED, 
                java.util.Map.of(
                    "error.type", error.getClass().getSimpleName(),
                    "error.message", error.getMessage() != null ? error.getMessage() : "",
                    "timestamp", Instant.now().toString()
                ));
            
        } catch (Exception e) {
            log.debug("设置错误属性时发生错误", e);
        }
    }
    
    /**
     * 构建操作名称
     */
    private String buildOperationName(ServerHttpRequest request) {
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        
        // 简化路径，移除查询参数
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        
        return method + " " + path;
    }
    
    /**
     * 记录性能指标
     */
    private void recordPerformanceMetrics(ServerWebExchange exchange, TracingContext context, 
                                         long startTime, long duration, boolean success) {
        try {
            String operationName = buildOperationName(exchange.getRequest());
            Map<String, Object> metadata = createPerformanceMetadata(exchange, context, success);
            
            performanceMonitor.recordOperationPerformance(
                operationName,
                startTime,
                System.currentTimeMillis(),
                success,
                metadata
            ).subscribe(
                null,
                error -> log.debug("记录性能指标失败", error)
            );
        } catch (Exception e) {
            log.debug("记录性能指标异常", e);
        }
    }
    
    /**
     * 记录错误性能指标
     */
    private void recordErrorPerformanceMetrics(ServerWebExchange exchange, TracingContext context,
                                              long startTime, long duration, Throwable error) {
        try {
            String operationName = buildOperationName(exchange.getRequest());
            Map<String, Object> metadata = createErrorMetadata(exchange, context, error);
            
            performanceMonitor.recordOperationPerformance(
                "error." + operationName,
                startTime,
                System.currentTimeMillis(),
                false,
                metadata
            ).subscribe(
                null,
                perfError -> log.debug("记录错误性能指标失败", perfError)
            );
        } catch (Exception e) {
            log.debug("记录错误性能指标异常", e);
        }
    }
    
    /**
     * 创建性能元数据
     */
    private Map<String, Object> createPerformanceMetadata(ServerWebExchange exchange, 
                                                          TracingContext context, boolean success) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() != null) {
            metadata.put("http.method", request.getMethod().name());
        }
        
        metadata.put("http.path", request.getPath().value());
        metadata.put("traceId", context.getTraceId());
        metadata.put("spanId", context.getSpanId());
        metadata.put("success", success);
        
        org.springframework.http.HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        if (statusCode != null) {
            metadata.put("http.status_code", statusCode.value());
        }
        
        String clientIp = getClientIp(request);
        if (clientIp != null) {
            metadata.put("client.ip", clientIp);
        }
        
        return metadata;
    }
    
    /**
     * 创建错误元数据
     */
    private Map<String, Object> createErrorMetadata(ServerWebExchange exchange, 
                                                    TracingContext context, Throwable error) {
        Map<String, Object> metadata = createPerformanceMetadata(exchange, context, false);
        
        metadata.put("error.type", error.getClass().getSimpleName());
        metadata.put("error.message", error.getMessage());
        metadata.put("error.occurred", true);
        
        return metadata;
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
     * 触发慢请求优化
     */
    private void triggerSlowRequestOptimization(String operationName, long duration, TracingContext context) {
        // 异步触发性能优化，避免影响主请求流程
        tracingService.triggerPerformanceOptimization()
            .subscribe(
                null,
                error -> log.warn("触发性能优化失败: operation={}, duration={}ms, traceId={}", 
                        operationName, duration, context.getTraceId(), error),
                () -> log.debug("为慢请求触发性能优化: operation={}, duration={}ms, traceId={}", 
                        operationName, duration, context.getTraceId())
            );
    }
}
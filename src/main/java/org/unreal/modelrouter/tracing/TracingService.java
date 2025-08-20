package org.unreal.modelrouter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 追踪服务
 * 
 * 提供追踪功能的高级API，包括：
 * - 追踪上下文的创建和管理
 * - HTTP请求的追踪处理
 * - 响应式上下文的传播
 * - 追踪生命周期管理
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TracingService {
    
    private final Tracer tracer;
    
    /**
     * 为HTTP请求创建根追踪上下文
     * 
     * @param exchange Web交换对象
     * @return 包含追踪上下文的Mono
     */
    public Mono<TracingContext> createRootSpan(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // 从请求头中提取追踪上下文
            Map<String, String> headers = extractHeaders(request);
            TracingContext context = new DefaultTracingContext(tracer);
            
            // 如果请求头中包含追踪信息，则提取上下文
            if (hasTracingHeaders(headers)) {
                context = context.extractContext(headers);
                log.debug("从请求头提取追踪上下文，traceId: {}", context.getTraceId());
            }
            
            // 创建HTTP服务器Span
            String operationName = buildOperationName(request);
            Span rootSpan = context.createSpan(operationName, SpanKind.SERVER);
            
            // 设置HTTP相关属性
            setHttpAttributes(rootSpan, request);
            
            // 设置到线程本地存储
            TracingContextHolder.setCurrentContext(context);
            
            log.debug("创建HTTP请求根Span: {} (traceId: {}, spanId: {})", 
                    operationName, context.getTraceId(), context.getSpanId());
            
            return context;
        });
    }
    
    /**
     * 创建业务操作的追踪上下文
     * 
     * @param operationName 操作名称
     * @param kind Span类型
     * @return 追踪上下文
     */
    public TracingContext createOperationSpan(String operationName, SpanKind kind) {
        TracingContext parentContext = TracingContextHolder.getCurrentContext();
        
        if (parentContext != null && parentContext.isActive()) {
            // 创建子Span
            Span childSpan = parentContext.createChildSpan(operationName, kind, parentContext.getCurrentSpan());
            TracingContext childContext = parentContext.copy();
            childContext.setCurrentSpan(childSpan);
            return childContext;
        } else {
            // 创建新的根上下文
            TracingContext context = new DefaultTracingContext(tracer);
            context.createSpan(operationName, kind);
            return context;
        }
    }
    
    /**
     * 完成HTTP请求的追踪
     * 
     * @param exchange Web交换对象
     * @param context 追踪上下文
     * @param duration 请求处理时长（毫秒）
     */
    public void finishHttpSpan(ServerWebExchange exchange, TracingContext context, long duration) {
        if (context == null || !context.isActive()) {
            return;
        }
        
        try {
            Span currentSpan = context.getCurrentSpan();
            
            // 设置响应相关属性
            setResponseAttributes(currentSpan, exchange, duration);
            
            // 完成Span
            context.finishSpan(currentSpan);
            
            log.debug("完成HTTP请求追踪，耗时: {}ms (traceId: {}, spanId: {})", 
                    duration, context.getTraceId(), context.getSpanId());
        } catch (Exception e) {
            log.warn("完成HTTP请求追踪时发生错误", e);
        } finally {
            // 清理线程本地存储
            TracingContextHolder.clearCurrentContext();
        }
    }
    
    /**
     * 记录错误到追踪上下文
     * 
     * @param context 追踪上下文
     * @param error 错误信息
     */
    public void recordError(TracingContext context, Throwable error) {
        if (context != null && context.isActive()) {
            Span currentSpan = context.getCurrentSpan();
            context.finishSpan(currentSpan, error);
            log.debug("记录错误到追踪上下文: {} (traceId: {}, spanId: {})", 
                    error.getMessage(), context.getTraceId(), context.getSpanId());
        }
    }
    
    /**
     * 从请求中提取头部信息
     */
    private Map<String, String> extractHeaders(ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        // 提取W3C Trace Context相关头部
        String traceparent = request.getHeaders().getFirst("traceparent");
        if (traceparent != null) {
            headers.put("traceparent", traceparent);
        }
        
        String tracestate = request.getHeaders().getFirst("tracestate");
        if (tracestate != null) {
            headers.put("tracestate", tracestate);
        }
        
        // 提取其他可能的追踪头部
        String xTraceId = request.getHeaders().getFirst("X-Trace-Id");
        if (xTraceId != null) {
            headers.put("X-Trace-Id", xTraceId);
        }
        
        return headers;
    }
    
    /**
     * 检查是否包含追踪头部
     */
    private boolean hasTracingHeaders(Map<String, String> headers) {
        return headers.containsKey("traceparent") || 
               headers.containsKey("X-Trace-Id");
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
     * 设置HTTP请求相关属性
     */
    private void setHttpAttributes(Span span, ServerHttpRequest request) {
        try {
            // HTTP方法
            if (request.getMethod() != null) {
                span.setAttribute("http.method", request.getMethod().name());
            }
            
            // URL相关
            span.setAttribute("http.url", request.getURI().toString());
            span.setAttribute("http.scheme", request.getURI().getScheme());
            span.setAttribute("http.host", request.getURI().getHost());
            span.setAttribute("http.target", request.getPath().value());
            
            // 客户端信息
            String clientIp = getClientIp(request);
            if (clientIp != null) {
                span.setAttribute("http.client_ip", clientIp);
            }
            
            String userAgent = request.getHeaders().getFirst("User-Agent");
            if (userAgent != null) {
                span.setAttribute("http.user_agent", userAgent);
            }
            
            // 请求大小
            String contentLength = request.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                try {
                    span.setAttribute("http.request_content_length", Long.parseLong(contentLength));
                } catch (NumberFormatException e) {
                    // 忽略无效的Content-Length
                }
            }
        } catch (Exception e) {
            log.debug("设置HTTP请求属性时发生错误", e);
        }
    }
    
    /**
     * 设置HTTP响应相关属性
     */
    private void setResponseAttributes(Span span, ServerWebExchange exchange, long duration) {
        try {
            // 响应状态码
            if (exchange.getResponse().getStatusCode() != null) {
                span.setAttribute("http.status_code", exchange.getResponse().getStatusCode().value());
            }
            
            // 响应时间
            span.setAttribute("http.response_time_ms", duration);
            
            // 响应大小
            if (exchange.getResponse().getHeaders().getContentLength() > 0) {
                span.setAttribute("http.response_content_length", 
                        exchange.getResponse().getHeaders().getContentLength());
            }
        } catch (Exception e) {
            log.debug("设置HTTP响应属性时发生错误", e);
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ServerHttpRequest request) {
        // 检查X-Forwarded-For头部
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // 取第一个IP地址
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
}
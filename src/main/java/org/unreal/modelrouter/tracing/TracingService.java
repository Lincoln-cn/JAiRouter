package org.unreal.modelrouter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.tracing.performance.TracingPerformanceMonitor;
import org.unreal.modelrouter.tracing.query.TraceQueryService;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final AsyncTracingProcessor asyncTracingProcessor;
    private final TracingMemoryManager memoryManager;
    private final TracingPerformanceMonitor performanceMonitor;
    private final TraceQueryService traceQueryService;
    
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
            
            // 缓存追踪数据到内存管理器
            if (context.isActive()) {
                String traceId = context.getTraceId();
                String spanId = context.getSpanId();
                memoryManager.cacheTraceData(
                    traceId,
                    spanId,
                    rootSpan,
                    estimateSpanSize(rootSpan)
                ).subscribe(
                    success -> {
                        if (!success) {
                            log.warn("缓存追踪数据失败: traceId={}", traceId);
                        }
                    },
                    error -> log.error("缓存追踪数据异常", error)
                );
            }
            
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
        
        long startTime = System.currentTimeMillis();
        
        if (parentContext != null && parentContext.isActive()) {
            // 创建子Span
            Span childSpan = parentContext.createChildSpan(operationName, kind, parentContext.getCurrentSpan());
            TracingContext childContext = parentContext.copy();
            childContext.setCurrentSpan(childSpan);
            
            // 缓存子Span数据
            memoryManager.cacheTraceData(
                childContext.getTraceId(),
                childContext.getSpanId(),
                childSpan,
                estimateSpanSize(childSpan)
            ).subscribe();
            
            return childContext;
        } else {
            // 创建新的根上下文
            TracingContext context = new DefaultTracingContext(tracer);
            Span span = context.createSpan(operationName, kind);
            
            // 缓存根Span数据
            if (context.isActive()) {
                memoryManager.cacheTraceData(
                    context.getTraceId(),
                    context.getSpanId(),
                    span,
                    estimateSpanSize(span)
                ).subscribe();
            }
            
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
        
        long startTime = System.currentTimeMillis();
        boolean success = true;
        
        try {
            Span currentSpan = context.getCurrentSpan();
            
            // 判断是否成功
            if (exchange.getResponse().getStatusCode() != null) {
                success = !exchange.getResponse().getStatusCode().isError();
            }
            
            // 设置响应相关属性
            setResponseAttributes(currentSpan, exchange, duration);
            
            // 完成Span
            context.finishSpan(currentSpan);
            
            // 异步提交追踪数据
            asyncTracingProcessor.submitTraceData(
                context.getTraceId(),
                context.getSpanId(),
                buildOperationName(exchange.getRequest()),
                startTime,
                duration,
                success,
                currentSpan.getSpanContext()
            ).subscribe(
                submitted -> {
                    if (!submitted) {
                        log.warn("异步提交追踪数据失败: traceId={}", context.getTraceId());
                    }
                },
                error -> log.error("异步提交追踪数据异常", error)
            );
            
            // 记录追踪数据到查询服务
            String serviceName = "jairouter-gateway";
            List<TraceQueryService.SpanRecord> spanRecords = new ArrayList<>();
            Instant startTimeInstant = Instant.ofEpochMilli(startTime);
            Instant endTimeInstant = Instant.ofEpochMilli(startTime + duration);
            spanRecords.add(new TraceQueryService.SpanRecord(
                context.getSpanId(),
                context.getTraceId(),
                buildOperationName(exchange.getRequest()),
                startTimeInstant,
                endTimeInstant,
                duration,
                !success,
                exchange.getResponse().getStatusCode() != null ? 
                    String.valueOf(exchange.getResponse().getStatusCode().value()) : "200",
                createSpanAttributes(exchange, context)
            ));
            
            traceQueryService.recordTrace(
                context.getTraceId(),
                serviceName,
                spanRecords,
                duration
            ).subscribe(
                v -> log.debug("追踪数据已记录到查询服务: traceId={}", context.getTraceId()),
                error -> log.warn("记录追踪数据到查询服务失败", error)
            );
            
            // 记录操作性能
            performanceMonitor.recordOperationPerformance(
                "http.request",
                startTime,
                System.currentTimeMillis(),
                success,
                createMetadata(exchange, context)
            ).subscribe();
            
            log.debug("完成HTTP请求追踪，耗时: {}ms (traceId: {}, spanId: {})", 
                    duration, context.getTraceId(), context.getSpanId());
        } catch (Exception e) {
            log.warn("完成HTTP请求追踪时发生错误", e);
            success = false;
            
            // 记录失败的性能指标
            performanceMonitor.recordOperationPerformance(
                "http.request",
                startTime,
                System.currentTimeMillis(),
                false,
                createErrorMetadata(exchange, context, e)
            ).subscribe();
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
            long startTime = System.currentTimeMillis();
            Span currentSpan = context.getCurrentSpan();
            
            // 记录错误到Span
            context.finishSpan(currentSpan, error);
            
            // 异步提交错误追踪数据
            asyncTracingProcessor.submitTraceData(
                context.getTraceId(),
                context.getSpanId(),
                "error." + error.getClass().getSimpleName(),
                startTime,
                System.currentTimeMillis() - startTime,
                false,
                currentSpan.getSpanContext()
            ).subscribe();
            
            // 记录错误追踪数据到查询服务
            String serviceName = "jairouter-gateway";
            List<TraceQueryService.SpanRecord> errorSpanRecords = new ArrayList<>();
            Map<String, Object> errorAttributes = new HashMap<>();
            errorAttributes.put("error.type", error.getClass().getSimpleName());
            errorAttributes.put("error.message", error.getMessage());
            errorAttributes.put("error.occurred", true);
            
            Instant errorStartInstant = Instant.ofEpochMilli(startTime);
            Instant errorEndInstant = Instant.ofEpochMilli(System.currentTimeMillis());
            errorSpanRecords.add(new TraceQueryService.SpanRecord(
                context.getSpanId(),
                context.getTraceId(),
                "error." + error.getClass().getSimpleName(),
                errorStartInstant,
                errorEndInstant,
                System.currentTimeMillis() - startTime,
                true, // 这是错误记录
                "500", // 错误状态码
                errorAttributes
            ));
            
            traceQueryService.recordTrace(
                context.getTraceId(),
                serviceName,
                errorSpanRecords,
                System.currentTimeMillis() - startTime
            ).subscribe(
                v -> log.debug("错误追踪数据已记录到查询服务: traceId={}", context.getTraceId()),
                recordError -> log.warn("记录错误追踪数据到查询服务失败", recordError)
            );
            
            // 记录错误性能指标
            Map<String, Object> errorMetadata = new HashMap<>();
            errorMetadata.put("error.type", error.getClass().getSimpleName());
            errorMetadata.put("error.message", error.getMessage());
            errorMetadata.put("traceId", context.getTraceId());
            errorMetadata.put("spanId", context.getSpanId());
            
            performanceMonitor.recordOperationPerformance(
                "error.handling",
                startTime,
                System.currentTimeMillis(),
                false,
                errorMetadata
            ).subscribe();
            
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
            
            // 安全地获取响应大小，避免修改headers
            try {
                long contentLength = exchange.getResponse().getHeaders().getContentLength();
                if (contentLength > 0) {
                    span.setAttribute("http.response_content_length", contentLength);
                }
            } catch (Exception e) {
                log.debug("获取响应大小时发生错误", e);
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
    
    /**
     * 估算Span的内存大小
     */
    private long estimateSpanSize(Span span) {
        if (span == null || !span.getSpanContext().isValid()) {
            return 0L;
        }
        
        // 基本Span结构大小估算
        long baseSize = 256L; // 基础对象大小
        
        // TraceId + SpanId 大小
        baseSize += 32L + 16L;
        
        // 属性大小估算（简化计算）
        baseSize += 512L; // 假设平均属性大小
        
        return baseSize;
    }
    
    /**
     * 创建操作元数据
     */
    private Map<String, Object> createMetadata(ServerWebExchange exchange, TracingContext context) {
        Map<String, Object> metadata = new HashMap<>();
        
        if (exchange.getRequest().getMethod() != null) {
            metadata.put("http.method", exchange.getRequest().getMethod().name());
        }
        
        metadata.put("http.path", exchange.getRequest().getPath().value());
        metadata.put("traceId", context.getTraceId());
        metadata.put("spanId", context.getSpanId());
        
        if (exchange.getResponse().getStatusCode() != null) {
            metadata.put("http.status_code", exchange.getResponse().getStatusCode().value());
        }
        
        String clientIp = getClientIp(exchange.getRequest());
        if (clientIp != null) {
            metadata.put("client.ip", clientIp);
        }
        
        return metadata;
    }
    
    /**
     * 创建错误元数据
     */
    private Map<String, Object> createErrorMetadata(ServerWebExchange exchange, TracingContext context, Throwable error) {
        Map<String, Object> metadata = createMetadata(exchange, context);
        
        metadata.put("error.type", error.getClass().getSimpleName());
        metadata.put("error.message", error.getMessage());
        metadata.put("error.occurred", true);
        
        return metadata;
    }
    
    /**
     * 获取性能统计信息
     */
    public Mono<Map<String, Object>> getPerformanceStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            // 异步处理器统计
            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();
            stats.put("processing", Map.of(
                "processed_count", processingStats.getProcessedCount(),
                "dropped_count", processingStats.getDroppedCount(),
                "queue_size", processingStats.getQueueSize(),
                "success_rate", processingStats.getSuccessRate(),
                "is_running", processingStats.isRunning()
            ));
            
            // 内存管理器统计
            TracingMemoryManager.MemoryStats memoryStats = memoryManager.getMemoryStats();
            stats.put("memory", Map.of(
                "heap_usage_ratio", memoryStats.getHeapUsageRatio(),
                "cache_size", memoryStats.getCacheSize(),
                "cache_hit_ratio", memoryStats.getHitRatio(),
                "pressure_level", memoryStats.getPressureLevel().name()
            ));
            
            return stats;
        });
    }
    
    /**
     * 手动触发性能优化
     */
    public Mono<Void> triggerPerformanceOptimization() {
        return performanceMonitor.detectBottlenecks()
            .flatMap(bottlenecks -> {
                if (!bottlenecks.isEmpty()) {
                    log.info("检测到{}个性能瓶颈，开始优化", bottlenecks.size());
                    
                    // 触发内存清理
                    return memoryManager.performMemoryCheck()
                        .then(memoryManager.performGarbageCollection())
                        .then(asyncTracingProcessor.flush())
                        .doOnSuccess(v -> log.info("性能优化完成"))
                        .then();
                } else {
                    log.debug("未检测到性能瓶颈");
                    return Mono.empty();
                }
            });
    }
    
    /**
     * 创建Span属性映射
     */
    private Map<String, Object> createSpanAttributes(ServerWebExchange exchange, TracingContext context) {
        Map<String, Object> attributes = new HashMap<>();
        
        ServerHttpRequest request = exchange.getRequest();
        
        // HTTP相关属性
        if (request.getMethod() != null) {
            attributes.put("http.method", request.getMethod().name());
        }
        attributes.put("http.url", request.getURI().toString());
        attributes.put("http.path", request.getPath().value());
        
        if (request.getURI().getScheme() != null) {
            attributes.put("http.scheme", request.getURI().getScheme());
        }
        if (request.getURI().getHost() != null) {
            attributes.put("http.host", request.getURI().getHost());
        }
        
        // 响应状态码
        if (exchange.getResponse().getStatusCode() != null) {
            attributes.put("http.status_code", exchange.getResponse().getStatusCode().value());
        }
        
        // 客户端信息
        String clientIp = getClientIp(request);
        if (clientIp != null) {
            attributes.put("http.client_ip", clientIp);
        }
        
        String userAgent = request.getHeaders().getFirst("User-Agent");
        if (userAgent != null) {
            attributes.put("http.user_agent", userAgent);
        }
        
        // 请求大小
        String contentLength = request.getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                attributes.put("http.request_content_length", Long.parseLong(contentLength));
            } catch (NumberFormatException e) {
                // 忽略无效的Content-Length
            }
        }
        
        // 响应大小
        if (exchange.getResponse().getHeaders().getContentLength() > 0) {
            attributes.put("http.response_content_length", 
                    exchange.getResponse().getHeaders().getContentLength());
        }
        
        // 追踪信息
        attributes.put("trace.trace_id", context.getTraceId());
        attributes.put("trace.span_id", context.getSpanId());
        
        return attributes;
    }
}
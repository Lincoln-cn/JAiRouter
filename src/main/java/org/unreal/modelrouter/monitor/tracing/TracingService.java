package org.unreal.modelrouter.monitor.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.helper.ServiceNameResolver;
import org.unreal.modelrouter.monitor.tracing.helper.SpanAttributeHelper;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪服务
 *
 * 提供追踪功能的高级 API，包括：
 * - 追踪上下文的创建和管理
 * - HTTP 请求的追踪处理
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
    private final TracingConfiguration tracingConfiguration;
    private final SpanAttributeHelper spanAttributeHelper;
    private final ServiceNameResolver serviceNameResolver;

    /**
     * 为 HTTP 请求创建根追踪上下文
     *
     * @param exchange Web 交换对象
     * @return 包含追踪上下文的 Mono
     */
    public Mono<TracingContext> createRootSpan(final ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            ServerHttpRequest request = exchange.getRequest();

            // 从请求头中提取追踪上下文
            Map<String, String> headers = extractHeaders(request);
            TracingContext context;

            // 如果请求头中包含追踪信息，则提取上下文（来自上游服务的追踪）
            if (hasTracingHeaders(headers)) {
                context = new DefaultTracingContext(tracer).extractContext(headers);
                log.debug("从请求头提取追踪上下文，traceId: {}", context.getTraceId());
            } else {
                // 创建新的根追踪上下文（新的请求链路开始）
                context = new DefaultTracingContext(tracer);
                log.debug("创建新的根追踪上下文，traceId: {}", context.getTraceId());
            }

            // 创建 HTTP 服务器 Span
            String operationName = serviceNameResolver.buildOperationName(request);
            Span rootSpan = context.createSpan(operationName, SpanKind.SERVER);

            // 设置 HTTP 相关属性
            spanAttributeHelper.setHttpAttributes(rootSpan, request);

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
                    800L // 固定估算值：基础 256 + TraceId/SpanId 48 + 属性约 500
                ).subscribe(
                    success -> {
                        if (!success) {
                            log.warn("缓存追踪数据失败: traceId={}", traceId);
                        }
                    },
                    error -> log.error("缓存追踪数据异常", error)
                );
            }

            log.debug("创建 HTTP 请求根 Span: {} (traceId: {}, spanId: {})",
                    operationName, context.getTraceId(), context.getSpanId());

            return context;
        });
    }

    /**
     * 创建业务操作的追踪上下文
     *
     * @param operationName 操作名称
     * @param kind Span 类型
     * @return 追踪上下文
     */
    public TracingContext createOperationSpan(final String operationName, final SpanKind kind) {
        TracingContext parentContext = TracingContextHolder.getCurrentContext();

        long startTime = System.currentTimeMillis();

        if (parentContext != null && parentContext.isActive()) {
            // 创建子 Span
            Span childSpan = parentContext.createChildSpan(operationName, kind, parentContext.getCurrentSpan());
            TracingContext childContext = parentContext.copy();
            childContext.setCurrentSpan(childSpan);

            // 缓存子 Span 数据
            memoryManager.cacheTraceData(
                childContext.getTraceId(),
                childContext.getSpanId(),
                childSpan,
                800L // 固定估算值
            ).subscribe();

            return childContext;
        } else {
            // 创建新的根上下文
            TracingContext context = new DefaultTracingContext(tracer);
            Span span = context.createSpan(operationName, kind);

            // 缓存根 Span 数据
            if (context.isActive()) {
                memoryManager.cacheTraceData(
                    context.getTraceId(),
                    context.getSpanId(),
                    span,
                    800L // 固定估算值
                ).subscribe();
            }

            return context;
        }
    }

    /**
     * 完成 HTTP 请求的追踪
     *
     * @param exchange Web 交换对象
     * @param context 追踪上下文
     * @param duration 请求处理时长（毫秒）
     */
    public void finishHttpSpan(final ServerWebExchange exchange, final TracingContext context, final long duration) {
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
            spanAttributeHelper.setResponseAttributes(currentSpan, exchange, duration);

            // 完成 Span
            context.finishSpan(currentSpan);

            // 异步提交追踪数据
            asyncTracingProcessor.submitTraceData(
                context.getTraceId(),
                context.getSpanId(),
                serviceNameResolver.buildOperationName(exchange.getRequest()),
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
            String serviceName = serviceNameResolver.resolveServiceName(exchange.getRequest());
            List<TraceQueryService.SpanRecord> spanRecords = new ArrayList<>();
            Instant startTimeInstant = Instant.ofEpochMilli(startTime);
            Instant endTimeInstant = Instant.ofEpochMilli(startTime + duration);
            spanRecords.add(new TraceQueryService.SpanRecord(
                context.getSpanId(),
                context.getTraceId(),
                serviceNameResolver.buildOperationName(exchange.getRequest()),
                startTimeInstant,
                endTimeInstant,
                duration,
                !success,
                exchange.getResponse().getStatusCode() != null
                    ? String.valueOf(exchange.getResponse().getStatusCode().value()) : "200",
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
                createSpanAttributes(exchange, context)
            ).subscribe();

            log.debug("完成 HTTP 请求追踪，耗时: {}ms (traceId: {}, spanId: {})",
                    duration, context.getTraceId(), context.getSpanId());
        } catch (Exception e) {
            log.warn("完成 HTTP 请求追踪时发生错误", e);
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
    public void recordError(final TracingContext context, final Throwable error) {
        if (context != null && context.isActive()) {
            long startTime = System.currentTimeMillis();
            Span currentSpan = context.getCurrentSpan();

            // 记录错误到 Span
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
            // 错误记录通常发生在 API 请求处理过程中，使用 "server" 作为服务标识
            String serviceName = "server";
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
    private Map<String, String> extractHeaders(final ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();

        // 提取 W3C Trace Context 相关头部
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
    private boolean hasTracingHeaders(final Map<String, String> headers) {
        return headers.containsKey("traceparent")
               || headers.containsKey("X-Trace-Id");
    }

    /**
     * 创建错误元数据
     */
    private Map<String, Object> createErrorMetadata(final ServerWebExchange exchange, final TracingContext context, final Throwable error) {
        Map<String, Object> metadata = createSpanAttributes(exchange, context);

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
            MemoryStats memoryStats = memoryManager.getMemoryStats();
            stats.put("memory", Map.of(
                "heap_usage_ratio", memoryStats.getHeapUsageRatio(),
                "cache_size", memoryStats.getCacheSize(),
                "cache_hit_ratio", memoryStats.getHitRatio(),
                "pressure_level", memoryStats.getPressureLevel().name()
            ));

            // 生成趋势数据（模拟数据，实际项目中应该从时序数据库获取）
            stats.put("traceVolumeTrend", buildTrendData(true));
            stats.put("errorTrend", buildTrendData(false));

            return stats;
        });
    }

    /**
     * 构建趋势数据（统一处理追踪量和错误量）
     * @param isTraceVolume true=追踪量趋势，false=错误量趋势
     */
    private List<Map<String, Object>> buildTrendData(final boolean isTraceVolume) {
        List<Map<String, Object>> trend = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = 12; i >= 0; i--) {
            long timestamp = now - (i * 5 * 60 * 1000);
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", timestamp);
            point.put("value", getTrendValue(timestamp, timestamp + 5 * 60 * 1000, isTraceVolume));
            trend.add(point);
        }
        return trend;
    }

    /**
     * 获取趋势值（从 traceQueryService 直接获取）
     */
    private int getTrendValue(final long startTime, final long endTime, final boolean isTraceVolume) {
        try {
            TraceQueryService.TraceStatistics stats = traceQueryService.getTraceStatistics(startTime, endTime).block();
            if (stats == null) return 0;
            return isTraceVolume ? (int) stats.getTotalTraces() : (int) stats.getErrorTraces();
        } catch (Exception e) {
            log.debug("获取趋势数据时发生错误", e);
            return 0;
        }
    }

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
     * 创建 Span 属性映射
     */
    private Map<String, Object> createSpanAttributes(final ServerWebExchange exchange, final TracingContext context) {
        Map<String, Object> attributes = spanAttributeHelper.extractExchangeAttributes(exchange);

        // 追踪信息
        attributes.put("trace.trace_id", context.getTraceId());
        attributes.put("trace.span_id", context.getSpanId());

        return attributes;
    }
}
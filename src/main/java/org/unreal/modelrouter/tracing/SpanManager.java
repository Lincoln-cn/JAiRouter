package org.unreal.modelrouter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Span管理器
 * 
 * 提供Span的创建、管理和生命周期控制，包括：
 * - Span的创建和配置
 * - Span状态管理
 * - 性能监控和统计
 * - 错误处理和恢复
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpanManager {
    
    private final Tracer tracer;
    
    // 统计信息
    private final AtomicLong totalSpansCreated = new AtomicLong(0);
    private final AtomicLong totalSpansFinished = new AtomicLong(0);
    private final AtomicLong totalSpansWithErrors = new AtomicLong(0);
    private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    
    /**
     * 创建HTTP服务器Span
     * 
     * @param operationName 操作名称
     * @param method HTTP方法
     * @param path 请求路径
     * @return 创建的Span
     */
    public Span createHttpServerSpan(String operationName, String method, String path) {
        Span span = createSpan(operationName, SpanKind.SERVER);
        
        // 设置HTTP相关属性
        span.setAttribute("http.method", method);
        span.setAttribute("http.target", path);
        span.setAttribute("component", "http-server");
        
        return span;
    }
    
    /**
     * 创建HTTP客户端Span
     * 
     * @param operationName 操作名称
     * @param method HTTP方法
     * @param url 请求URL
     * @return 创建的Span
     */
    public Span createHttpClientSpan(String operationName, String method, String url) {
        Span span = createSpan(operationName, SpanKind.CLIENT);
        
        // 设置HTTP客户端相关属性
        span.setAttribute("http.method", method);
        span.setAttribute("http.url", url);
        span.setAttribute("component", "http-client");
        
        return span;
    }
    
    /**
     * 创建内部操作Span
     * 
     * @param operationName 操作名称
     * @param component 组件名称
     * @return 创建的Span
     */
    public Span createInternalSpan(String operationName, String component) {
        Span span = createSpan(operationName, SpanKind.INTERNAL);
        
        // 设置组件信息
        span.setAttribute("component", component);
        
        return span;
    }
    
    /**
     * 创建数据库操作Span
     * 
     * @param operationName 操作名称
     * @param dbType 数据库类型
     * @param dbName 数据库名称
     * @return 创建的Span
     */
    public Span createDatabaseSpan(String operationName, String dbType, String dbName) {
        Span span = createSpan(operationName, SpanKind.CLIENT);
        
        // 设置数据库相关属性
        span.setAttribute("db.system", dbType);
        span.setAttribute("db.name", dbName);
        span.setAttribute("component", "database");
        
        return span;
    }
    
    /**
     * 创建消息队列Span
     * 
     * @param operationName 操作名称
     * @param kind Span类型（PRODUCER或CONSUMER）
     * @param destination 目标队列或主题
     * @return 创建的Span
     */
    public Span createMessagingSpan(String operationName, SpanKind kind, String destination) {
        Span span = createSpan(operationName, kind);
        
        // 设置消息队列相关属性
        span.setAttribute("messaging.destination", destination);
        span.setAttribute("component", "messaging");
        
        return span;
    }
    
    /**
     * 创建基础Span
     * 
     * @param operationName 操作名称
     * @param kind Span类型
     * @return 创建的Span
     */
    public Span createSpan(String operationName, SpanKind kind) {
        try {
            Span span = tracer.spanBuilder(operationName)
                    .setSpanKind(kind)
                    .startSpan();
            
            // 更新统计信息
            totalSpansCreated.incrementAndGet();
            operationCounts.computeIfAbsent(operationName, k -> new AtomicLong(0)).incrementAndGet();
            
            // 设置基础属性
            span.setAttribute("span.kind", kind.name());
            span.setAttribute("service.name", "jairouter");
            span.setAttribute("created.timestamp", Instant.now().toString());
            
            log.debug("创建Span: {} (kind: {}, spanId: {})", 
                    operationName, kind, span.getSpanContext().getSpanId());
            
            return span;
        } catch (Exception e) {
            log.warn("创建Span失败: {}", operationName, e);
            return Span.getInvalid();
        }
    }
    
    /**
     * 成功完成Span
     * 
     * @param span 要完成的Span
     * @param duration 操作耗时
     */
    public void finishSpanSuccessfully(Span span, Duration duration) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            // 设置成功状态和耗时
            span.setStatus(StatusCode.OK);
            span.setAttribute("duration.ms", duration.toMillis());
            span.setAttribute("finished.timestamp", Instant.now().toString());
            
            // 结束Span
            span.end();
            
            // 更新统计信息
            totalSpansFinished.incrementAndGet();
            
            log.debug("成功完成Span，耗时: {}ms (spanId: {})", 
                    duration.toMillis(), span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.warn("完成Span时发生错误", e);
        }
    }
    
    /**
     * 完成Span并记录错误
     * 
     * @param span 要完成的Span
     * @param error 错误信息
     * @param duration 操作耗时
     */
    public void finishSpanWithError(Span span, Throwable error, Duration duration) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            // 记录异常信息
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getMessage());
            
            // 设置错误相关属性
            span.setAttribute("error", true);
            span.setAttribute("error.type", error.getClass().getSimpleName());
            span.setAttribute("error.message", error.getMessage());
            span.setAttribute("duration.ms", duration.toMillis());
            span.setAttribute("finished.timestamp", Instant.now().toString());
            
            // 结束Span
            span.end();
            
            // 更新统计信息
            totalSpansFinished.incrementAndGet();
            totalSpansWithErrors.incrementAndGet();
            
            log.debug("完成Span并记录错误: {} (spanId: {}, duration: {}ms)", 
                    error.getMessage(), span.getSpanContext().getSpanId(), duration.toMillis());
        } catch (Exception e) {
            log.warn("完成Span并记录错误时发生异常", e);
        }
    }
    
    /**
     * 为Span添加业务事件
     * 
     * @param span 目标Span
     * @param eventName 事件名称
     * @param attributes 事件属性
     */
    public void addBusinessEvent(Span span, String eventName, Map<String, Object> attributes) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            // 添加时间戳
            Map<String, Object> eventAttributes = new HashMap<>(attributes);
            eventAttributes.put("timestamp", Instant.now().toString());
            eventAttributes.put("event.type", "business");
            
            // 转换属性并添加事件
            io.opentelemetry.api.common.AttributesBuilder builder = 
                    io.opentelemetry.api.common.Attributes.builder();
            
            eventAttributes.forEach((key, value) -> {
                if (value instanceof String) {
                    builder.put(io.opentelemetry.api.common.AttributeKey.stringKey(key), (String) value);
                } else if (value instanceof Long) {
                    builder.put(io.opentelemetry.api.common.AttributeKey.longKey(key), (Long) value);
                } else if (value instanceof Double) {
                    builder.put(io.opentelemetry.api.common.AttributeKey.doubleKey(key), (Double) value);
                } else if (value instanceof Boolean) {
                    builder.put(io.opentelemetry.api.common.AttributeKey.booleanKey(key), (Boolean) value);
                } else {
                    builder.put(io.opentelemetry.api.common.AttributeKey.stringKey(key), value.toString());
                }
            });
            
            span.addEvent(eventName, builder.build());
            
            log.debug("为Span添加业务事件: {} (spanId: {})", 
                    eventName, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("添加业务事件失败: {}", eventName, e);
        }
    }
    
    /**
     * 为Span设置业务标签
     * 
     * @param span 目标Span
     * @param tags 业务标签
     */
    public void setBusinessTags(Span span, Map<String, Object> tags) {
        if (span == null || !span.getSpanContext().isValid() || tags == null) {
            return;
        }
        
        try {
            tags.forEach((key, value) -> {
                if (value instanceof String) {
                    span.setAttribute(key, (String) value);
                } else if (value instanceof Long) {
                    span.setAttribute(key, (Long) value);
                } else if (value instanceof Double) {
                    span.setAttribute(key, (Double) value);
                } else if (value instanceof Boolean) {
                    span.setAttribute(key, (Boolean) value);
                } else {
                    span.setAttribute(key, value.toString());
                }
            });
            
            log.debug("为Span设置业务标签: {} (spanId: {})", 
                    tags.keySet(), span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置业务标签失败", e);
        }
    }
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSpansCreated", totalSpansCreated.get());
        stats.put("totalSpansFinished", totalSpansFinished.get());
        stats.put("totalSpansWithErrors", totalSpansWithErrors.get());
        stats.put("activeSpans", totalSpansCreated.get() - totalSpansFinished.get());
        stats.put("errorRate", calculateErrorRate());
        stats.put("operationCounts", new HashMap<>(operationCounts));
        return stats;
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalSpansCreated.set(0);
        totalSpansFinished.set(0);
        totalSpansWithErrors.set(0);
        operationCounts.clear();
        log.info("重置Span统计信息");
    }
    
    /**
     * 计算错误率
     */
    private double calculateErrorRate() {
        long finished = totalSpansFinished.get();
        if (finished == 0) {
            return 0.0;
        }
        return (double) totalSpansWithErrors.get() / finished;
    }
}
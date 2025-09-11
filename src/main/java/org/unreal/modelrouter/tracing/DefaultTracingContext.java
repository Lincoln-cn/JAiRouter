package org.unreal.modelrouter.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认追踪上下文实现
 * 
 * 基于OpenTelemetry API实现的追踪上下文，提供：
 * - Span生命周期管理
 * - W3C Trace Context标准的上下文传播
 * - 属性和事件管理
 * - 错误处理和状态管理
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class DefaultTracingContext implements TracingContext {
    
    private final Tracer tracer;
    private final W3CTraceContextPropagator propagator;
    private Context context;
    private Span currentSpan;
    
    /**
     * 构造函数
     * 
     * @param tracer OpenTelemetry Tracer实例
     */
    public DefaultTracingContext(Tracer tracer) {
        this.tracer = tracer;
        this.propagator = W3CTraceContextPropagator.getInstance();
        this.context = Context.current();
        this.currentSpan = Span.getInvalid();
    }
    
    /**
     * 私有构造函数，用于复制上下文
     */
    private DefaultTracingContext(Tracer tracer, Context context, Span currentSpan) {
        this.tracer = tracer;
        this.propagator = W3CTraceContextPropagator.getInstance();
        this.context = context;
        this.currentSpan = currentSpan;
    }
    
    @Override
    public Span createSpan(String operationName, SpanKind kind) {
        try {
            SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                    .setSpanKind(kind);
            
            // 如果有当前上下文，设置为父上下文
            if (context != null) {
                spanBuilder.setParent(context);
            }
            
            Span span = spanBuilder.startSpan();
            
            // 更新当前上下文
            this.context = context.with(span);
            this.currentSpan = span;
            
            log.debug("创建Span: {} (kind: {}, traceId: {}, spanId: {})", 
                    operationName, kind, getTraceId(), getSpanId());
            
            return span;
        } catch (Exception e) {
            log.warn("创建Span失败: {}", operationName, e);
            return Span.getInvalid();
        }
    }
    
    @Override
    public Span createChildSpan(String operationName, SpanKind kind, Span parentSpan) {
        try {
            Context parentContext = context.with(parentSpan);
            
            Span span = tracer.spanBuilder(operationName)
                    .setSpanKind(kind)
                    .setParent(parentContext)
                    .startSpan();
            
            log.debug("创建子Span: {} (parent: {}, traceId: {}, spanId: {})", 
                    operationName, parentSpan.getSpanContext().getSpanId(), 
                    span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
            
            return span;
        } catch (Exception e) {
            log.warn("创建子Span失败: {}", operationName, e);
            return Span.getInvalid();
        }
    }
    
    @Override
    public Span getCurrentSpan() {
        return currentSpan != null ? currentSpan : Span.getInvalid();
    }
    
    @Override
    public void setCurrentSpan(Span span) {
        this.currentSpan = span;
        if (span != null && span.getSpanContext().isValid()) {
            this.context = context.with(span);
        }
    }
    
    @Override
    public void finishSpan(Span span) {
        if (span != null && span.getSpanContext().isValid()) {
            try {
                span.setStatus(StatusCode.OK);
                span.end();
                log.debug("完成Span: {} (traceId: {}, spanId: {})", 
                        span.toString(), 
                        span.getSpanContext().getTraceId(), 
                        span.getSpanContext().getSpanId());
            } catch (Exception e) {
                log.warn("完成Span时发生错误", e);
            }
        }
    }
    
    @Override
    public void finishSpan(Span span, Throwable error) {
        if (span != null && span.getSpanContext().isValid()) {
            try {
                // 记录错误信息
                span.recordException(error);
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.end();
                
                log.debug("完成Span并记录错误: {} (traceId: {}, spanId: {}, error: {})", 
                        span.toString(),
                        span.getSpanContext().getTraceId(), 
                        span.getSpanContext().getSpanId(),
                        error.getMessage());
            } catch (Exception e) {
                log.warn("完成Span并记录错误时发生异常", e);
            }
        }
    }
    
    @Override
    public void injectContext(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        
        try {
            propagator.inject(context, headers, MAP_SETTER);
            log.debug("注入追踪上下文到头部，traceId: {}", getTraceId());
        } catch (Exception e) {
            log.warn("注入追踪上下文失败", e);
        }
    }
    
    @Override
    public TracingContext extractContext(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return new DefaultTracingContext(tracer);
        }
        
        try {
            Context extractedContext = propagator.extract(Context.current(), headers, MAP_GETTER);
            Span extractedSpan = Span.fromContext(extractedContext);
            
            log.debug("从头部提取追踪上下文，traceId: {}", 
                    extractedSpan.getSpanContext().getTraceId());
            
            return new DefaultTracingContext(tracer, extractedContext, extractedSpan);
        } catch (Exception e) {
            log.warn("提取追踪上下文失败", e);
            return new DefaultTracingContext(tracer);
        }
    }
    
    @Override
    public TracingContext copy() {
        return new DefaultTracingContext(tracer, context, currentSpan);
    }
    
    @Override
    public void setTag(String key, String value) {
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            try {
                currentSpan.setAttribute(AttributeKey.stringKey(key), value);
            } catch (Exception e) {
                log.debug("设置字符串属性失败: {} = {}", key, value, e);
            }
        }
    }
    
    @Override
    public void setTag(String key, Number value) {
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            try {
                if (value instanceof Long) {
                    currentSpan.setAttribute(AttributeKey.longKey(key), value.longValue());
                } else if (value instanceof Double) {
                    currentSpan.setAttribute(AttributeKey.doubleKey(key), value.doubleValue());
                } else {
                    currentSpan.setAttribute(AttributeKey.longKey(key), value.longValue());
                }
            } catch (Exception e) {
                log.debug("设置数值属性失败: {} = {}", key, value, e);
            }
        }
    }
    
    @Override
    public void setTag(String key, Boolean value) {
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            try {
                currentSpan.setAttribute(AttributeKey.booleanKey(key), value);
            } catch (Exception e) {
                log.debug("设置布尔属性失败: {} = {}", key, value, e);
            }
        }
    }
    
    @Override
    public void addEvent(String name, Map<String, Object> attributes) {
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            try {
                if (attributes == null || attributes.isEmpty()) {
                    currentSpan.addEvent(name);
                } else {
                    io.opentelemetry.api.common.AttributesBuilder builder = Attributes.builder();
                    attributes.forEach((k, v) -> {
                        if (v instanceof String) {
                            builder.put(AttributeKey.stringKey(k), (String) v);
                        } else if (v instanceof Long) {
                            builder.put(AttributeKey.longKey(k), (Long) v);
                        } else if (v instanceof Double) {
                            builder.put(AttributeKey.doubleKey(k), (Double) v);
                        } else if (v instanceof Boolean) {
                            builder.put(AttributeKey.booleanKey(k), (Boolean) v);
                        } else {
                            builder.put(AttributeKey.stringKey(k), v.toString());
                        }
                    });
                    currentSpan.addEvent(name, builder.build());
                }
            } catch (Exception e) {
                log.debug("添加事件失败: {}", name, e);
            }
        }
    }
    
    @Override
    public void addEvent(String name) {
        addEvent(name, null);
    }
    
    @Override
    public String getTraceId() {
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getTraceId();
        }
        return "";
    }
    
    @Override
    public String getSpanId() {
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getSpanId();
        }
        return "";
    }
    
    @Override
    public Map<String, String> getLogContext() {
        Map<String, String> logContext = new HashMap<>();
        logContext.put("traceId", getTraceId());
        logContext.put("spanId", getSpanId());
        return logContext;
    }
    
    @Override
    public boolean isActive() {
        return currentSpan != null && currentSpan.getSpanContext().isValid();
    }
    
    @Override
    public boolean isSampled() {
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().isSampled();
        }
        return false;
    }
    
    @Override
    public void clear() {
        this.context = Context.current();
        this.currentSpan = Span.getInvalid();
        log.debug("清理追踪上下文");
    }
    
    // ========================================
    // 内部工具类
    // ========================================
    
    /**
     * Map设置器，用于注入追踪上下文
     */
    private static final TextMapSetter<Map<String, String>> MAP_SETTER = 
            new TextMapSetter<Map<String, String>>() {
                @Override
                public void set(@Nullable Map<String, String> carrier, String key, String value) {
                    if (carrier != null) {
                        carrier.put(key, value);
                    }
                }
            };
    
    /**
     * Map获取器，用于提取追踪上下文
     */
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = 
            new TextMapGetter<Map<String, String>>() {
                @Override
                public Iterable<String> keys(@Nullable Map<String, String> carrier) {
                    return carrier != null ? carrier.keySet() : java.util.Collections.emptyList();
                }
                
                @Override
                @Nullable
                public String get(@Nullable Map<String, String> carrier, String key) {
                    return carrier != null ? carrier.get(key) : null;
                }
            };
}
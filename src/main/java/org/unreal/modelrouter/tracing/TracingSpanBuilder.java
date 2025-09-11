package org.unreal.modelrouter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 追踪Span构建器包装器
 * 
 * 简化Span创建过程，提供：
 * - 链式调用API
 * - 常用属性的便捷设置
 * - 自动属性注入
 * - 错误处理和降级
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class TracingSpanBuilder {
    
    private final Tracer tracer;
    private final String operationName;
    private final Map<String, Object> attributes = new HashMap<>();
    private SpanKind spanKind = SpanKind.INTERNAL;
    private Context parentContext;
    private Span parentSpan;
    private Instant startTime;
    private boolean autoStart = true;
    
    /**
     * 构造函数
     * 
     * @param tracer OpenTelemetry Tracer
     * @param operationName 操作名称
     */
    public TracingSpanBuilder(Tracer tracer, String operationName) {
        this.tracer = tracer;
        this.operationName = operationName;
    }
    
    /**
     * 设置Span类型
     * 
     * @param kind Span类型
     * @return 当前构建器
     */
    public TracingSpanBuilder setKind(SpanKind kind) {
        this.spanKind = kind;
        return this;
    }
    
    /**
     * 设置为服务器Span
     * 
     * @return 当前构建器
     */
    public TracingSpanBuilder asServer() {
        return setKind(SpanKind.SERVER);
    }
    
    /**
     * 设置为客户端Span
     * 
     * @return 当前构建器
     */
    public TracingSpanBuilder asClient() {
        return setKind(SpanKind.CLIENT);
    }
    
    /**
     * 设置为内部Span
     * 
     * @return 当前构建器
     */
    public TracingSpanBuilder asInternal() {
        return setKind(SpanKind.INTERNAL);
    }
    
    /**
     * 设置为生产者Span
     * 
     * @return 当前构建器
     */
    public TracingSpanBuilder asProducer() {
        return setKind(SpanKind.PRODUCER);
    }
    
    /**
     * 设置为消费者Span
     * 
     * @return 当前构建器
     */
    public TracingSpanBuilder asConsumer() {
        return setKind(SpanKind.CONSUMER);
    }
    
    /**
     * 设置父上下文
     * 
     * @param context 父上下文
     * @return 当前构建器
     */
    public TracingSpanBuilder setParent(Context context) {
        this.parentContext = context;
        return this;
    }
    
    /**
     * 设置父Span
     * 
     * @param span 父Span
     * @return 当前构建器
     */
    public TracingSpanBuilder setParent(Span span) {
        this.parentSpan = span;
        return this;
    }
    
    /**
     * 设置开始时间
     * 
     * @param startTime 开始时间
     * @return 当前构建器
     */
    public TracingSpanBuilder setStartTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }
    
    /**
     * 设置字符串属性
     * 
     * @param key 属性键
     * @param value 属性值
     * @return 当前构建器
     */
    public TracingSpanBuilder setAttribute(String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
        return this;
    }
    
    /**
     * 设置数值属性
     * 
     * @param key 属性键
     * @param value 属性值
     * @return 当前构建器
     */
    public TracingSpanBuilder setAttribute(String key, Number value) {
        if (value != null) {
            attributes.put(key, value);
        }
        return this;
    }
    
    /**
     * 设置布尔属性
     * 
     * @param key 属性键
     * @param value 属性值
     * @return 当前构建器
     */
    public TracingSpanBuilder setAttribute(String key, Boolean value) {
        if (value != null) {
            attributes.put(key, value);
        }
        return this;
    }
    
    /**
     * 批量设置属性
     * 
     * @param attrs 属性Map
     * @return 当前构建器
     */
    public TracingSpanBuilder setAttributes(Map<String, Object> attrs) {
        if (attrs != null) {
            attributes.putAll(attrs);
        }
        return this;
    }
    
    /**
     * 设置HTTP相关属性
     * 
     * @param method HTTP方法
     * @param url 请求URL
     * @param statusCode 状态码（可选）
     * @return 当前构建器
     */
    public TracingSpanBuilder setHttpAttributes(String method, String url, Integer statusCode) {
        setAttribute("http.method", method);
        setAttribute("http.url", url);
        if (statusCode != null) {
            setAttribute("http.status_code", statusCode);
        }
        return this;
    }
    
    /**
     * 设置数据库相关属性
     * 
     * @param system 数据库系统
     * @param name 数据库名称
     * @param statement SQL语句（可选）
     * @return 当前构建器
     */
    public TracingSpanBuilder setDatabaseAttributes(String system, String name, String statement) {
        setAttribute("db.system", system);
        setAttribute("db.name", name);
        if (statement != null) {
            setAttribute("db.statement", statement);
        }
        return this;
    }
    
    /**
     * 设置消息队列相关属性
     * 
     * @param destination 目标队列或主题
     * @param operation 操作类型（send/receive）
     * @return 当前构建器
     */
    public TracingSpanBuilder setMessagingAttributes(String destination, String operation) {
        setAttribute("messaging.destination", destination);
        setAttribute("messaging.operation", operation);
        return this;
    }
    
    /**
     * 设置组件名称
     * 
     * @param component 组件名称
     * @return 当前构建器
     */
    public TracingSpanBuilder setComponent(String component) {
        return setAttribute("component", component);
    }
    
    /**
     * 设置服务名称
     * 
     * @param service 服务名称
     * @return 当前构建器
     */
    public TracingSpanBuilder setService(String service) {
        return setAttribute("service.name", service);
    }
    
    /**
     * 设置用户信息
     * 
     * @param userId 用户ID
     * @param userType 用户类型（可选）
     * @return 当前构建器
     */
    public TracingSpanBuilder setUserInfo(String userId, String userType) {
        setAttribute("user.id", userId);
        if (userType != null) {
            setAttribute("user.type", userType);
        }
        return this;
    }
    
    /**
     * 设置是否自动开始
     * 
     * @param autoStart 是否自动开始
     * @return 当前构建器
     */
    public TracingSpanBuilder setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
        return this;
    }
    
    /**
     * 构建并开始Span
     * 
     * @return 创建的Span
     */
    public Span start() {
        try {
            SpanBuilder builder = tracer.spanBuilder(operationName)
                    .setSpanKind(spanKind);
            
            // 设置父上下文
            if (parentContext != null) {
                builder.setParent(parentContext);
            } else if (parentSpan != null) {
                builder.setParent(Context.current().with(parentSpan));
            }
            
            // 设置开始时间
            if (startTime != null) {
                builder.setStartTimestamp(startTime.toEpochMilli(), TimeUnit.MILLISECONDS);
            }
            
            // 开始Span
            Span span = builder.startSpan();
            
            // 设置属性
            setSpanAttributes(span);
            
            log.debug("构建并开始Span: {} (kind: {}, spanId: {})", 
                    operationName, spanKind, span.getSpanContext().getSpanId());
            
            return span;
        } catch (Exception e) {
            log.warn("构建Span失败: {}", operationName, e);
            return Span.getInvalid();
        }
    }
    
    /**
     * 构建Span但不开始
     * 
     * @return Span构建器
     */
    public SpanBuilder build() {
        SpanBuilder builder = tracer.spanBuilder(operationName)
                .setSpanKind(spanKind);
        
        // 设置父上下文
        if (parentContext != null) {
            builder.setParent(parentContext);
        } else if (parentSpan != null) {
            builder.setParent(Context.current().with(parentSpan));
        }
        
        // 设置开始时间
        if (startTime != null) {
            builder.setStartTimestamp(startTime.toEpochMilli(), TimeUnit.MILLISECONDS);
        }
        
        return builder;
    }
    
    /**
     * 设置Span属性
     */
    private void setSpanAttributes(Span span) {
        // 设置基础属性
        span.setAttribute("span.kind", spanKind.name());
        span.setAttribute("created.timestamp", Instant.now().toString());
        
        // 设置自定义属性
        attributes.forEach((key, value) -> {
            try {
                if (value instanceof String) {
                    span.setAttribute(key, (String) value);
                } else if (value instanceof Long) {
                    span.setAttribute(key, (Long) value);
                } else if (value instanceof Double) {
                    span.setAttribute(key, (Double) value);
                } else if (value instanceof Boolean) {
                    span.setAttribute(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    span.setAttribute(key, ((Integer) value).longValue());
                } else if (value instanceof Float) {
                    span.setAttribute(key, ((Float) value).doubleValue());
                } else {
                    span.setAttribute(key, value.toString());
                }
            } catch (Exception e) {
                log.debug("设置Span属性失败: {} = {}", key, value, e);
            }
        });
    }
    
    /**
     * 创建新的构建器实例
     * 
     * @param tracer OpenTelemetry Tracer
     * @param operationName 操作名称
     * @return 新的构建器实例
     */
    public static TracingSpanBuilder create(Tracer tracer, String operationName) {
        return new TracingSpanBuilder(tracer, operationName);
    }
}
package org.unreal.modelrouter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;

import java.util.Map;

/**
 * 追踪上下文接口
 * 
 * 提供分布式追踪的核心功能，包括：
 * - Span生命周期管理
 * - 追踪上下文传播
 * - 属性和事件管理
 * - 日志关联信息
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public interface TracingContext {
    
    // ========================================
    // Span管理
    // ========================================
    
    /**
     * 创建新的Span
     * 
     * @param operationName 操作名称
     * @param kind Span类型
     * @return 创建的Span
     */
    Span createSpan(String operationName, SpanKind kind);
    
    /**
     * 创建子Span
     * 
     * @param operationName 操作名称
     * @param kind Span类型
     * @param parentSpan 父Span
     * @return 创建的子Span
     */
    Span createChildSpan(String operationName, SpanKind kind, Span parentSpan);
    
    /**
     * 获取当前活跃的Span
     * 
     * @return 当前Span，如果没有则返回null
     */
    Span getCurrentSpan();
    
    /**
     * 设置当前活跃的Span
     * 
     * @param span 要设置的Span
     */
    void setCurrentSpan(Span span);
    
    /**
     * 完成Span
     * 
     * @param span 要完成的Span
     */
    void finishSpan(Span span);
    
    /**
     * 完成Span并记录错误
     * 
     * @param span 要完成的Span
     * @param error 错误信息
     */
    void finishSpan(Span span, Throwable error);
    
    // ========================================
    // 上下文传播
    // ========================================
    
    /**
     * 将追踪上下文注入到头部信息中
     * 
     * @param headers 要注入的头部信息
     */
    void injectContext(Map<String, String> headers);
    
    /**
     * 从头部信息中提取追踪上下文
     * 
     * @param headers 包含追踪信息的头部
     * @return 提取的追踪上下文
     */
    TracingContext extractContext(Map<String, String> headers);
    
    /**
     * 复制当前追踪上下文
     * 
     * @return 复制的追踪上下文
     */
    TracingContext copy();
    
    // ========================================
    // 属性管理
    // ========================================
    
    /**
     * 设置字符串属性
     * 
     * @param key 属性键
     * @param value 属性值
     */
    void setTag(String key, String value);
    
    /**
     * 设置数值属性
     * 
     * @param key 属性键
     * @param value 属性值
     */
    void setTag(String key, Number value);
    
    /**
     * 设置布尔属性
     * 
     * @param key 属性键
     * @param value 属性值
     */
    void setTag(String key, Boolean value);
    
    /**
     * 添加事件
     * 
     * @param name 事件名称
     * @param attributes 事件属性
     */
    void addEvent(String name, Map<String, Object> attributes);
    
    /**
     * 添加简单事件
     * 
     * @param name 事件名称
     */
    void addEvent(String name);
    
    // ========================================
    // 日志关联
    // ========================================
    
    /**
     * 获取追踪ID
     * 
     * @return 追踪ID，如果没有则返回空字符串
     */
    String getTraceId();
    
    /**
     * 获取当前SpanID
     * 
     * @return SpanID，如果没有则返回空字符串
     */
    String getSpanId();
    
    /**
     * 获取日志上下文信息
     * 
     * @return 包含traceId和spanId的Map
     */
    Map<String, String> getLogContext();
    
    // ========================================
    // 状态管理
    // ========================================
    
    /**
     * 检查是否有活跃的追踪
     * 
     * @return 如果有活跃追踪返回true
     */
    boolean isActive();
    
    /**
     * 检查是否启用了采样
     * 
     * @return 如果启用采样返回true
     */
    boolean isSampled();
    
    /**
     * 清理追踪上下文
     */
    void clear();
}
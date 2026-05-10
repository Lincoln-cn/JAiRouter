package org.unreal.modelrouter.router.adapter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;
import org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer;

/**
 * 适配器追踪管理器
 *
 * 负责管理适配器调用的分布式追踪，包括：
 * - 适配器调用 Span 的创建和管理
 * - 追踪上下文的传播
 * - 追踪属性记录
 * - 错误状态记录
 * - 业务日志记录 (v2.27.0)
 *
 * @author JAiRouter Team
 * @since v2.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdapterTracingManager {

    private final Tracer tracer;
    
    private AdapterTracingEnhancer enhancer; // v2.27.0: 延迟注入避免循环依赖
    
    @Autowired
    public void setEnhancer(final AdapterTracingEnhancer enhancer) {
        this.enhancer = enhancer;
    }

    /**
     * 开始适配器调用追踪
     * 
     * @param adapterType 适配器类型（如 gpustack, ollama 等）
     * @param instance 模型实例信息
     * @param serviceType 服务类型（如 CHAT, EMBEDDING 等）
     * @param modelName 模型名称
     * @return 创建的 Span
     */
    public Span startAdapterCall(
            final String adapterType,
            final ModelRouterProperties.ModelInstance instance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {
        
        // 获取当前追踪上下文
        TracingContext context = TracingContextHolder.getCurrentContext();
        
        // 如果没有活跃的追踪上下文，创建新的上下文
        if (context == null || !context.isActive()) {
            log.debug("无活跃追踪上下文，跳过适配器追踪：adapter={}, instance={}", adapterType, instance.getName());
            return null;
        }
        
        // 构建 Span 名称
        String spanName = buildSpanName(adapterType, serviceType, modelName);
        
        // 创建客户端 Span（适配器调用是下游服务调用）
        Span span = context.createSpan(spanName, SpanKind.CLIENT);
        
        // 记录适配器属性
        recordAdapterAttributes(span, adapterType, instance, serviceType, modelName);
        
        // 设置为当前 Span
        context.setCurrentSpan(span);
        
        String spanId = span.getSpanContext() != null ? span.getSpanContext().getSpanId() : "unknown";
        log.debug("开始适配器调用追踪：spanName={}, traceId={}, spanId={}",
                spanName, context != null ? context.getTraceId() : "unknown", spanId);
        
        return span;
    }

    /**
     * 结束适配器调用追踪
     * 
     * @param span 要结束的 Span
     * @param success 是否成功
     * @param error 错误信息（如果失败）
     */
    public void endAdapterCall(final Span span, final boolean success, final Throwable error) {
        if (span == null) {
            return;
        }
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        
        try {
            if (success) {
                // 成功完成
                span.setStatus(StatusCode.OK);
                log.debug("结束适配器调用追踪（成功）：traceId={}", 
                        context != null ? context.getTraceId() : "unknown");
            } else {
                // 失败，记录错误状态
                span.setStatus(StatusCode.ERROR);
                
                if (error != null) {
                    // 记录错误信息
                    span.recordException(error);
                    span.setAttribute("error.message", error.getMessage());
                    span.setAttribute("error.type", error.getClass().getSimpleName());
                }
                
                log.debug("结束适配器调用追踪（失败）：traceId={}, error={}", 
                        context != null ? context.getTraceId() : "unknown",
                        error != null ? error.getMessage() : "unknown error");
            }
            
            // 结束 Span
            span.end();
            
            // 从上下文中移除
            if (context != null) {
                context.finishSpan(span);
            }
            
        } catch (Exception e) {
            log.error("结束适配器调用追踪时出错", e);
        }
    }

    /**
     * 记录适配器属性
     * 
     * @param span 追踪 Span
     * @param adapterType 适配器类型
     * @param instance 模型实例信息
     * @param serviceType 服务类型
     * @param modelName 模型名称
     */
    public void recordAdapterAttributes(
            final Span span,
            final String adapterType,
            final ModelRouterProperties.ModelInstance instance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {
        
        if (span == null) {
            return;
        }
        
        // 基础适配器属性
        span.setAttribute("adapter.type", adapterType != null ? adapterType : "unknown");
        span.setAttribute("adapter.instance.name", instance.getName() != null ? instance.getName() : "unknown");
        span.setAttribute("adapter.instance.url", instance.getBaseUrl() != null ? instance.getBaseUrl() : "unknown");
        
        // 服务类型
        if (serviceType != null) {
            span.setAttribute("adapter.service.type", serviceType.name());
        }
        
        // 模型信息
        if (modelName != null && !modelName.isEmpty()) {
            span.setAttribute("adapter.model.name", modelName);
        }
        
        // 实例健康状态（通过 status 字段推断）
        if (instance.getStatus() != null) {
            boolean isHealthy = "active".equals(instance.getStatus());
            span.setAttribute("adapter.instance.healthy", isHealthy);
        }
        
        // 实例权重（如果配置了）
        int weight = instance.getWeight();
        if (weight > 0) {
            span.setAttribute("adapter.instance.weight", (long) weight);
        }
        
        log.debug("记录适配器属性：adapter={}, instance={}, service={}, model={}",
                adapterType, instance.getName(), serviceType, modelName);
    }

    /**
     * 记录请求参数
     * 
     * @param span 追踪 Span
     * @param requestSize 请求大小（字节）
     * @param hasStream 是否流式请求
     */
    public void recordRequestAttributes(final Span span, final long requestSize, final boolean hasStream) {
        if (span == null) {
            return;
        }
        
        span.setAttribute("adapter.request.size", requestSize);
        span.setAttribute("adapter.request.streaming", hasStream);
        
        log.debug("记录请求属性：size={} bytes, streaming={}", requestSize, hasStream);
    }

    /**
     * 记录响应属性
     * 
     * @param span 追踪 Span
     * @param responseSize 响应大小（字节）
     * @param durationMs 响应时间（毫秒）
     * @param statusCode HTTP 状态码
     */
    public void recordResponseAttributes(final Span span, final long responseSize, final long durationMs, final int statusCode) {
        if (span == null) {
            return;
        }
        
        span.setAttribute("adapter.response.size", responseSize);
        span.setAttribute("adapter.response.duration.ms", durationMs);
        span.setAttribute("adapter.response.status.code", statusCode);
        
        log.debug("记录响应属性：size={} bytes, duration={}ms, status={}", responseSize, durationMs, statusCode);
    }

    /**
     * 构建 Span 名称
     * 
     * @param adapterType 适配器类型
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return Span 名称
     */
    private String buildSpanName(
            final String adapterType,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {
        
        StringBuilder spanName = new StringBuilder("adapter.call.");
        
        // 添加适配器类型
        if (adapterType != null && !adapterType.isEmpty()) {
            spanName.append(adapterType);
        } else {
            spanName.append("unknown");
        }
        
        // 添加服务类型
        if (serviceType != null) {
            spanName.append(".").append(serviceType.name().toLowerCase());
        }
        
        // 添加模型名称（简化版）
        if (modelName != null && !modelName.isEmpty()) {
            // 只取模型名称的一部分，避免 Span 名称过长
            String shortModelName = modelName.length() > 20
                    ? modelName.substring(0, 20) : modelName;
            spanName.append(".").append(shortModelName);
        }
        
        return spanName.toString();
    }

    // ========== v2.27.0: 业务日志封装方法 ==========

    /**
     * 记录适配器调用开始（整合 logAdapterCallStart + enhanceAdapterSpan）
     * 
     * @param adapterType 适配器类型
     * @param instance 实例信息
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @since v2.27.0
     */
    public void recordCallStart(
            final String adapterType,
            final ModelRouterProperties.ModelInstance instance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (!isTracingActive(context)) {
            return;
        }
        
        try {
            if (enhancer != null) {
                // 1. 记录调用开始日志
                enhancer.logAdapterCallStart(adapterType, instance, serviceType.name(), modelName, context);
                
                // 2. 增强 Span
                Span currentSpan = context.getCurrentSpan();
                if (currentSpan != null) {
                    enhancer.enhanceAdapterSpan(currentSpan, adapterType, instance, serviceType.name(), modelName);
                }
            }
            
            log.debug("记录适配器调用开始: adapter={}, instance={}, model={}", 
                    adapterType, instance.getName(), modelName);
        } catch (Exception e) {
            log.debug("记录适配器调用开始追踪失败: {}", e.getMessage());
        }
    }
    
    /**
     * 记录适配器调用完成
     * 
     * @param adapterType 适配器类型
     * @param instance 实例信息
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param durationMs 耗时（毫秒）
     * @param success 是否成功
     * @since v2.27.0
     */
    public void recordCallComplete(
            final String adapterType,
            final ModelRouterProperties.ModelInstance instance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final long durationMs,
            final boolean success) {
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (!isTracingActive(context)) {
            return;
        }
        
        try {
            if (enhancer != null) {
                enhancer.logAdapterCallComplete(adapterType, instance, serviceType.name(), 
                        modelName, durationMs, success, context);
            }
            
            log.debug("记录适配器调用完成: adapter={}, instance={}, duration={}ms, success={}", 
                    adapterType, instance.getName(), durationMs, success);
        } catch (Exception e) {
            log.debug("记录适配器调用完成追踪失败: {}", e.getMessage());
        }
    }
    
    /**
     * 记录适配器重试事件
     * 
     * @param adapterType 适配器类型
     * @param instance 实例信息
     * @param retryCount 当前重试次数
     * @param maxRetries 最大重试次数
     * @param error 导致重试的错误
     * @since v2.27.0
     */
    public void recordRetry(
            final String adapterType,
            final ModelRouterProperties.ModelInstance instance,
            final int retryCount,
            final int maxRetries,
            final Throwable error) {
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (!isTracingActive(context)) {
            return;
        }
        
        try {
            if (enhancer != null) {
                enhancer.logAdapterRetry(adapterType, instance, retryCount, maxRetries, error, context);
            }
            
            log.debug("记录适配器重试: adapter={}, instance={}, retry={}/{}", 
                    adapterType, instance.getName(), retryCount, maxRetries);
        } catch (Exception e) {
            log.debug("记录适配器重试追踪失败: {}", e.getMessage());
        }
    }
    
    /**
     * 记录适配器转换错误
     * 
     * @param adapterType 适配器类型
     * @param error 错误对象
     * @since v2.27.0
     */
    public void recordTransformError(
            final String adapterType,
            final Throwable error) {
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (!isTracingActive(context)) {
            return;
        }
        
        try {
            if (enhancer != null) {
                enhancer.logAdapterRetry(adapterType, null, 0, 0, error, context);
            }
            
            log.debug("记录适配器转换错误: adapter={}, error={}", adapterType, error.getMessage());
        } catch (Exception e) {
            log.debug("记录适配器转换错误追踪失败: {}", e.getMessage());
        }
    }
    
    /**
     * 检查追踪是否活跃（便捷方法）
     * 
     * @return 追踪是否活跃
     * @since v2.27.0
     */
    public boolean isTracingActive() {
        TracingContext context = TracingContextHolder.getCurrentContext();
        return isTracingActive(context);
    }
    
    /**
     * 内部方法：检查追踪上下文是否活跃
     */
    private boolean isTracingActive(final TracingContext context) {
        return context != null && context.isActive();
    }
}

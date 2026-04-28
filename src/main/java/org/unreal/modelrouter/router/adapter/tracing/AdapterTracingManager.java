package org.unreal.modelrouter.router.adapter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;

/**
 * 适配器追踪管理器
 * 
 * 负责管理适配器调用的分布式追踪，包括：
 * - 适配器调用 Span 的创建和管理
 * - 追踪上下文的传播
 * - 追踪属性记录
 * - 错误状态记录
 * 
 * @author JAiRouter Team
 * @since v2.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdapterTracingManager {

    private final Tracer tracer;

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
    public void recordResponseAttributes(final Span span, final long responseSize, final long durationMs,final int statusCode) {
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
            String shortModelName = modelName.length() > 20 ? 
                    modelName.substring(0, 20) : modelName;
            spanName.append(".").append(shortModelName);
        }
        
        return spanName.toString();
    }
}

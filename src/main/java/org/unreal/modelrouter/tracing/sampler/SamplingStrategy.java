package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

import java.util.Map;

/**
 * 采样策略接口
 * 
 * 定义采样决策的方法，支持基于不同条件的采样策略
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public interface SamplingStrategy {
    
    /**
     * 根据追踪上下文和操作信息决定是否采样
     * 
     * @param parentContext 父级上下文
     * @param traceId 追踪ID
     * @param spanName Span名称
     * @param spanKind Span类型
     * @param attributes Span属性
     * @param parentLinks 父级链接
     * @return 是否采样决策结果
     */
    SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String spanName,
            SpanKind spanKind,
            Map<String, Object> attributes,
            Map<String, String> parentLinks
    );
    
    /**
     * 获取采样策略描述
     * 
     * @return 描述信息
     */
    String getDescription();
}
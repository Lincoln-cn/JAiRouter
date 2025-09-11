package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于比例的采样策略
 * 
 * 根据配置的比例决定是否采样，支持0.0-1.0之间的采样率
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public class RatioBasedSamplingStrategy implements SamplingStrategy {
    
    private final double ratio;
    private final String description;
    
    /**
     * 构造基于比例的采样策略
     * 
     * @param ratio 采样率 (0.0-1.0)
     */
    public RatioBasedSamplingStrategy(double ratio) {
        if (ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException("采样率必须在0.0-1.0之间");
        }
        this.ratio = ratio;
        this.description = "RatioBasedSampling{" + ratio + "}";
    }
    
    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String spanName,
            SpanKind spanKind,
            Map<String, Object> attributes,
            Map<String, String> parentLinks) {
        
        // 如果采样率为1.0，则总是采样
        if (ratio >= 1.0) {
            return SamplingResult.create(SamplingResult.Decision.RECORD_AND_SAMPLE);
        }
        
        // 如果采样率为0.0，则从不采样
        if (ratio <= 0.0) {
            return SamplingResult.create(SamplingResult.Decision.DROP);
        }
        
        // 使用traceId的hash值来决定是否采样，确保一致性
        long traceIdHash = hashTraceId(traceId);
        double threshold = ratio * Long.MAX_VALUE;
        
        if (traceIdHash < threshold) {
            return SamplingResult.create(SamplingResult.Decision.RECORD_AND_SAMPLE);
        } else {
            return SamplingResult.create(SamplingResult.Decision.DROP);
        }
    }
    
    /**
     * 计算traceId的hash值
     * 
     * @param traceId 追踪ID
     * @return hash值
     */
    private long hashTraceId(String traceId) {
        // 简单的hash实现，实际项目中可能需要更复杂的hash算法
        return Math.abs(traceId.hashCode()) * ThreadLocalRandom.current().nextLong();
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    public double getRatio() {
        return ratio;
    }
}
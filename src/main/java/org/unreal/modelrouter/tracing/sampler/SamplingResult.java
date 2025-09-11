package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.api.trace.SpanKind;

import java.util.Map;

/**
 * 采样结果
 * 
 * 封装采样决策的结果，包括是否采样和采样属性
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public class SamplingResult {
    
    /**
     * 采样决策枚举
     */
    public enum Decision {
        /**
         * 记录Span
         */
        RECORD_AND_SAMPLE(SpanKind.INTERNAL),
        
        /**
         * 不记录Span
         */
        DROP(null),
        
        /**
         * 记录Span但不采样
         */
        RECORD_ONLY(null);
        
        private final SpanKind spanKind;
        
        Decision(SpanKind spanKind) {
            this.spanKind = spanKind;
        }
        
        public SpanKind getSpanKind() {
            return spanKind;
        }
    }
    
    private final Decision decision;
    private final Map<String, Object> attributes;
    private final String traceState;
    
    private SamplingResult(Decision decision, Map<String, Object> attributes, String traceState) {
        this.decision = decision;
        this.attributes = attributes;
        this.traceState = traceState;
    }
    
    /**
     * 创建采样结果实例
     * 
     * @param decision 采样决策
     * @param attributes 附加属性
     * @param traceState 追踪状态
     * @return 采样结果
     */
    public static SamplingResult create(Decision decision, Map<String, Object> attributes, String traceState) {
        return new SamplingResult(decision, attributes, traceState);
    }
    
    /**
     * 创建简单的采样结果实例
     * 
     * @param decision 采样决策
     * @return 采样结果
     */
    public static SamplingResult create(Decision decision) {
        return new SamplingResult(decision, Map.of(), null);
    }
    
    public Decision getDecision() {
        return decision;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public String getTraceState() {
        return traceState;
    }
}
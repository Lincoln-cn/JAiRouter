package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;

import java.util.List;
import java.util.Map;

/**
 * 基于规则的采样策略
 * 
 * 根据预定义的规则决定是否采样，支持基于属性的条件判断
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public class RuleBasedSamplingStrategy implements SamplingStrategy {
    
    private final TracingConfiguration.SamplingConfig samplingConfig;
    private final RatioBasedSamplingStrategy defaultSampler;
    private final String description;
    
    /**
     * 构造基于规则的采样策略
     * 
     * @param samplingConfig 采样配置
     */
    public RuleBasedSamplingStrategy(TracingConfiguration.SamplingConfig samplingConfig) {
        this.samplingConfig = samplingConfig;
        this.defaultSampler = new RatioBasedSamplingStrategy(samplingConfig.getRatio());
        this.description = "RuleBasedSampling";
    }
    
    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String spanName,
            SpanKind spanKind,
            Map<String, Object> attributes,
            Map<String, String> parentLinks) {
        
        // 检查是否在始终采样的操作列表中
        if (samplingConfig.getAlwaysSample().contains(spanName)) {
            return SamplingResult.create(SamplingResult.Decision.RECORD_AND_SAMPLE);
        }
        
        // 检查是否在从不采样的操作列表中
        if (samplingConfig.getNeverSample().contains(spanName)) {
            return SamplingResult.create(SamplingResult.Decision.DROP);
        }
        
        // 检查服务类型特定的采样率
        if (attributes.containsKey("service.type")) {
            String serviceType = (String) attributes.get("service.type");
            if (samplingConfig.getServiceRatios().containsKey(serviceType)) {
                Double serviceRatio = samplingConfig.getServiceRatios().get(serviceType);
                RatioBasedSamplingStrategy serviceSampler = new RatioBasedSamplingStrategy(serviceRatio);
                return serviceSampler.shouldSample(parentContext, traceId, spanName, spanKind, attributes, parentLinks);
            }
        }
        
        // 检查基于属性的采样规则
        for (TracingConfiguration.SamplingConfig.SamplingRule rule : samplingConfig.getRules()) {
            if (matchesRule(rule, attributes)) {
                RatioBasedSamplingStrategy ruleSampler = new RatioBasedSamplingStrategy(rule.getRatio());
                return ruleSampler.shouldSample(parentContext, traceId, spanName, spanKind, attributes, parentLinks);
            }
        }
        
        // 使用默认采样策略
        return defaultSampler.shouldSample(parentContext, traceId, spanName, spanKind, attributes, parentLinks);
    }
    
    /**
     * 检查属性是否匹配规则条件
     * 
     * @param rule 采样规则
     * @param attributes 属性集合
     * @return 是否匹配
     */
    private boolean matchesRule(TracingConfiguration.SamplingConfig.SamplingRule rule, Map<String, Object> attributes) {
        String condition = rule.getCondition();
        if (condition == null || condition.isEmpty()) {
            return false;
        }
        
        // 简单条件匹配实现
        // 实际项目中可以实现更复杂的表达式解析
        if (condition.contains(">=")) {
            String[] parts = condition.split(">=");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String valueStr = parts[1].trim();
                
                if (attributes.containsKey(key)) {
                    Object attrValue = attributes.get(key);
                    try {
                        double threshold = Double.parseDouble(valueStr);
                        double attrDoubleValue = Double.parseDouble(attrValue.toString());
                        return attrDoubleValue >= threshold;
                    } catch (NumberFormatException e) {
                        // 忽略格式错误
                    }
                }
            }
        } else if (condition.contains("==")) {
            String[] parts = condition.split("==");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String expectedValue = parts[1].trim();
                
                if (attributes.containsKey(key)) {
                    Object attrValue = attributes.get(key);
                    return expectedValue.equals(attrValue.toString());
                }
            }
        }
        
        return false;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
}
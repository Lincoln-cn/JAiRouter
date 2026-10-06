package org.unreal.modelrouter.monitor.tracing.sampler;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;

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
    public RuleBasedSamplingStrategy(final TracingConfiguration.SamplingConfig samplingConfig) {
        this.samplingConfig = samplingConfig;
        this.defaultSampler = new RatioBasedSamplingStrategy(samplingConfig.getRatio());
        this.description = "RuleBasedSampling";
    }
    
    @Override
    public SamplingResult shouldSample(
            final Context parentContext,
            final String traceId,
            final String spanName,
            final SpanKind spanKind,
            final Map<String, Object> attributes,
            final Map<String, String> parentLinks) {
        
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
    private boolean matchesRule(
            final TracingConfiguration.SamplingConfig.SamplingRule rule,
            final Map<String, Object> attributes) {
        String condition = rule.getCondition();
        if (condition == null || condition.isEmpty()) {
            return false;
        }
        
        // 支持四种比较运算符（与 SamplingConfigurationValidator.validateRuleCondition 的口径一致；
        // issue #234 之前这里只实现了 >= 与 ==，<= / != 会被校验器认可却不生效）
        for (String op : new String[] {">=", "<=", "==", "!="}) {
            final int idx = condition.indexOf(op);
            if (idx < 0) {
                continue;
            }
            final String key = condition.substring(0, idx).trim();
            final String expected = condition.substring(idx + op.length()).trim();
            if (!attributes.containsKey(key)) {
                return false;
            }
            final String actual = String.valueOf(attributes.get(key));
            if ("==".equals(op)) {
                return expected.equals(actual);
            }
            if ("!=".equals(op)) {
                return !expected.equals(actual);
            }
            try {
                final double threshold = Double.parseDouble(expected);
                final double actualValue = Double.parseDouble(actual);
                return ">=".equals(op) ? actualValue >= threshold : actualValue <= threshold;
            } catch (NumberFormatException e) {
                // 阈值或属性值不是数字：该条规则不匹配（与原先"忽略格式错误"的行为一致）
                return false;
            }
        }

        return false;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
}
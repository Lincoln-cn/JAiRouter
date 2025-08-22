package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采样策略管理器
 * 
 * 负责管理采样策略的创建、更新和获取，支持动态配置更新
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SamplingStrategyManager {
    
    private final TracingConfiguration tracingConfig;
    private final Map<String, Sampler> strategies = new ConcurrentHashMap<>();
    private volatile Sampler currentStrategy;
    
    public SamplingStrategyManager(TracingConfiguration tracingConfig) {
        this.tracingConfig = tracingConfig;
    }
    
    /**
     * 初始化采样策略管理器
     */
    @PostConstruct
    public void init() {
        log.info("初始化采样策略管理器");
        refreshStrategies();
        // 设置默认策略
        this.currentStrategy = strategies.get("ratio_based");
    }
    
    /**
     * 刷新采样策略
     */
    public void refreshStrategies() {
        log.info("刷新采样策略配置");
        
        TracingConfiguration.SamplingConfig samplingConfig = tracingConfig.getSampling();
        
        // 创建基于比例的采样策略
        strategies.put("ratio_based", new RatioBasedSamplingStrategy(samplingConfig.getRatio()));
        
        // 创建基于规则的采样策略
        strategies.put("rule_based", new RuleBasedSamplingStrategy(samplingConfig.getRules()));
        
        // 注意：由于配置类中没有这些字段，暂时使用固定值
        strategies.put("adaptive", new AdaptiveSamplingStrategy(
                1000, // targetSpansPerSecond
                0.1,  // minRatio
                1.0   // maxRatio
        ));
        
        // 如果当前策略为null，则设置默认策略
        if (this.currentStrategy == null) {
            this.currentStrategy = strategies.get("ratio_based");
        }
        
        log.info("采样策略刷新完成，当前策略数量: {}", strategies.size());
    }
    
    /**
     * 获取当前采样策略
     * 
     * @return 当前采样策略
     */
    public Sampler getCurrentStrategy() {
        return currentStrategy;
    }
    
    /**
     * 更新采样策略
     * 
     * @param strategyName 策略名称
     */
    public void updateStrategy(String strategyName) {
        Sampler strategy = strategies.get(strategyName);
        if (strategy != null) {
            this.currentStrategy = strategy;
            log.info("更新采样策略为: {}", strategyName);
        } else {
            log.warn("未找到采样策略: {}，使用默认策略", strategyName);
        }
    }
    
    /**
     * 更新采样配置
     * 
     * @param newConfig 新的采样配置
     */
    public void updateSamplingConfiguration(TracingConfiguration.SamplingConfig newConfig) {
        log.info("更新采样配置");
        refreshStrategies();
    }
    
    /**
     * 基于比例的采样策略实现
     */
    private static class RatioBasedSamplingStrategy implements Sampler {
        private final double ratio;
        
        public RatioBasedSamplingStrategy(double ratio) {
            this.ratio = ratio;
        }
        
        @Override
        public SamplingResult shouldSample(Context parentContext, String traceId, String name,
                                         SpanKind spanKind, Attributes attributes,
                                         List<LinkData> parentLinks) {
            // 使用简单的概率采样
            boolean shouldSample = Math.random() < ratio;
            if (shouldSample) {
                return SamplingResult.recordAndSample();
            } else {
                return SamplingResult.drop();
            }
        }
        
        @Override
        public String getDescription() {
            return "RatioBasedSampler{" + ratio + "}";
        }
    }
    
    /**
     * 基于规则的采样策略实现
     */
    private static class RuleBasedSamplingStrategy implements Sampler {
        private final List<TracingConfiguration.SamplingConfig.SamplingRule> rules;
        
        public RuleBasedSamplingStrategy(List<TracingConfiguration.SamplingConfig.SamplingRule> rules) {
            this.rules = rules;
        }
        
        @Override
        public SamplingResult shouldSample(Context parentContext, String traceId, String name,
                                         SpanKind spanKind, Attributes attributes,
                                         List<LinkData> parentLinks) {
            // 简单实现：检查是否有匹配的规则
            boolean shouldSample = true;
            
            // 这里可以实现更复杂的规则匹配逻辑
            // 例如基于服务名称、操作类型等进行条件判断
            
            if (shouldSample) {
                return SamplingResult.recordAndSample();
            } else {
                return SamplingResult.drop();
            }
        }
        
        @Override
        public String getDescription() {
            return "RuleBasedSampler";
        }
    }
    
    /**
     * 自适应采样策略实现
     */
    private static class AdaptiveSamplingStrategy implements Sampler {
        private final int targetSpansPerSecond;
        private final double minRatio;
        private final double maxRatio;
        private volatile long lastSampleTime = System.currentTimeMillis();
        private volatile int spanCount = 0;
        private volatile double currentRatio;
        
        public AdaptiveSamplingStrategy(int targetSpansPerSecond, double minRatio, double maxRatio) {
            this.targetSpansPerSecond = targetSpansPerSecond;
            this.minRatio = minRatio;
            this.maxRatio = maxRatio;
            this.currentRatio = maxRatio; // 初始使用最大比例
        }
        
        @Override
        public SamplingResult shouldSample(Context parentContext, String traceId, String name,
                                         SpanKind spanKind, Attributes attributes,
                                         List<LinkData> parentLinks) {
            long currentTime = System.currentTimeMillis();
            spanCount++;
            
            // 每秒调整一次采样率
            if (currentTime - lastSampleTime >= 1000) {
                synchronized (this) {
                    if (currentTime - lastSampleTime >= 1000) {
                        // 根据当前跨度计数调整采样率
                        if (spanCount > targetSpansPerSecond) {
                            currentRatio = Math.max(minRatio, currentRatio * 0.9);
                        } else if (spanCount < targetSpansPerSecond * 0.8) {
                            currentRatio = Math.min(maxRatio, currentRatio * 1.1);
                        }
                        
                        spanCount = 0;
                        lastSampleTime = currentTime;
                        log.debug("自适应采样率调整为: {}", currentRatio);
                    }
                }
            }
            
            boolean shouldSample = Math.random() < currentRatio;
            if (shouldSample) {
                return SamplingResult.recordAndSample();
            } else {
                return SamplingResult.drop();
            }
        }
        
        @Override
        public String getDescription() {
            return "AdaptiveSampler{target=" + targetSpansPerSecond + 
                   ", currentRatio=" + currentRatio + "}";
        }
    }
}
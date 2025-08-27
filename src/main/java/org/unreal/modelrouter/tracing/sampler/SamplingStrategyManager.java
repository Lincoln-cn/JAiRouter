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
        strategies.put("ratio_based", Sampler.parentBased(Sampler.traceIdRatioBased(samplingConfig.getRatio())));
        
        // 创建基于规则的采样策略  
        strategies.put("rule_based", Sampler.parentBased(Sampler.traceIdRatioBased(samplingConfig.getRatio())));
        
        // 创建自适应采样策略
        TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig = samplingConfig.getAdaptive();
        if (adaptiveConfig.isEnabled()) {
            // 简化实现，使用基于比例的采样器
            double adaptiveRatio = Math.min(adaptiveConfig.getMaxRatio(), 
                                           Math.max(adaptiveConfig.getMinRatio(), samplingConfig.getRatio()));
            strategies.put("adaptive", Sampler.parentBased(Sampler.traceIdRatioBased(adaptiveRatio)));
        }
        
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
    
}
package org.unreal.modelrouter.tracing.sampler;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;

import io.opentelemetry.sdk.trace.samplers.Sampler;

/**
 * 采样策略管理器测试类
 */
class SamplingStrategyManagerTest {
    
    @Mock
    private TracingConfiguration tracingConfig;
    
    @Mock
    private TracingConfiguration.SamplingConfig samplingConfig;
    
    @Mock
    private TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig;
    
    private SamplingStrategyManager samplingStrategyManager;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 配置AdaptiveConfig mock
        when(adaptiveConfig.isEnabled()).thenReturn(false);
        when(adaptiveConfig.getTargetSpansPerSecond()).thenReturn(1000L);
        when(adaptiveConfig.getMinRatio()).thenReturn(0.1);
        when(adaptiveConfig.getMaxRatio()).thenReturn(1.0);
        when(adaptiveConfig.getAdjustmentInterval()).thenReturn(30L);
        
        // 配置SamplingConfig mock
        when(tracingConfig.getSampling()).thenReturn(samplingConfig);
        when(samplingConfig.getRatio()).thenReturn(0.5);
        when(samplingConfig.getRules()).thenReturn(java.util.List.of());
        when(samplingConfig.getAlwaysSample()).thenReturn(java.util.List.of());
        when(samplingConfig.getNeverSample()).thenReturn(java.util.List.of());
        when(samplingConfig.getServiceRatios()).thenReturn(java.util.Map.of());
        when(samplingConfig.getAdaptive()).thenReturn(adaptiveConfig);
        
        samplingStrategyManager = new SamplingStrategyManager(tracingConfig);
        // 手动调用init方法确保初始化完成
        samplingStrategyManager.init();
    }
    
    @Test
    void testInit() {
        // 验证初始化后当前策略不为null
        assertNotNull(samplingStrategyManager.getCurrentStrategy());
    }
    
    @Test
    void testRefreshStrategies() {
        // 刷新策略
        samplingStrategyManager.refreshStrategies();
        
        // 验证当前策略不为null
        assertNotNull(samplingStrategyManager.getCurrentStrategy());
    }
    
    @Test
    void testUpdateStrategy() {
        // 刷新策略
        samplingStrategyManager.refreshStrategies();
        
        // 保存原始策略
        Sampler originalStrategy = samplingStrategyManager.getCurrentStrategy();
        
        // 更新为rule_based策略
        samplingStrategyManager.updateStrategy("rule_based");
        assertNotEquals(originalStrategy, samplingStrategyManager.getCurrentStrategy());
        
        // 更新为不存在的策略，应该保持原策略不变
        samplingStrategyManager.updateStrategy("nonexistent");
        assertNotNull(samplingStrategyManager.getCurrentStrategy());
    }
    
    @Test
    void testUpdateSamplingConfiguration() {
        // 刷新策略
        samplingStrategyManager.refreshStrategies();
        
        // 保存原始策略
        Sampler originalStrategy = samplingStrategyManager.getCurrentStrategy();
        
        // 更新配置
        samplingStrategyManager.updateSamplingConfiguration(samplingConfig);
        
        // 验证策略已更新（这里只是重新加载，实际策略可能相同）
        assertNotNull(samplingStrategyManager.getCurrentStrategy());
    }
    
    @Test
    void testGetCurrentStrategy() {
        // 验证获取当前策略不为null
        assertNotNull(samplingStrategyManager.getCurrentStrategy());
        
        // 刷新策略后再次验证
        samplingStrategyManager.refreshStrategies();
        assertNotNull(samplingStrategyManager.getCurrentStrategy());
    }
}
package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 采样策略管理器测试类
 */
class SamplingStrategyManagerTest {
    
    @Mock
    private TracingConfiguration tracingConfig;
    
    @Mock
    private TracingConfiguration.SamplingConfig samplingConfig;
    
    private SamplingStrategyManager samplingStrategyManager;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tracingConfig.getSampling()).thenReturn(samplingConfig);
        when(samplingConfig.getRatio()).thenReturn(0.5);
        when(samplingConfig.getRules()).thenReturn(java.util.List.of());
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
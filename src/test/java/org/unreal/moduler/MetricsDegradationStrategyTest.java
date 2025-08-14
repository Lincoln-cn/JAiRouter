package org.unreal.moduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.MetricsDegradationStrategy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsDegradationStrategy 单元测试
 */
class MetricsDegradationStrategyTest {

    private MeterRegistry meterRegistry;
    private MetricsDegradationStrategy degradationStrategy;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        degradationStrategy = new MetricsDegradationStrategy(meterRegistry);
    }

    @Test
    void testInitialState() {
        assertEquals(MetricsDegradationStrategy.DegradationLevel.NONE, 
                    degradationStrategy.getCurrentDegradationLevel());
        assertEquals(1.0, degradationStrategy.getCurrentSamplingRate());
        assertTrue(degradationStrategy.shouldCollectMetrics());
        assertTrue(degradationStrategy.isAutoModeEnabled());
    }

    @Test
    void testSetDegradationLevel() {
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.LIGHT);
        
        assertEquals(MetricsDegradationStrategy.DegradationLevel.LIGHT, 
                    degradationStrategy.getCurrentDegradationLevel());
        assertEquals(0.5, degradationStrategy.getCurrentSamplingRate());
    }

    @Test
    void testShouldCollectMetricsBasedOnLevel() {
        // 正常模式 - 应该总是收集
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.NONE);
        assertTrue(degradationStrategy.shouldCollectMetrics());

        // 紧急模式 - 应该不收集
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.EMERGENCY);
        assertFalse(degradationStrategy.shouldCollectMetrics());
    }

    @Test
    void testAutoModeEvaluation() {
        // 低内存使用率和错误数 - 应该保持正常
        degradationStrategy.evaluateAndAdjustDegradation(0.5, 2);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.NONE, 
                    degradationStrategy.getCurrentDegradationLevel());

        // 高内存使用率 - 应该降级
        degradationStrategy.evaluateAndAdjustDegradation(0.85, 2);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.MODERATE, 
                    degradationStrategy.getCurrentDegradationLevel());

        // 极高内存使用率 - 应该紧急降级
        degradationStrategy.evaluateAndAdjustDegradation(0.96, 2);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.EMERGENCY, 
                    degradationStrategy.getCurrentDegradationLevel());
    }

    @Test
    void testErrorBasedDegradation() {
        // 高错误数 - 应该降级
        degradationStrategy.evaluateAndAdjustDegradation(0.5, 20);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.MODERATE, 
                    degradationStrategy.getCurrentDegradationLevel());

        // 极高错误数 - 应该紧急降级
        degradationStrategy.evaluateAndAdjustDegradation(0.5, 60);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.EMERGENCY, 
                    degradationStrategy.getCurrentDegradationLevel());
    }

    @Test
    void testComponentSpecificErrors() {
        String component = "test-component";
        
        // 记录组件错误
        for (int i = 0; i < 15; i++) {
            degradationStrategy.recordComponentError(component);
        }

        // 高错误组件应该有更低的采样率
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.NONE);
        
        // 由于随机性，我们测试多次来验证趋势
        int collectCount = 0;
        int totalTests = 1000;
        for (int i = 0; i < totalTests; i++) {
            if (degradationStrategy.shouldCollectMetrics(component)) {
                collectCount++;
            }
        }
        
        // 高错误组件的采样率应该很低（约10%）
        double actualRate = (double) collectCount / totalTests;
        assertTrue(actualRate < 0.2, "Expected low sampling rate for high-error component, got: " + actualRate);
    }

    @Test
    void testResetComponentErrors() {
        String component = "test-component";
        
        // 记录错误
        for (int i = 0; i < 15; i++) {
            degradationStrategy.recordComponentError(component);
        }

        // 重置错误
        degradationStrategy.resetComponentErrors(component);

        // 重置后应该正常收集
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.NONE);
        assertTrue(degradationStrategy.shouldCollectMetrics(component));
    }

    @Test
    void testAutoModeToggle() {
        assertTrue(degradationStrategy.isAutoModeEnabled());

        // 禁用自动模式
        degradationStrategy.setAutoModeEnabled(false);
        assertFalse(degradationStrategy.isAutoModeEnabled());

        // 自动评估不应该改变级别
        MetricsDegradationStrategy.DegradationLevel initialLevel = degradationStrategy.getCurrentDegradationLevel();
        degradationStrategy.evaluateAndAdjustDegradation(0.96, 60);
        assertEquals(initialLevel, degradationStrategy.getCurrentDegradationLevel());

        // 重新启用自动模式
        degradationStrategy.setAutoModeEnabled(true);
        degradationStrategy.evaluateAndAdjustDegradation(0.96, 60);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.EMERGENCY, 
                    degradationStrategy.getCurrentDegradationLevel());
    }

    @Test
    void testForceRecovery() {
        // 设置为紧急降级
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.EMERGENCY);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.EMERGENCY, 
                    degradationStrategy.getCurrentDegradationLevel());

        // 强制恢复
        degradationStrategy.forceRecovery();
        assertEquals(MetricsDegradationStrategy.DegradationLevel.NONE, 
                    degradationStrategy.getCurrentDegradationLevel());
    }

    @Test
    void testDegradationStatus() {
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.MODERATE);
        degradationStrategy.recordComponentError("component1");
        degradationStrategy.recordComponentError("component2");

        MetricsDegradationStrategy.DegradationStatus status = degradationStrategy.getDegradationStatus();
        
        assertEquals(MetricsDegradationStrategy.DegradationLevel.MODERATE, status.getLevel());
        assertEquals(0.2, status.getSamplingRate());
        assertTrue(status.isAutoModeEnabled());
        assertEquals(2, status.getErrorComponentCount());
        assertNotNull(status.getTimeSinceLastChange());
    }

    @Test
    void testSamplingRateConsistency() {
        for (MetricsDegradationStrategy.DegradationLevel level : MetricsDegradationStrategy.DegradationLevel.values()) {
            degradationStrategy.setDegradationLevel(level);
            assertEquals(level.getSamplingRate(), degradationStrategy.getCurrentSamplingRate());
        }
    }

    @Test
    void testDegradationLevelProgression() {
        // 测试基于内存使用率的渐进降级
        degradationStrategy.evaluateAndAdjustDegradation(0.75, 0);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.LIGHT, 
                    degradationStrategy.getCurrentDegradationLevel());

        degradationStrategy.evaluateAndAdjustDegradation(0.85, 0);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.MODERATE, 
                    degradationStrategy.getCurrentDegradationLevel());

        degradationStrategy.evaluateAndAdjustDegradation(0.92, 0);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.HEAVY, 
                    degradationStrategy.getCurrentDegradationLevel());

        degradationStrategy.evaluateAndAdjustDegradation(0.96, 0);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.EMERGENCY, 
                    degradationStrategy.getCurrentDegradationLevel());
    }
}
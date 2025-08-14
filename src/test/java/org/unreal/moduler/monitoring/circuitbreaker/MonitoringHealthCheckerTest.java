package org.unreal.moduler.monitoring.circuitbreaker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.monitoring.AsyncMetricsProcessor;
import org.unreal.modelrouter.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitoring.circuitbreaker.*;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MonitoringHealthChecker 单元测试
 */
class MonitoringHealthCheckerTest {

    private MonitoringHealthChecker healthChecker;
    private MeterRegistry meterRegistry;

    @Mock
    private MetricsErrorHandler errorHandler;

    @Mock
    private MetricsDegradationStrategy degradationStrategy;

    @Mock
    private MetricsCacheAndRetry cacheAndRetry;

    @Mock
    private AsyncMetricsProcessor asyncProcessor;

    @Mock
    private MetricsMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        
        healthChecker = new MonitoringHealthChecker(
            meterRegistry,
            errorHandler,
            degradationStrategy,
            cacheAndRetry,
            asyncProcessor,
            memoryManager
        );
    }

    @Test
    void testHealthySystemCheck() {
        // 模拟健康的系统状态
        when(errorHandler.getErrorStats()).thenReturn(
            new MetricsErrorHandler.MetricsErrorStats(0, 0, 0)
        );
        
        when(degradationStrategy.getDegradationStatus()).thenReturn(
            new MetricsDegradationStrategy.DegradationStatus(
                MetricsDegradationStrategy.DegradationLevel.NONE,
                1.0,
                true,
                java.time.Duration.ofMinutes(1),
                0
            )
        );
        
        when(cacheAndRetry.getCacheStats()).thenReturn(
            new MetricsCacheAndRetry.CacheStats(100, 1000, 0, 0)
        );
        
        when(asyncProcessor.getStats()).thenReturn(
            new AsyncMetricsProcessor.ProcessingStats(1000, 0, 10, "CLOSED")
        );
        
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.1, 100L, 1000L, 50, 100, 10, 0, 0)
        );

        // 执行健康检查
        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        // 验证结果
        assertTrue(result.isHealthy());
        assertNotNull(result.getDetails());
        assertTrue(result.getDetails().containsKey("summary"));
    }

    @Test
    void testUnhealthySystemCheck() {
        // 模拟不健康的系统状态
        when(errorHandler.getErrorStats()).thenReturn(
            new MetricsErrorHandler.MetricsErrorStats(10, 8, 50) // 过多降级组件
        );
        
        when(degradationStrategy.getDegradationStatus()).thenReturn(
            new MetricsDegradationStrategy.DegradationStatus(
                MetricsDegradationStrategy.DegradationLevel.EMERGENCY, // 紧急降级
                0.0,
                true,
                java.time.Duration.ofMinutes(1),
                10
            )
        );
        
        when(cacheAndRetry.getCacheStats()).thenReturn(
            new MetricsCacheAndRetry.CacheStats(950, 1000, 200, 50) // 缓存使用率过高
        );
        
        when(asyncProcessor.getStats()).thenReturn(
            new AsyncMetricsProcessor.ProcessingStats(1000, 500, 1500, "OPEN") // 队列积压
        );
        
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.95, 950L, 1000L, 500, 100, 10, 50, 10) // 内存使用率过高
        );

        // 执行健康检查
        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        // 验证结果
        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
        assertNotNull(result.getIssues());
        assertFalse(result.getIssues().isEmpty());
    }

    @Test
    void testPartiallyHealthySystem() {
        // 模拟部分组件不健康的状态
        when(errorHandler.getErrorStats()).thenReturn(
            new MetricsErrorHandler.MetricsErrorStats(2, 1, 5) // 少量错误
        );
        
        when(degradationStrategy.getDegradationStatus()).thenReturn(
            new MetricsDegradationStrategy.DegradationStatus(
                MetricsDegradationStrategy.DegradationLevel.LIGHT, // 轻度降级
                0.5,
                true,
                java.time.Duration.ofMinutes(1),
                2
            )
        );
        
        when(cacheAndRetry.getCacheStats()).thenReturn(
            new MetricsCacheAndRetry.CacheStats(500, 1000, 5, 2) // 正常缓存状态
        );
        
        when(asyncProcessor.getStats()).thenReturn(
            new AsyncMetricsProcessor.ProcessingStats(500, 10, 100, "CLOSED") // 正常处理状态
        );
        
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.7, 700L, 1000L, 200, 150, 20, 5, 2) // 正常内存状态
        );

        // 执行健康检查
        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        // 验证结果 - 轻度降级不算不健康
        assertTrue(result.isHealthy());
        assertNotNull(result.getDetails());
    }

    @Test
    void testHealthCheckException() {
        // 模拟健康检查过程中的异常
        when(errorHandler.getErrorStats()).thenThrow(new RuntimeException("Error handler failure"));

        // 执行健康检查
        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        // 验证结果
        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
        assertTrue(result.getIssues().contains("Health check exception"));
    }

    @Test
    void testHealthCheckDetailsStructure() {
        // 模拟正常状态
        when(errorHandler.getErrorStats()).thenReturn(
            new MetricsErrorHandler.MetricsErrorStats(0, 0, 0)
        );
        
        when(degradationStrategy.getDegradationStatus()).thenReturn(
            new MetricsDegradationStrategy.DegradationStatus(
                MetricsDegradationStrategy.DegradationLevel.NONE,
                1.0,
                true,
                java.time.Duration.ofMinutes(1),
                0
            )
        );
        
        when(cacheAndRetry.getCacheStats()).thenReturn(
            new MetricsCacheAndRetry.CacheStats(100, 1000, 0, 0)
        );
        
        when(asyncProcessor.getStats()).thenReturn(
            new AsyncMetricsProcessor.ProcessingStats(1000, 0, 10, "CLOSED")
        );
        
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.1, 100L, 1000L, 50, 100, 10, 0, 0)
        );

        // 执行健康检查
        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        // 验证详细信息结构
        assertNotNull(result.getDetails());
        assertTrue(result.getDetails().containsKey("errorHandler"));
        assertTrue(result.getDetails().containsKey("degradationStrategy"));
        assertTrue(result.getDetails().containsKey("cacheAndRetry"));
        assertTrue(result.getDetails().containsKey("asyncProcessor"));
        assertTrue(result.getDetails().containsKey("memoryManager"));
        assertTrue(result.getDetails().containsKey("meterRegistry"));
        assertTrue(result.getDetails().containsKey("summary"));
    }

    @Test
    void testMeterRegistryHealthCheck() {
        // 添加一些指标到注册表
        meterRegistry.counter("jairouter.test.counter").increment();
        meterRegistry.gauge("jairouter.test.gauge", 42.0);

        // 模拟其他组件正常状态
        when(errorHandler.getErrorStats()).thenReturn(
            new MetricsErrorHandler.MetricsErrorStats(0, 0, 0)
        );
        
        when(degradationStrategy.getDegradationStatus()).thenReturn(
            new MetricsDegradationStrategy.DegradationStatus(
                MetricsDegradationStrategy.DegradationLevel.NONE,
                1.0,
                true,
                java.time.Duration.ofMinutes(1),
                0
            )
        );
        
        when(cacheAndRetry.getCacheStats()).thenReturn(
            new MetricsCacheAndRetry.CacheStats(100, 1000, 0, 0)
        );
        
        when(asyncProcessor.getStats()).thenReturn(
            new AsyncMetricsProcessor.ProcessingStats(1000, 0, 10, "CLOSED")
        );
        
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.1, 100L, 1000L, 50, 100, 10, 0, 0)
        );

        // 执行健康检查
        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        // 验证MeterRegistry检查结果
        assertTrue(result.isHealthy());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> registryDetails = 
            (java.util.Map<String, Object>) result.getDetails().get("meterRegistry");
        
        assertNotNull(registryDetails);
        assertTrue((Integer) registryDetails.get("meterCount") > 0);
        assertTrue((Boolean) registryDetails.get("hasJaiRouterMetrics"));
        assertEquals("UP", registryDetails.get("status"));
    }
}
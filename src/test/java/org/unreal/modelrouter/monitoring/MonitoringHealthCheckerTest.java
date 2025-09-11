package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.monitoring.*;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MonitoringHealthChecker 单元测试
 */
class MonitoringHealthCheckerTest {

    private MeterRegistry meterRegistry;
    private MonitoringHealthChecker healthChecker;

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
            meterRegistry, errorHandler, degradationStrategy, 
            cacheAndRetry, asyncProcessor, memoryManager
        );
    }

    @Test
    void testHealthySystem() {
        // 模拟健康的系统状态
        setupHealthyMocks();
        
        // 添加JAiRouter指标到注册表以满足健康检查
        meterRegistry.counter("jairouter.test.metric").increment();

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertTrue(result.isHealthy());
        assertNotNull(result.getDetails());
        assertTrue(result.getDetails().containsKey("summary"));
    }

    @Test
    void testUnhealthySystemDueToHighMemory() {
        // 模拟高内存使用率
        setupHealthyMocks();
        
        MetricsMemoryManager.MemoryStats memoryStats = mock(MetricsMemoryManager.MemoryStats.class);
        when(memoryStats.getMemoryUsageRatio()).thenReturn(0.95); // 95% 内存使用率
        when(memoryStats.getUsedMemory()).thenReturn(950L);
        when(memoryStats.getMaxMemory()).thenReturn(1000L);
        when(memoryStats.getCacheSize()).thenReturn(100);
        when(memoryStats.getMemoryCleanups()).thenReturn(5L);
        when(memoryManager.getMemoryStats()).thenReturn(memoryStats);

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
    }

    @Test
    void testUnhealthySystemDueToEmergencyDegradation() {
        // 模拟紧急降级状态
        setupHealthyMocks();
        
        MetricsDegradationStrategy.DegradationStatus degradationStatus = 
            mock(MetricsDegradationStrategy.DegradationStatus.class);
        when(degradationStatus.getLevel()).thenReturn(MetricsDegradationStrategy.DegradationLevel.EMERGENCY);
        when(degradationStatus.getSamplingRate()).thenReturn(0.0);
        when(degradationStatus.isAutoModeEnabled()).thenReturn(true);
        when(degradationStatus.getTimeSinceLastChange()).thenReturn(Duration.ofMinutes(5));
        when(degradationStatus.getErrorComponentCount()).thenReturn(2);
        when(degradationStrategy.getDegradationStatus()).thenReturn(degradationStatus);

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
    }

    @Test
    void testUnhealthySystemDueToDegradedComponents() {
        // 模拟过多降级组件
        setupHealthyMocks();
        
        MetricsErrorHandler.MetricsErrorStats errorStats = 
            mock(MetricsErrorHandler.MetricsErrorStats.class);
        when(errorStats.getActiveErrorComponents()).thenReturn(3);
        when(errorStats.getDegradedComponents()).thenReturn(8); // 超过阈值
        when(errorStats.getTotalErrors()).thenReturn(50);
        when(errorHandler.getErrorStats()).thenReturn(errorStats);

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
    }

    @Test
    void testUnhealthySystemDueToHighCacheUsage() {
        // 模拟高缓存使用率
        setupHealthyMocks();
        
        MetricsCacheAndRetry.CacheStats cacheStats = mock(MetricsCacheAndRetry.CacheStats.class);
        when(cacheStats.getCurrentSize()).thenReturn(9500);
        when(cacheStats.getMaxSize()).thenReturn(10000);
        when(cacheStats.getUsageRatio()).thenReturn(0.95); // 95% 缓存使用率
        when(cacheStats.getActiveRetries()).thenReturn(150); // 过多重试
        when(cacheStats.getQueuedRetries()).thenReturn(50);
        when(cacheAndRetry.getCacheStats()).thenReturn(cacheStats);

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
    }

    @Test
    void testUnhealthySystemDueToProcessingDelay() {
        // 模拟处理延迟过高
        setupHealthyMocks();
        
        AsyncMetricsProcessor.ProcessingStats processorStats = 
            mock(AsyncMetricsProcessor.ProcessingStats.class);
        when(processorStats.getQueueSize()).thenReturn(500L);
        when(processorStats.getProcessedCount()).thenReturn(10000L);
        when(processorStats.getDroppedCount()).thenReturn(2000L); // 高丢弃率
        when(processorStats.getCircuitBreakerState()).thenReturn("CLOSED");
        when(asyncProcessor.getStats()).thenReturn(processorStats);

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
    }

    @Test
    void testHealthCheckException() {
        // 模拟健康检查过程中的异常
        when(errorHandler.getErrorStats()).thenThrow(new RuntimeException("Test exception"));
        
        // 添加JAiRouter指标以满足MeterRegistry检查
        meterRegistry.counter("jairouter.test.metric").increment();
        
        // 设置其他组件为健康状态
        setupOtherHealthyMocks();

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertFalse(result.isHealthy());
        assertTrue(result.getIssues().contains("ErrorHandler"));
        
        // 检查错误处理器的详细信息包含错误
        @SuppressWarnings("unchecked")
        var errorHandlerDetails = (java.util.Map<String, Object>) result.getDetails().get("errorHandler");
        assertTrue(errorHandlerDetails.containsKey("error"));
    }

    @Test
    void testSummaryInformation() {
        setupHealthyMocks();

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertNotNull(result.getDetails().get("summary"));
        @SuppressWarnings("unchecked")
        var summary = (java.util.Map<String, Object>) result.getDetails().get("summary");
        
        assertTrue(summary.containsKey("totalComponents"));
        assertTrue(summary.containsKey("healthyComponents"));
        assertTrue(summary.containsKey("healthRatio"));
        assertTrue(summary.containsKey("overallStatus"));
        assertTrue(summary.containsKey("checkTime"));
    }

    @Test
    void testMeterRegistryHealthCheck() {
        setupHealthyMocks();
        
        // 添加一些JAiRouter指标到注册表
        meterRegistry.counter("jairouter.test.metric").increment();

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        assertTrue(result.isHealthy());
        
        @SuppressWarnings("unchecked")
        var registryDetails = (java.util.Map<String, Object>) result.getDetails().get("meterRegistry");
        assertTrue((Boolean) registryDetails.get("hasJaiRouterMetrics"));
        assertTrue((Integer) registryDetails.get("meterCount") > 0);
    }

    @Test
    void testPartiallyHealthySystem() {
        // 设置部分组件健康，部分不健康
        setupPartiallyHealthyMocks();

        MonitoringHealthChecker.HealthCheckResult result = healthChecker.performHealthCheck();

        // 系统应该是不健康状态，但有详细信息
        assertFalse(result.isHealthy());
        assertNotNull(result.getDetails());
        
        @SuppressWarnings("unchecked")
        var summary = (java.util.Map<String, Object>) result.getDetails().get("summary");
        assertEquals("DEGRADED", summary.get("overallStatus"));
    }

    private void setupHealthyMocks() {
        // 添加JAiRouter指标到注册表
        meterRegistry.counter("jairouter.test.metric").increment();
        // 错误处理器 - 健康状态
        MetricsErrorHandler.MetricsErrorStats errorStats = 
            mock(MetricsErrorHandler.MetricsErrorStats.class);
        when(errorStats.getActiveErrorComponents()).thenReturn(2);
        when(errorStats.getDegradedComponents()).thenReturn(1);
        when(errorStats.getTotalErrors()).thenReturn(5);
        when(errorHandler.getErrorStats()).thenReturn(errorStats);

        // 降级策略 - 正常状态
        MetricsDegradationStrategy.DegradationStatus degradationStatus = 
            mock(MetricsDegradationStrategy.DegradationStatus.class);
        when(degradationStatus.getLevel()).thenReturn(MetricsDegradationStrategy.DegradationLevel.NONE);
        when(degradationStatus.getSamplingRate()).thenReturn(1.0);
        when(degradationStatus.isAutoModeEnabled()).thenReturn(true);
        when(degradationStatus.getTimeSinceLastChange()).thenReturn(Duration.ofMinutes(5));
        when(degradationStatus.getErrorComponentCount()).thenReturn(2);
        when(degradationStrategy.getDegradationStatus()).thenReturn(degradationStatus);

        // 缓存和重试 - 正常状态
        MetricsCacheAndRetry.CacheStats cacheStats = mock(MetricsCacheAndRetry.CacheStats.class);
        when(cacheStats.getCurrentSize()).thenReturn(500);
        when(cacheStats.getMaxSize()).thenReturn(10000);
        when(cacheStats.getUsageRatio()).thenReturn(0.05);
        when(cacheStats.getActiveRetries()).thenReturn(5);
        when(cacheStats.getQueuedRetries()).thenReturn(2);
        when(cacheAndRetry.getCacheStats()).thenReturn(cacheStats);

        // 异步处理器 - 正常状态
        AsyncMetricsProcessor.ProcessingStats processorStats = 
            mock(AsyncMetricsProcessor.ProcessingStats.class);
        when(processorStats.getQueueSize()).thenReturn(100L);
        when(processorStats.getProcessedCount()).thenReturn(10000L);
        when(processorStats.getDroppedCount()).thenReturn(5L);
        when(processorStats.getCircuitBreakerState()).thenReturn("CLOSED");
        when(asyncProcessor.getStats()).thenReturn(processorStats);

        // 内存管理器 - 正常状态
        MetricsMemoryManager.MemoryStats memoryStats = mock(MetricsMemoryManager.MemoryStats.class);
        when(memoryStats.getUsedMemory()).thenReturn(500L);
        when(memoryStats.getMaxMemory()).thenReturn(1000L);
        when(memoryStats.getMemoryUsageRatio()).thenReturn(0.5);
        when(memoryStats.getCacheSize()).thenReturn(100);
        when(memoryStats.getMemoryCleanups()).thenReturn(5L);
        when(memoryManager.getMemoryStats()).thenReturn(memoryStats);
    }

    private void setupPartiallyHealthyMocks() {
        setupHealthyMocks();
        
        // 让内存管理器不健康
        MetricsMemoryManager.MemoryStats memoryStats = mock(MetricsMemoryManager.MemoryStats.class);
        when(memoryStats.getUsedMemory()).thenReturn(950L);
        when(memoryStats.getMaxMemory()).thenReturn(1000L);
        when(memoryStats.getMemoryUsageRatio()).thenReturn(0.95); // 不健康的内存使用率
        when(memoryStats.getCacheSize()).thenReturn(100);
        when(memoryStats.getMemoryCleanups()).thenReturn(5L);
        when(memoryManager.getMemoryStats()).thenReturn(memoryStats);
    }
    
    private void setupOtherHealthyMocks() {
        // 降级策略 - 正常状态
        MetricsDegradationStrategy.DegradationStatus degradationStatus = 
            mock(MetricsDegradationStrategy.DegradationStatus.class);
        when(degradationStatus.getLevel()).thenReturn(MetricsDegradationStrategy.DegradationLevel.NONE);
        when(degradationStatus.getSamplingRate()).thenReturn(1.0);
        when(degradationStatus.isAutoModeEnabled()).thenReturn(true);
        when(degradationStatus.getTimeSinceLastChange()).thenReturn(Duration.ofMinutes(5));
        when(degradationStatus.getErrorComponentCount()).thenReturn(2);
        when(degradationStrategy.getDegradationStatus()).thenReturn(degradationStatus);

        // 缓存和重试 - 正常状态
        MetricsCacheAndRetry.CacheStats cacheStats = mock(MetricsCacheAndRetry.CacheStats.class);
        when(cacheStats.getCurrentSize()).thenReturn(500);
        when(cacheStats.getMaxSize()).thenReturn(10000);
        when(cacheStats.getUsageRatio()).thenReturn(0.05);
        when(cacheStats.getActiveRetries()).thenReturn(5);
        when(cacheStats.getQueuedRetries()).thenReturn(2);
        when(cacheAndRetry.getCacheStats()).thenReturn(cacheStats);

        // 异步处理器 - 正常状态
        AsyncMetricsProcessor.ProcessingStats processorStats = 
            mock(AsyncMetricsProcessor.ProcessingStats.class);
        when(processorStats.getQueueSize()).thenReturn(100L);
        when(processorStats.getProcessedCount()).thenReturn(10000L);
        when(processorStats.getDroppedCount()).thenReturn(5L);
        when(processorStats.getCircuitBreakerState()).thenReturn("CLOSED");
        when(asyncProcessor.getStats()).thenReturn(processorStats);

        // 内存管理器 - 正常状态
        MetricsMemoryManager.MemoryStats memoryStats = mock(MetricsMemoryManager.MemoryStats.class);
        when(memoryStats.getUsedMemory()).thenReturn(500L);
        when(memoryStats.getMaxMemory()).thenReturn(1000L);
        when(memoryStats.getMemoryUsageRatio()).thenReturn(0.5);
        when(memoryStats.getCacheSize()).thenReturn(100);
        when(memoryStats.getMemoryCleanups()).thenReturn(5L);
        when(memoryManager.getMemoryStats()).thenReturn(memoryStats);
    }
}
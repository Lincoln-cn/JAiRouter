package org.unreal.moduler.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.controller.MonitoringController;
import org.unreal.modelrouter.monitoring.AsyncMetricsProcessor;
import org.unreal.modelrouter.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitoring.circuitbreaker.*;
import org.unreal.modelrouter.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MonitoringController 错误处理功能测试
 */
class MonitoringControllerErrorHandlingTest {

    private MonitoringController controller;
    private MeterRegistry meterRegistry;
    private MetricsErrorHandler errorHandler;
    private MetricsDegradationStrategy degradationStrategy;
    private MetricsCacheAndRetry cacheAndRetry;
    private MetricsCircuitBreaker circuitBreaker;
    private MonitoringHealthChecker healthChecker;

    @Mock
    private MonitoringProperties monitoringProperties;

    @Mock
    private DynamicMonitoringConfigUpdater configUpdater;

    @Mock
    private AsyncMetricsProcessor asyncProcessor;

    @Mock
    private MetricsMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        meterRegistry = new SimpleMeterRegistry();
        errorHandler = new MetricsErrorHandler(meterRegistry);
        degradationStrategy = new MetricsDegradationStrategy(meterRegistry);
        cacheAndRetry = new MetricsCacheAndRetry(meterRegistry);
        circuitBreaker = new MetricsCircuitBreaker();
        
        healthChecker = new MonitoringHealthChecker(
            meterRegistry,
            errorHandler,
            degradationStrategy,
            cacheAndRetry,
            asyncProcessor,
            memoryManager
        );
        
        controller = new MonitoringController(
            monitoringProperties,
            configUpdater,
            errorHandler,
            healthChecker,
            cacheAndRetry,
            degradationStrategy,
            circuitBreaker
        );
    }

    @Test
    void testGetHealthStatus() {
        // 模拟健康的系统状态
        when(asyncProcessor.getStats()).thenReturn(
            new AsyncMetricsProcessor.ProcessingStats(1000, 0, 10, "CLOSED")
        );
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.1, 100L, 1000L, 50, 100, 10, 0, 0)
        );

        ResponseEntity<Map<String, Object>> response = controller.getHealthStatus();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("status"));
        assertTrue(response.getBody().containsKey("healthy"));
        assertTrue(response.getBody().containsKey("details"));
    }

    @Test
    void testGetErrorStats() {
        // 触发一些错误
        errorHandler.handleMetricsError("test-component", "test-operation", 
            new RuntimeException("Test error"));

        ResponseEntity<Map<String, Object>> response = controller.getErrorStats();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("activeErrorComponents"));
        assertTrue(response.getBody().containsKey("degradedComponents"));
        assertTrue(response.getBody().containsKey("totalErrors"));
        
        assertEquals(1, response.getBody().get("totalErrors"));
    }

    @Test
    void testResetErrorState() {
        // 首先触发错误
        for (int i = 0; i < 12; i++) {
            errorHandler.handleMetricsError("test-component", "test-operation", 
                new RuntimeException("Test error"));
        }
        assertTrue(errorHandler.isDegraded("test-component", "test-operation"));

        // 重置错误状态
        Map<String, String> request = Map.of(
            "component", "test-component",
            "operation", "test-operation"
        );

        ResponseEntity<String> response = controller.resetErrorState(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("错误状态已重置"));
        assertFalse(errorHandler.isDegraded("test-component", "test-operation"));
    }

    @Test
    void testGetDegradationStatus() {
        ResponseEntity<Map<String, Object>> response = controller.getDegradationStatus();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("level"));
        assertTrue(response.getBody().containsKey("levelDescription"));
        assertTrue(response.getBody().containsKey("samplingRate"));
        assertTrue(response.getBody().containsKey("autoModeEnabled"));
    }

    @Test
    void testSetDegradationLevel() {
        Map<String, String> request = Map.of("level", "LIGHT");

        ResponseEntity<String> response = controller.setDegradationLevel(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("降级级别已设置为"));
        assertEquals(MetricsDegradationStrategy.DegradationLevel.LIGHT, 
                    degradationStrategy.getCurrentDegradationLevel());
    }

    @Test
    void testSetAutoMode() {
        Map<String, Boolean> request = Map.of("enabled", false);

        ResponseEntity<String> response = controller.setAutoMode(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("降级策略自动模式已禁用"));
        assertFalse(degradationStrategy.isAutoModeEnabled());
    }

    @Test
    void testForceRecovery() {
        // 首先设置降级状态
        degradationStrategy.setDegradationLevel(MetricsDegradationStrategy.DegradationLevel.HEAVY);
        assertEquals(MetricsDegradationStrategy.DegradationLevel.HEAVY, 
                    degradationStrategy.getCurrentDegradationLevel());

        ResponseEntity<String> response = controller.forceRecovery();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("已强制恢复到正常模式"));
        assertEquals(MetricsDegradationStrategy.DegradationLevel.NONE, 
                    degradationStrategy.getCurrentDegradationLevel());
    }

    @Test
    void testGetCacheStats() {
        // 添加一些缓存数据
        cacheAndRetry.cacheMetric("test-key", "test-value", java.time.Duration.ofMinutes(1));

        ResponseEntity<Map<String, Object>> response = controller.getCacheStats();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("currentSize"));
        assertTrue(response.getBody().containsKey("maxSize"));
        assertTrue(response.getBody().containsKey("usageRatio"));
        assertTrue(response.getBody().containsKey("activeRetries"));
        assertTrue(response.getBody().containsKey("queuedRetries"));
        
        assertEquals(1, response.getBody().get("currentSize"));
    }

    @Test
    void testClearCache() {
        // 添加缓存数据
        cacheAndRetry.cacheMetric("test-key", "test-value", java.time.Duration.ofMinutes(1));
        assertEquals(1, cacheAndRetry.getCacheStats().getCurrentSize());

        ResponseEntity<String> response = controller.clearCache();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("缓存已清空"));
        assertEquals(0, cacheAndRetry.getCacheStats().getCurrentSize());
    }

    @Test
    void testGetCircuitBreakerStats() {
        // 记录一些统计信息
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();

        ResponseEntity<Map<String, Object>> response = controller.getCircuitBreakerStats();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("state"));
        assertTrue(response.getBody().containsKey("failureCount"));
        assertTrue(response.getBody().containsKey("successCount"));
        assertTrue(response.getBody().containsKey("requestCount"));
        assertTrue(response.getBody().containsKey("failureRate"));
        
        assertEquals(2, response.getBody().get("requestCount"));
    }

    @Test
    void testForceOpenCircuitBreaker() {
        assertEquals("CLOSED", circuitBreaker.getState());

        ResponseEntity<String> response = controller.forceOpenCircuitBreaker();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("熔断器已强制开启"));
        assertEquals("OPEN", circuitBreaker.getState());
    }

    @Test
    void testForceCloseCircuitBreaker() {
        // 首先强制开启熔断器
        circuitBreaker.forceOpen();
        assertEquals("OPEN", circuitBreaker.getState());

        ResponseEntity<String> response = controller.forceCloseCircuitBreaker();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("熔断器已强制关闭"));
        assertEquals("CLOSED", circuitBreaker.getState());
    }

    @Test
    void testGetMonitoringOverview() {
        // 模拟系统状态
        when(monitoringProperties.isEnabled()).thenReturn(true);
        when(monitoringProperties.getPrefix()).thenReturn("jairouter");
        when(monitoringProperties.getEnabledCategories()).thenReturn(
            java.util.Set.of("system", "business", "infrastructure")
        );
        
        when(asyncProcessor.getStats()).thenReturn(
            new AsyncMetricsProcessor.ProcessingStats(1000, 0, 10, "CLOSED")
        );
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.1, 100L, 1000L, 50, 100, 10, 0, 0)
        );

        ResponseEntity<Map<String, Object>> response = controller.getMonitoringOverview();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        
        // 验证概览包含所有必要信息
        assertTrue(response.getBody().containsKey("enabled"));
        assertTrue(response.getBody().containsKey("prefix"));
        assertTrue(response.getBody().containsKey("enabledCategories"));
        assertTrue(response.getBody().containsKey("healthy"));
        assertTrue(response.getBody().containsKey("errorStats"));
        assertTrue(response.getBody().containsKey("degradationStatus"));
        assertTrue(response.getBody().containsKey("cacheStats"));
        assertTrue(response.getBody().containsKey("circuitBreakerStats"));
        
        assertEquals(true, response.getBody().get("enabled"));
        assertEquals("jairouter", response.getBody().get("prefix"));
    }

    @Test
    void testErrorHandlingInEndpoints() {
        // 测试当组件抛出异常时的错误处理
        
        // 模拟健康检查异常
        when(asyncProcessor.getStats()).thenThrow(new RuntimeException("Async processor error"));
        when(memoryManager.getMemoryStats()).thenReturn(
            new MetricsMemoryManager.MemoryStats(0.1, 100L, 1000L, 50, 100, 10, 0, 0)
        );
        
        ResponseEntity<Map<String, Object>> healthResponse = controller.getHealthStatus();
        // 健康检查器会捕获异常并返回结构化的错误响应，状态码仍然是200
        assertEquals(200, healthResponse.getStatusCode().value());
        assertNotNull(healthResponse.getBody());
        assertTrue(healthResponse.getBody().containsKey("healthy"));
        // 但是健康状态应该是false，因为有组件异常
        assertEquals(false, healthResponse.getBody().get("healthy"));
    }
}
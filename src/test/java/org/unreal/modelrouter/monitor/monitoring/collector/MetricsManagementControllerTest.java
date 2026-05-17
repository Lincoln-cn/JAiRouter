package org.unreal.modelrouter.monitor.monitoring.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.monitor.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitor.monitoring.error.MetricsErrorHandler;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MetricsManagementController 单元测试
 *
 * <p>测试监控管理功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("MetricsManagementController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricsManagementControllerTest {

    private MonitoringProperties monitoringProperties;
    private AsyncMetricsCollector asyncMetricsCollector;
    private MetricsCircuitBreaker circuitBreaker;
    private MetricsMemoryManager memoryManager;
    private DynamicMonitoringConfigUpdater configUpdater;
    private MetricsErrorHandler errorHandler;
    private MetricsDegradationStrategy degradationStrategy;
    private MetricsCacheAndRetry cacheAndRetry;
    private MonitoringHealthChecker healthChecker;
    private MetricsManagementController controller;

    @BeforeEach
    void setUp() {
        monitoringProperties = mock(MonitoringProperties.class);
        asyncMetricsCollector = mock(AsyncMetricsCollector.class);
        circuitBreaker = mock(MetricsCircuitBreaker.class);
        memoryManager = mock(MetricsMemoryManager.class);
        configUpdater = mock(DynamicMonitoringConfigUpdater.class);
        errorHandler = mock(MetricsErrorHandler.class);
        degradationStrategy = mock(MetricsDegradationStrategy.class);
        cacheAndRetry = mock(MetricsCacheAndRetry.class);
        healthChecker = mock(MonitoringHealthChecker.class);

        controller = new MetricsManagementController(
                monitoringProperties, asyncMetricsCollector, circuitBreaker,
                memoryManager, configUpdater, errorHandler, degradationStrategy,
                cacheAndRetry, healthChecker
        );
    }

    // ==================== 获取监控系统状态测试 ====================

    @Nested
    @DisplayName("GET /actuator/metrics/status - 获取监控系统状态测试")
    class GetMonitoringStatusTests {

        @Test
        @DisplayName("METRICS-001: 成功获取监控系统状态")
        void testGetMonitoringStatus_success() {
            // Given
            when(monitoringProperties.isEnabled()).thenReturn(true);
            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            
            AsyncMetricsCollector.PerformanceStats perfStats = mock(AsyncMetricsCollector.PerformanceStats.class);
            when(perfStats.isAsyncProcessingEnabled()).thenReturn(true);
            when(asyncMetricsCollector.getPerformanceStats()).thenReturn(perfStats);
            when(circuitBreaker.getStats()).thenReturn(mock(MetricsCircuitBreaker.CircuitBreakerStats.class));

            // When
            ResponseEntity<Map<String, Object>> result = controller.getMonitoringStatus();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue((Boolean) result.getBody().get("enabled"));
        }
    }

    // ==================== 获取性能统计测试 ====================

    @Nested
    @DisplayName("GET /actuator/metrics/performance - 获取性能统计测试")
    class GetPerformanceStatsTests {

        @Test
        @DisplayName("METRICS-002: 成功获取性能统计")
        void testGetPerformanceStats_success() {
            // Given
            AsyncMetricsCollector.PerformanceStats stats = mock(AsyncMetricsCollector.PerformanceStats.class);
            when(asyncMetricsCollector.getPerformanceStats()).thenReturn(stats);

            // When
            ResponseEntity<AsyncMetricsCollector.PerformanceStats> result = controller.getPerformanceStats();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
        }
    }

    // ==================== 获取内存统计测试 ====================

    @Nested
    @DisplayName("GET /actuator/metrics/memory - 获取内存统计测试")
    class GetMemoryStatsTests {

        @Test
        @DisplayName("METRICS-003: 成功获取内存统计")
        void testGetMemoryStats_success() {
            // Given
            MetricsMemoryManager.MemoryStats stats = mock(MetricsMemoryManager.MemoryStats.class);
            when(memoryManager.getMemoryStats()).thenReturn(stats);

            // When
            ResponseEntity<MetricsMemoryManager.MemoryStats> result = controller.getMemoryStats();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
        }
    }

    // ==================== 获取熔断器统计测试 ====================

    @Nested
    @DisplayName("GET /actuator/metrics/circuit-breaker - 获取熔断器统计测试")
    class GetCircuitBreakerStatsTests {

        @Test
        @DisplayName("METRICS-004: 成功获取熔断器统计")
        void testGetCircuitBreakerStats_success() {
            // Given
            MetricsCircuitBreaker.CircuitBreakerStats stats = mock(MetricsCircuitBreaker.CircuitBreakerStats.class);
            when(circuitBreaker.getStats()).thenReturn(stats);

            // When
            ResponseEntity<MetricsCircuitBreaker.CircuitBreakerStats> result = controller.getCircuitBreakerStats();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
        }
    }

    // ==================== 内存清理测试 ====================

    @Nested
    @DisplayName("POST /actuator/metrics/memory/cleanup - 内存清理测试")
    class ForceMemoryCleanupTests {

        @Test
        @DisplayName("METRICS-005: 成功执行内存清理")
        void testForceMemoryCleanup_success() {
            // Given
            doNothing().when(memoryManager).performMemoryCheck();

            // When
            ResponseEntity<String> result = controller.forceMemoryCleanup();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().contains("completed"));
        }
    }

    // ==================== 清空指标缓存测试 ====================

    @Nested
    @DisplayName("POST /actuator/metrics/memory/clear-cache - 清空指标缓存测试")
    class ClearMetricsCacheTests {

        @Test
        @DisplayName("METRICS-006: 成功清空指标缓存")
        void testClearMetricsCache_success() {
            // Given
            doNothing().when(memoryManager).clearCache();

            // When
            ResponseEntity<String> result = controller.clearMetricsCache();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().contains("cleared"));
        }
    }

    // ==================== 熔断器控制测试 ====================

    @Nested
    @DisplayName("POST /actuator/metrics/circuit-breaker/{action} - 熔断器控制测试")
    class ControlCircuitBreakerTests {

        @Test
        @DisplayName("METRICS-007: 成功打开熔断器")
        void testControlCircuitBreaker_open() {
            // Given
            doNothing().when(circuitBreaker).forceOpen();

            // When
            ResponseEntity<String> result = controller.controlCircuitBreaker("open");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().contains("opened"));
        }

        @Test
        @DisplayName("METRICS-008: 成功关闭熔断器")
        void testControlCircuitBreaker_close() {
            // Given
            doNothing().when(circuitBreaker).forceClose();

            // When
            ResponseEntity<String> result = controller.controlCircuitBreaker("close");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().contains("closed"));
        }

        @Test
        @DisplayName("METRICS-009: 无效操作返回错误")
        void testControlCircuitBreaker_invalidAction() {
            // When
            ResponseEntity<String> result = controller.controlCircuitBreaker("invalid");

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertTrue(result.getBody().contains("Invalid"));
        }
    }

    // ==================== 获取降级状态测试 ====================

    @Nested
    @DisplayName("GET /actuator/metrics/degradation - 获取降级状态测试")
    class GetDegradationStatusTests {

        @Test
        @DisplayName("METRICS-010: 成功获取降级状态")
        void testGetDegradationStatus_success() {
            // Given
            MetricsDegradationStrategy.DegradationStatus status = mock(MetricsDegradationStrategy.DegradationStatus.class);
            when(degradationStrategy.getDegradationStatus()).thenReturn(status);

            // When
            ResponseEntity<MetricsDegradationStrategy.DegradationStatus> result = controller.getDegradationStatus();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
        }
    }

    // ==================== 设置降级级别测试 ====================

    @Nested
    @DisplayName("POST /actuator/metrics/degradation/level - 设置降级级别测试")
    class SetDegradationLevelTests {

        @Test
        @DisplayName("METRICS-011: 成功设置降级级别")
        void testSetDegradationLevel_success() {
            // Given
            doNothing().when(degradationStrategy).setDegradationLevel(any());

            // When
            ResponseEntity<String> result = controller.setDegradationLevel("NONE");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
        }

        @Test
        @DisplayName("METRICS-012: 无效级别返回错误")
        void testSetDegradationLevel_invalid() {
            // When
            ResponseEntity<String> result = controller.setDegradationLevel("INVALID");

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertTrue(result.getBody().contains("Invalid"));
        }
    }

    // ==================== 强制恢复测试 ====================

    @Nested
    @DisplayName("POST /actuator/metrics/degradation/force-recovery - 强制恢复测试")
    class ForceRecoveryTests {

        @Test
        @DisplayName("METRICS-013: 成功强制恢复")
        void testForceRecovery_success() {
            // Given
            doNothing().when(degradationStrategy).forceRecovery();

            // When
            ResponseEntity<String> result = controller.forceRecovery();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().contains("recovery"));
        }
    }

    // ==================== 健康检查测试 ====================

    @Nested
    @DisplayName("GET /actuator/metrics/health - 健康检查测试")
    class GetHealthTests {

        @Test
        @DisplayName("METRICS-014: 健康检查通过")
        void testGetHealth_healthy() {
            // Given
            MonitoringHealthChecker.HealthCheckResult healthResult = mock(MonitoringHealthChecker.HealthCheckResult.class);
            when(healthResult.isHealthy()).thenReturn(true);
            when(healthChecker.performHealthCheck()).thenReturn(healthResult);

            // When
            ResponseEntity<Map<String, Object>> result = controller.getHealth();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("UP", result.getBody().get("status"));
        }

        @Test
        @DisplayName("METRICS-015: 健康检查失败")
        void testGetHealth_unhealthy() {
            // Given
            MonitoringHealthChecker.HealthCheckResult healthResult = mock(MonitoringHealthChecker.HealthCheckResult.class);
            when(healthResult.isHealthy()).thenReturn(false);
            when(healthChecker.performHealthCheck()).thenReturn(healthResult);

            // When
            ResponseEntity<Map<String, Object>> result = controller.getHealth();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("DOWN", result.getBody().get("status"));
        }
    }

    // ==================== 更新采样率配置测试 ====================

    @Nested
    @DisplayName("POST /actuator/metrics/sampling - 更新采样率配置测试")
    class UpdateSamplingConfigTests {

        @Test
        @DisplayName("METRICS-016: 成功更新采样率配置")
        void testUpdateSamplingConfig_success() {
            // Given
            MonitoringProperties.Sampling sampling = mock(MonitoringProperties.Sampling.class);
            when(monitoringProperties.getSampling()).thenReturn(sampling);
            
            MetricsManagementController.SamplingUpdateRequest request = new MetricsManagementController.SamplingUpdateRequest();
            request.setRequestMetrics(0.5);

            // When
            ResponseEntity<String> result = controller.updateSamplingConfig(request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(sampling).setRequestMetrics(0.5);
        }
    }
}

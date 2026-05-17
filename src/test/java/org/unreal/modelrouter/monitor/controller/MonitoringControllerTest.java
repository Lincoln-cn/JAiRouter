package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.monitor.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitor.monitoring.error.MetricsErrorHandler;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MonitoringController 单元测试
 * 
 * <p>测试监控配置管理功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("MonitoringController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitoringControllerTest {

    @Mock
    private MonitoringProperties monitoringProperties;

    @Mock
    private DynamicMonitoringConfigUpdater configUpdater;

    @Mock
    private MetricsErrorHandler errorHandler;

    @Mock
    private MonitoringHealthChecker healthChecker;

    @Mock
    private MetricsCacheAndRetry cacheAndRetry;

    @Mock
    private MetricsDegradationStrategy degradationStrategy;

    @Mock
    private MetricsCircuitBreaker circuitBreaker;

    @InjectMocks
    private MonitoringController controller;

    @BeforeEach
    void setUp() {
        when(monitoringProperties.isEnabled()).thenReturn(true);
        when(monitoringProperties.getPrefix()).thenReturn("jairouter");
        when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
        when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("router", "adapter"));
        when(monitoringProperties.getCustomTags()).thenReturn(new HashMap<>());
        when(monitoringProperties.getSampling()).thenReturn(mock(MonitoringProperties.Sampling.class));
        when(monitoringProperties.getPerformance()).thenReturn(mock(MonitoringProperties.Performance.class));
    }

    // ==================== 获取配置测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/config - 获取配置测试")
    class GetConfigurationTests {

        @Test
        @DisplayName("MONITOR-001: 成功获取监控配置")
        void testGetConfiguration_success() {
            // When
            var result = controller.getConfiguration();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 更新启用状态测试 ====================

    @Nested
    @DisplayName("PUT /api/monitoring/config/enabled - 更新启用状态测试")
    class UpdateEnabledTests {

        @Test
        @DisplayName("MONITOR-002: 成功更新启用状态")
        void testUpdateEnabled_success() {
            // Given
            when(configUpdater.updateBasicConfig(anyBoolean(), anyString(), any(), any())).thenReturn(true);

            // When
            var result = controller.updateEnabled(Map.of("enabled", false));

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("MONITOR-003: 缺少参数返回错误")
        void testUpdateEnabled_missingParam() {
            // When
            var result = controller.updateEnabled(new HashMap<>());

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("Missing"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== 更新前缀测试 ====================

    @Nested
    @DisplayName("PUT /api/monitoring/config/prefix - 更新前缀测试")
    class UpdatePrefixTests {

        @Test
        @DisplayName("MONITOR-004: 成功更新指标前缀")
        void testUpdatePrefix_success() {
            // Given
            when(configUpdater.validateConfigurationChange(anyString(), anyString())).thenReturn(true);
            when(configUpdater.updateBasicConfig(anyBoolean(), anyString(), any(), any())).thenReturn(true);

            // When
            var result = controller.updatePrefix(Map.of("prefix", "newprefix"));

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("MONITOR-005: 空前缀返回错误")
        void testUpdatePrefix_emptyPrefix() {
            // When
            var result = controller.updatePrefix(Map.of("prefix", ""));

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 健康状态测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/health - 健康状态测试")
    class GetHealthStatusTests {

        @Test
        @DisplayName("MONITOR-006: 成功获取健康状态")
        void testGetHealthStatus_success() {
            // Given
            MonitoringHealthChecker.HealthCheckResult healthResult = mock(MonitoringHealthChecker.HealthCheckResult.class);
            when(healthResult.isHealthy()).thenReturn(true);
            when(healthResult.getDetails()).thenReturn(new HashMap<>());
            when(healthChecker.performHealthCheck()).thenReturn(healthResult);

            // When
            var result = controller.getHealthStatus();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 错误统计测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/errors/stats - 错误统计测试")
    class GetErrorStatsTests {

        @Test
        @DisplayName("MONITOR-007: 成功获取错误统计")
        void testGetErrorStats_success() {
            // Given
            MetricsErrorHandler.MetricsErrorStats stats = mock(MetricsErrorHandler.MetricsErrorStats.class);
            when(stats.getActiveErrorComponents()).thenReturn(0);
            when(stats.getDegradedComponents()).thenReturn(0);
            when(stats.getTotalErrors()).thenReturn(0);
            when(errorHandler.getErrorStats()).thenReturn(stats);

            // When
            var result = controller.getErrorStats();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 降级状态测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/degradation/status - 降级状态测试")
    class GetDegradationStatusTests {

        @Test
        @DisplayName("MONITOR-008: 成功获取降级状态")
        void testGetDegradationStatus_success() {
            // Given
            MetricsDegradationStrategy.DegradationStatus status = mock(MetricsDegradationStrategy.DegradationStatus.class);
            when(status.getLevel()).thenReturn(MetricsDegradationStrategy.DegradationLevel.NONE);
            when(status.getLevel()).thenReturn(MetricsDegradationStrategy.DegradationLevel.NONE);
            when(status.getSamplingRate()).thenReturn(1.0);
            when(status.isAutoModeEnabled()).thenReturn(true);
            when(status.getTimeSinceLastChange()).thenReturn(Duration.ofMinutes(5));
            when(status.getErrorComponentCount()).thenReturn(0);
            when(degradationStrategy.getDegradationStatus()).thenReturn(status);

            // When
            var result = controller.getDegradationStatus();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 缓存统计测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/cache/stats - 缓存统计测试")
    class GetCacheStatsTests {

        @Test
        @DisplayName("MONITOR-009: 成功获取缓存统计")
        void testGetCacheStats_success() {
            // Given
            MetricsCacheAndRetry.CacheStats stats = mock(MetricsCacheAndRetry.CacheStats.class);
            when(stats.getCurrentSize()).thenReturn(10);
            when(stats.getMaxSize()).thenReturn(100);
            when(stats.getUsageRatio()).thenReturn(0.1);
            when(stats.getActiveRetries()).thenReturn(0);
            when(stats.getQueuedRetries()).thenReturn(0);
            when(cacheAndRetry.getCacheStats()).thenReturn(stats);

            // When
            var result = controller.getCacheStats();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 清空缓存测试 ====================

    @Nested
    @DisplayName("POST /api/monitoring/cache/clear - 清空缓存测试")
    class ClearCacheTests {

        @Test
        @DisplayName("MONITOR-010: 成功清空缓存")
        void testClearCache_success() {
            // Given
            doNothing().when(cacheAndRetry).clearCache();

            // When
            var result = controller.clearCache();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 熔断器统计测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/circuit-breaker/stats - 熔断器统计测试")
    class GetCircuitBreakerStatsTests {

        @Test
        @DisplayName("MONITOR-011: 成功获取熔断器统计")
        void testGetCircuitBreakerStats_success() {
            // Given
            MetricsCircuitBreaker.CircuitBreakerStats stats = mock(MetricsCircuitBreaker.CircuitBreakerStats.class);
            when(stats.getState()).thenReturn("CLOSED");
            when(stats.getFailureCount()).thenReturn(0);
            when(stats.getSuccessCount()).thenReturn(100);
            when(stats.getRequestCount()).thenReturn(100);
            when(stats.getFailureRate()).thenReturn(0.0);
            when(circuitBreaker.getStats()).thenReturn(stats);

            // When
            var result = controller.getCircuitBreakerStats();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 强制开启熔断器测试 ====================

    @Nested
    @DisplayName("POST /api/monitoring/circuit-breaker/force-open - 强制开启熔断器测试")
    class ForceOpenCircuitBreakerTests {

        @Test
        @DisplayName("MONITOR-012: 成功强制开启熔断器")
        void testForceOpenCircuitBreaker_success() {
            // Given
            doNothing().when(circuitBreaker).forceOpen();

            // When
            var result = controller.forceOpenCircuitBreaker();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 监控概览测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/overview - 监控概览测试")
    class GetMonitoringOverviewTests {

        @Test
        @DisplayName("MONITOR-013: 成功获取监控概览")
        void testGetMonitoringOverview_success() {
            // Given
            MonitoringHealthChecker.HealthCheckResult healthResult = mock(MonitoringHealthChecker.HealthCheckResult.class);
            when(healthResult.isHealthy()).thenReturn(true);
            when(healthResult.getDetails()).thenReturn(new HashMap<>());
            when(healthChecker.performHealthCheck()).thenReturn(healthResult);

            MetricsErrorHandler.MetricsErrorStats errorStats = mock(MetricsErrorHandler.MetricsErrorStats.class);
            when(errorStats.getActiveErrorComponents()).thenReturn(0);
            when(errorStats.getDegradedComponents()).thenReturn(0);
            when(errorStats.getTotalErrors()).thenReturn(0);
            when(errorHandler.getErrorStats()).thenReturn(errorStats);

            MetricsDegradationStrategy.DegradationStatus degradationStatus = mock(MetricsDegradationStrategy.DegradationStatus.class);
            when(degradationStatus.getLevel()).thenReturn(MetricsDegradationStrategy.DegradationLevel.NONE);
            when(degradationStatus.getSamplingRate()).thenReturn(1.0);
            when(degradationStatus.isAutoModeEnabled()).thenReturn(true);
            when(degradationStrategy.getDegradationStatus()).thenReturn(degradationStatus);

            MetricsCacheAndRetry.CacheStats cacheStats = mock(MetricsCacheAndRetry.CacheStats.class);
            when(cacheStats.getUsageRatio()).thenReturn(0.1);
            when(cacheStats.getActiveRetries()).thenReturn(0);
            when(cacheAndRetry.getCacheStats()).thenReturn(cacheStats);

            MetricsCircuitBreaker.CircuitBreakerStats cbStats = mock(MetricsCircuitBreaker.CircuitBreakerStats.class);
            when(cbStats.getState()).thenReturn("CLOSED");
            when(cbStats.getFailureRate()).thenReturn(0.0);
            when(circuitBreaker.getStats()).thenReturn(cbStats);

            // When
            var result = controller.getMonitoringOverview();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                    })
                    .verifyComplete();
        }
    }
}

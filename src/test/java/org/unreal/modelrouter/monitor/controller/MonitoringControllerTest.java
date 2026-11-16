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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCacheAndRetry;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsDegradationStrategy;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MonitoringHealthChecker;
import org.unreal.modelrouter.monitor.monitoring.config.DynamicMonitoringConfigUpdater;
import org.unreal.modelrouter.monitor.monitoring.error.MetricsErrorHandler;
import org.unreal.modelrouter.monitor.monitoring.security.SecurityAlertService;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.AuditEventType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
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
@SuppressWarnings("deprecation") // 兼容别名路径仍需被测试覆盖（#555）
class MonitoringControllerTest {

    /** 写端点新增的审计主体参数：审计只记名字，不需要真实凭据。 */
    private static final Authentication AUTH = new UsernamePasswordAuthenticationToken(
            "tester", "n/a", List.of());

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

    @Mock
    private ObjectProvider<SecurityAlertService> alertServiceProvider;

    @Mock
    private SecurityAlertService securityAlertService;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private MonitoringController controller;

    @BeforeEach
    void setUp() {
        when(securityAuditService.recordEvent(any())).thenReturn(Mono.empty());
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
            var result = controller.updateEnabled(Map.of("enabled", false), AUTH);

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
            var result = controller.updateEnabled(new HashMap<>(), AUTH);

            // Then
            // 参数缺失属于客户端不合规：ApiException(INVALID_REQUEST) 由
            // GlobalControllerExceptionHandler 映射为 400，而不是 200 + success=false。
            StepVerifier.create(result)
                    .expectErrorSatisfies(ex -> {
                        assertInstanceOf(ApiException.class, ex);
                        ApiException apiEx = (ApiException) ex;
                        assertEquals("INVALID_REQUEST", apiEx.getErrorCode());
                        assertEquals(HttpStatus.BAD_REQUEST, apiEx.getStatus());
                        assertTrue(apiEx.getMessage().contains("Missing"));
                    })
                    .verify();
        }
    }

    // ==================== 降级策略自动模式测试 ====================

    @Nested
    @DisplayName("POST /api/monitoring/degradation/auto-mode - 降级策略自动模式测试")
    class SetAutoModeTests {

        @Test
        @DisplayName("MONITOR-020: 缺少 enabled 参数返回 400（与 updateEnabled 口径一致）")
        void testSetAutoMode_missingEnabledParam() {
            // When：请求体存在但没有 enabled 键
            var result = controller.setAutoMode(new HashMap<>(), AUTH);

            // Then：客户端参数不合规 → ApiException(INVALID_REQUEST) → 400。
            // 此前该分支既无测试覆盖、错误码又与同文件 updateEnabled 不一致（500 vs 400）。
            StepVerifier.create(result)
                    .expectErrorSatisfies(ex -> {
                        assertInstanceOf(ApiException.class, ex);
                        ApiException apiEx = (ApiException) ex;
                        assertEquals("INVALID_REQUEST", apiEx.getErrorCode());
                        assertEquals(HttpStatus.BAD_REQUEST, apiEx.getStatus());
                        assertTrue(apiEx.getMessage().contains("Missing 'enabled' parameter"));
                    })
                    .verify();
        }

        @Test
        @DisplayName("MONITOR-021: 启用自动模式成功")
        void testSetAutoMode_enabledSuccess() {
            // When
            var result = controller.setAutoMode(Map.of("enabled", true), AUTH);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> assertTrue(response.isSuccess()))
                    .verifyComplete();
            verify(degradationStrategy).setAutoModeEnabled(true);
        }

        @Test
        @DisplayName("MONITOR-022: 禁用自动模式成功")
        void testSetAutoMode_disabledSuccess() {
            // When
            var result = controller.setAutoMode(Map.of("enabled", false), AUTH);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> assertTrue(response.isSuccess()))
                    .verifyComplete();
            verify(degradationStrategy).setAutoModeEnabled(false);
        }

        @Test
        @DisplayName("MONITOR-023: 策略内部异常不得再被压成 200")
        void testSetAutoMode_internalFailureIsErrorStatus() {
            // Given：策略内部抛错
            doThrow(new IllegalStateException("strategy down"))
                    .when(degradationStrategy).setAutoModeEnabled(anyBoolean());

            // When
            var result = controller.setAutoMode(Map.of("enabled", true), AUTH);

            // Then：经 catch-all ApiExceptions.wrap 归一为 ApiException，
            // 关键断言是「绝不能再返回 200 + success=false」（issue #94 的核心契约）。
            StepVerifier.create(result)
                    .expectErrorSatisfies(ex -> {
                        assertInstanceOf(ApiException.class, ex);
                        ApiException apiEx = (ApiException) ex;
                        assertFalse(apiEx.getStatus().is2xxSuccessful(),
                                "内部失败不应以 2xx 状态返回: " + apiEx.getStatus());
                        assertTrue(apiEx.getMessage().contains("设置失败"));
                    })
                    .verify();
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
            var result = controller.updatePrefix(Map.of("prefix", "newprefix"), AUTH);

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
            var result = controller.updatePrefix(Map.of("prefix", ""), AUTH);

            // Then
            // 前缀为空属于客户端不合规：ApiException(INVALID_REQUEST) 由
            // GlobalControllerExceptionHandler 映射为 400，而不是 200 + success=false。
            StepVerifier.create(result)
                    .expectErrorSatisfies(ex -> {
                        assertInstanceOf(ApiException.class, ex);
                        ApiException apiEx = (ApiException) ex;
                        assertEquals("INVALID_REQUEST", apiEx.getErrorCode());
                        assertEquals(HttpStatus.BAD_REQUEST, apiEx.getStatus());
                        assertTrue(apiEx.getMessage().contains("Missing or empty 'prefix'"));
                    })
                    .verify();
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
            var result = controller.clearCache(AUTH);

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
            var result = controller.forceOpenCircuitBreaker(AUTH);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 指标处理链路熔断器作用域测试（#555） ====================

    @Nested
    @DisplayName("指标处理链路熔断器：新旧路径共用同一个 MetricsCircuitBreaker（#555）")
    class MetricsPipelineCircuitBreakerScopeTests {

        @Test
        @DisplayName("MONITOR-025: 规范路径 force-open 命中同一个熔断器且响应说明作用域")
        void testMetricsPipelineForceOpen_hitsSameCircuitBreaker() {
            // When
            var result = controller.forceOpenMetricsPipelineCircuitBreaker(AUTH);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getMessage());
                        assertTrue(response.getMessage().contains("指标处理链路"),
                                "响应文案必须写明作用于指标处理链路，实际: " + response.getMessage());
                    })
                    .verifyComplete();
            // 新旧路径委派同一私有实现，因此对同一个 MetricsCircuitBreaker 只应触发一次
            verify(circuitBreaker, times(1)).forceOpen();
        }

        @Test
        @DisplayName("MONITOR-026: 旧路径 force-open 是兼容别名，命中同一个熔断器")
        void testLegacyForceOpen_hitsSameCircuitBreaker() {
            // When
            var result = controller.forceOpenCircuitBreaker(AUTH);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertTrue(response.getMessage().contains("指标处理链路"));
                    })
                    .verifyComplete();
            verify(circuitBreaker, times(1)).forceOpen();
        }

        @Test
        @DisplayName("MONITOR-027: 规范路径 force-close 命中同一个熔断器且响应说明作用域")
        void testMetricsPipelineForceClose_hitsSameCircuitBreaker() {
            // When
            var result = controller.forceCloseMetricsPipelineCircuitBreaker(AUTH);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getMessage());
                        assertTrue(response.getMessage().contains("指标处理链路"));
                    })
                    .verifyComplete();
            verify(circuitBreaker, times(1)).forceClose();
        }

        @Test
        @DisplayName("MONITOR-028: 旧路径 force-close 是兼容别名，命中同一个熔断器")
        void testLegacyForceClose_hitsSameCircuitBreaker() {
            // When
            var result = controller.forceCloseCircuitBreaker(AUTH);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> assertTrue(response.isSuccess()))
                    .verifyComplete();
            verify(circuitBreaker, times(1)).forceClose();
        }

        @Test
        @DisplayName("MONITOR-029: 规范路径 stats 与旧路径读取同一个熔断器")
        void testMetricsPipelineStats_hitsSameCircuitBreaker() {
            // Given
            MetricsCircuitBreaker.CircuitBreakerStats stats = mock(MetricsCircuitBreaker.CircuitBreakerStats.class);
            when(stats.getState()).thenReturn("OPEN");
            when(circuitBreaker.getStats()).thenReturn(stats);

            // When
            var newPathResult = controller.getMetricsPipelineCircuitBreakerStats();
            var legacyPathResult = controller.getCircuitBreakerStats();

            // Then
            StepVerifier.create(newPathResult)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("OPEN", response.getData().get("state"));
                    })
                    .verifyComplete();
            StepVerifier.create(legacyPathResult)
                    .assertNext(response -> assertTrue(response.isSuccess()))
                    .verifyComplete();
            // 两条路径都读同一个 MetricsCircuitBreaker 单例
            verify(circuitBreaker, times(2)).getStats();
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

        @Test
        @DisplayName("MONITOR-014: 概览包含近 24 小时安全告警数（#291）")
        void testGetMonitoringOverview_includesSecurityAlertsLast24h() {
            stubOverviewDependencies();
            when(alertServiceProvider.getIfAvailable()).thenReturn(securityAlertService);
            when(securityAlertService.countAlertsLast24Hours()).thenReturn(Mono.just(3L));

            StepVerifier.create(controller.getMonitoringOverview())
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(3L, response.getData().get("alertsLast24Hours"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("MONITOR-015: 告警服务未装配时不返回该键（alerts 关闭的部署形态）")
        void testGetMonitoringOverview_withoutAlertService_omitsKey() {
            stubOverviewDependencies();
            when(alertServiceProvider.getIfAvailable()).thenReturn(null);

            StepVerifier.create(controller.getMonitoringOverview())
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertFalse(response.getData().containsKey("alertsLast24Hours"));
                    })
                    .verifyComplete();
        }

        private void stubOverviewDependencies() {
            MonitoringHealthChecker.HealthCheckResult healthResult = mock(MonitoringHealthChecker.HealthCheckResult.class);
            when(healthResult.isHealthy()).thenReturn(true);
            when(healthResult.getDetails()).thenReturn(new HashMap<>());
            when(healthChecker.performHealthCheck()).thenReturn(healthResult);

            MetricsErrorHandler.MetricsErrorStats errorStats = mock(MetricsErrorHandler.MetricsErrorStats.class);
            when(errorStats.getActiveErrorComponents()).thenReturn(0);
            when(errorStats.getDegradedComponents()).thenReturn(0);
            when(errorStats.getTotalErrors()).thenReturn(0);
            when(errorHandler.getErrorStats()).thenReturn(errorStats);

            MetricsDegradationStrategy.DegradationStatus degradationStatus =
                    mock(MetricsDegradationStrategy.DegradationStatus.class);
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
        }
    }

    // ==================== 采样率 / 采集性能热改测试 ====================

    @Nested
    @DisplayName("PUT /api/monitoring/config/sampling 与 /config/performance - 热改测试")
    class MonitoringConfigHotReloadTests {

        private MonitoringController.SamplingUpdateRequest allSamplingRates() {
            MonitoringController.SamplingUpdateRequest request = new MonitoringController.SamplingUpdateRequest();
            request.setRequestMetrics(0.5);
            request.setBackendMetrics(0.4);
            request.setInfrastructureMetrics(0.3);
            request.setTraceMetrics(0.2);
            request.setTraceProcessingMetrics(0.1);
            request.setTraceAnalysisMetrics(0.05);
            return request;
        }

        private MonitoringController.PerformanceUpdateRequest performance(
                final Boolean async, final Integer batchSize, final Integer bufferSize) {
            MonitoringController.PerformanceUpdateRequest request = new MonitoringController.PerformanceUpdateRequest();
            request.setAsyncProcessing(async);
            request.setBatchSize(batchSize);
            request.setBufferSize(bufferSize);
            return request;
        }

        @Test
        @DisplayName("MONITOR-SAMPLING-001: 采样率热改成功并写审计")
        void testUpdateSampling_success() {
            when(configUpdater.updateSamplingConfig(any())).thenReturn(true);

            StepVerifier.create(controller.updateSampling(allSamplingRates(), AUTH))
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("采样率已更新", response.getMessage());
                    })
                    .verifyComplete();

            verify(securityAuditService).recordEvent(argThat(event ->
                    AuditEventType.MONITORING_CONFIG_CHANGED.name().equals(event.getEventType())
                            && "tester".equals(event.getUserId())
                            && "CONFIG_SAMPLING".equals(event.getAction())
                            && "monitoring".equals(event.getResource())));
        }

        @Test
        @DisplayName("MONITOR-SAMPLING-002: 采样率越界被拒且不写审计")
        void testUpdateSampling_outOfRange() {
            MonitoringController.SamplingUpdateRequest request = allSamplingRates();
            request.setRequestMetrics(1.5);

            StepVerifier.create(controller.updateSampling(request, AUTH))
                    .expectErrorSatisfies(ex -> {
                        assertInstanceOf(ApiException.class, ex);
                        assertEquals("INVALID_REQUEST", ((ApiException) ex).getErrorCode());
                        assertTrue(ex.getMessage().contains("0~1"));
                    })
                    .verify();

            verify(securityAuditService, never()).recordEvent(any());
        }

        @Test
        @DisplayName("MONITOR-SAMPLING-003: 无变化时不写审计")
        void testUpdateSampling_noChange() {
            when(configUpdater.updateSamplingConfig(any())).thenReturn(false);

            StepVerifier.create(controller.updateSampling(allSamplingRates(), AUTH))
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("采样率无变化", response.getMessage());
                    })
                    .verifyComplete();

            verify(securityAuditService, never()).recordEvent(any());
        }

        @Test
        @DisplayName("MONITOR-PERF-001: 采集性能热改成功并写审计")
        void testUpdatePerformance_success() {
            when(configUpdater.updatePerformanceConfig(any())).thenReturn(true);

            StepVerifier.create(controller.updatePerformance(performance(true, 200, 2000), AUTH))
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("采集性能配置已更新", response.getMessage());
                    })
                    .verifyComplete();

            verify(securityAuditService).recordEvent(argThat(event ->
                    AuditEventType.MONITORING_CONFIG_CHANGED.name().equals(event.getEventType())
                            && "CONFIG_PERFORMANCE".equals(event.getAction())));
        }

        @Test
        @DisplayName("MONITOR-PERF-002: 缓冲区小于批大小被拒")
        void testUpdatePerformance_bufferSmallerThanBatch() {
            StepVerifier.create(controller.updatePerformance(performance(true, 500, 100), AUTH))
                    .expectErrorSatisfies(ex -> {
                        assertInstanceOf(ApiException.class, ex);
                        assertEquals("INVALID_REQUEST", ((ApiException) ex).getErrorCode());
                        assertTrue(ex.getMessage().contains("缓冲区必须"));
                    })
                    .verify();
        }
    }

    /**
     * #1173：应急操作的影响面预览。
     *
     * <p>守两件事：① 每个 action 返回的 impact 字段齐不齐；② 明细确实按上限截断、总数单独给。
     * 预览是只读的，所以每个用例都顺带断言「没有写审计」。</p>
     */
    @Nested
    @DisplayName("应急操作影响面预览（#1173）")
    class EmergencyPreviewTests {

        @Test
        @DisplayName("PREVIEW-001: clear-cache 返回条数、上限与截断后的键名")
        void previewClearCache() {
            when(cacheAndRetry.getCacheStats()).thenReturn(
                    new MetricsCacheAndRetry.CacheStats(1234, 10000, 0, 0));
            when(cacheAndRetry.snapshotKeys(20)).thenReturn(List.of("http.server.requests", "jvm.memory.used"));

            StepVerifier.create(controller.previewEmergencyAction("clear-cache"))
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("clear-cache", response.getData().get("action"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> impact = (Map<String, Object>) response.getData().get("impact");
                        assertEquals(1234, impact.get("entries"));
                        assertEquals(10000, impact.get("maxSize"));
                        assertEquals(List.of("http.server.requests", "jvm.memory.used"), impact.get("sampleKeys"));
                    })
                    .verifyComplete();

            verify(securityAuditService, never()).recordEvent(any());
        }

        @Test
        @DisplayName("PREVIEW-002: reset-errors 返回键数、错误总数与逐键明细")
        void previewResetErrors() {
            when(errorHandler.getErrorStats())
                    .thenReturn(new MetricsErrorHandler.MetricsErrorStats(3, 1, 42));
            when(errorHandler.snapshotErrorKeys(20)).thenReturn(List.of(
                    new MetricsErrorHandler.ErrorKeySnapshot("collector", "scrape", 30),
                    new MetricsErrorHandler.ErrorKeySnapshot("reporter", "flush", 12)));

            StepVerifier.create(controller.previewEmergencyAction("reset-errors"))
                    .assertNext(response -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> impact = (Map<String, Object>) response.getData().get("impact");
                        assertEquals(3, impact.get("keys"));
                        assertEquals(42, impact.get("totalErrors"));
                        @SuppressWarnings("unchecked")
                        List<MetricsErrorHandler.ErrorKeySnapshot> sample =
                                (List<MetricsErrorHandler.ErrorKeySnapshot>) impact.get("sampleKeys");
                        assertEquals(2, sample.size());
                        assertEquals("collector", sample.get(0).getComponent());
                        assertEquals(30, sample.get(0).getCount());
                    })
                    .verifyComplete();

            verify(securityAuditService, never()).recordEvent(any());
        }

        @Test
        @DisplayName("PREVIEW-003: force-recovery 返回当前级别与自动模式")
        void previewForceRecovery() {
            MetricsDegradationStrategy.DegradationStatus status =
                    mock(MetricsDegradationStrategy.DegradationStatus.class);
            when(status.getLevel()).thenReturn(MetricsDegradationStrategy.DegradationLevel.LIGHT);
            when(status.getSamplingRate()).thenReturn(0.5);
            when(status.isAutoModeEnabled()).thenReturn(true);
            when(degradationStrategy.getDegradationStatus()).thenReturn(status);

            StepVerifier.create(controller.previewEmergencyAction("force-recovery"))
                    .assertNext(response -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> impact = (Map<String, Object>) response.getData().get("impact");
                        assertEquals("LIGHT", impact.get("level"));
                        assertEquals(0.5, impact.get("samplingRate"));
                        assertEquals(true, impact.get("autoModeEnabled"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("PREVIEW-004: breaker-open 返回当前熔断状态与失败率")
        void previewBreakerOpen() {
            MetricsCircuitBreaker.CircuitBreakerStats stats =
                    new MetricsCircuitBreaker.CircuitBreakerStats("CLOSED", 0, 0, 100, 0.0);
            when(circuitBreaker.getStats()).thenReturn(stats);

            StepVerifier.create(controller.previewEmergencyAction("breaker-open"))
                    .assertNext(response -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> impact = (Map<String, Object>) response.getData().get("impact");
                        assertEquals("CLOSED", impact.get("state"));
                        assertEquals(100, impact.get("requestCount"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("PREVIEW-005: 未知动作返回失败响应，不抛到调用方")
        void previewUnknownAction() {
            StepVerifier.create(controller.previewEmergencyAction("rm-rf"))
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("影响面"));
                    })
                    .verifyComplete();
        }
    }
}

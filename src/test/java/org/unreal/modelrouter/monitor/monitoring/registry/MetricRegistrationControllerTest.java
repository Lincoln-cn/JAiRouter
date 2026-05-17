package org.unreal.modelrouter.monitor.monitoring.registry;

import io.micrometer.core.instrument.Meter;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.monitor.monitoring.registry.model.MetricMetadata;
import org.unreal.modelrouter.monitor.monitoring.registry.model.MetricRegistrationRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MetricRegistrationController 单元测试
 *
 * <p>测试指标注册管理功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("MetricRegistrationController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricRegistrationControllerTest {

    @Mock
    private MetricRegistrationService metricRegistrationService;

    @Mock
    private CustomMeterRegistry customMeterRegistry;

    @InjectMocks
    private MetricRegistrationController controller;

    private MetricRegistrationRequest testRequest;
    private MetricMetadata testMetadata;

    @BeforeEach
    void setUp() {
        testRequest = MetricRegistrationRequest.builder("test.metric", Meter.Type.COUNTER)
                .description("Test metric")
                .build();

        testMetadata = MetricMetadata.builder("test.metric", Meter.Type.COUNTER)
                .description("Test metric")
                .build();
    }

    // ==================== 注册指标测试 ====================

    @Nested
    @DisplayName("POST /api/monitoring/metrics/register - 注册指标测试")
    class RegisterMetricTests {

        @Test
        @DisplayName("REG-001: 成功注册指标")
        void testRegisterMetric_success() {
            // Given
            MetricRegistrationService.MetricRegistrationResult result =
                    MetricRegistrationService.MetricRegistrationResult.success("test.metric");
            when(metricRegistrationService.registerBusinessMetric(any())).thenReturn(result);

            // When
            ResponseEntity<MetricRegistrationService.MetricRegistrationResult> response =
                    controller.registerMetric(testRequest);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isSuccess());
        }

        @Test
        @DisplayName("REG-002: 注册失败返回错误")
        void testRegisterMetric_failed() {
            // Given
            MetricRegistrationService.MetricRegistrationResult result =
                    MetricRegistrationService.MetricRegistrationResult.failure("test.metric", "Already exists", null);
            when(metricRegistrationService.registerBusinessMetric(any())).thenReturn(result);

            // When
            ResponseEntity<MetricRegistrationService.MetricRegistrationResult> response =
                    controller.registerMetric(testRequest);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertFalse(response.getBody().isSuccess());
        }
    }

    // ==================== 批量注册指标测试 ====================

    @Nested
    @DisplayName("POST /api/monitoring/metrics/register/batch - 批量注册指标测试")
    class BatchRegisterMetricsTests {

        @Test
        @DisplayName("REG-003: 成功批量注册")
        void testBatchRegisterMetrics_success() {
            // Given
            MetricRegistrationService.BatchRegistrationResult result =
                    new MetricRegistrationService.BatchRegistrationResult(2, 2, 0, List.of());
            when(metricRegistrationService.batchRegisterMetrics(anyList())).thenReturn(result);

            // When
            ResponseEntity<MetricRegistrationService.BatchRegistrationResult> response =
                    controller.batchRegisterMetrics(List.of(testRequest));

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(2, response.getBody().getSuccessCount());
        }
    }

    // ==================== 注销指标测试 ====================

    @Nested
    @DisplayName("DELETE /api/monitoring/metrics/{metricName} - 注销指标测试")
    class UnregisterMetricTests {

        @Test
        @DisplayName("REG-004: 成功注销指标")
        void testUnregisterMetric_success() {
            // Given
            when(metricRegistrationService.unregisterMetric(anyString(), anyMap())).thenReturn(true);

            // When
            ResponseEntity<Map<String, Object>> response = controller.unregisterMetric("test.metric", null);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue((Boolean) response.getBody().get("success"));
        }

        @Test
        @DisplayName("REG-005: 注销失败")
        void testUnregisterMetric_failed() {
            // Given
            when(metricRegistrationService.unregisterMetric(anyString(), anyMap())).thenReturn(false);

            // When
            ResponseEntity<Map<String, Object>> response = controller.unregisterMetric("nonexistent", null);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertFalse((Boolean) response.getBody().get("success"));
        }
    }

    // ==================== 获取指标元数据测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/metrics/metadata - 获取指标元数据测试")
    class GetAllMetricMetadataTests {

        @Test
        @DisplayName("REG-006: 成功获取所有元数据")
        void testGetAllMetricMetadata_success() {
            // Given
            when(customMeterRegistry.getAllMetricMetadata()).thenReturn(List.of(testMetadata));

            // When
            ResponseEntity<List<MetricMetadata>> response = controller.getAllMetricMetadata();

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().size());
        }
    }

    // ==================== 获取单个指标元数据测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/metrics/metadata/{metricName} - 获取单个指标元数据测试")
    class GetMetricMetadataTests {

        @Test
        @DisplayName("REG-007: 成功获取指标元数据")
        void testGetMetricMetadata_success() {
            // Given
            when(customMeterRegistry.getMetricMetadata("test.metric")).thenReturn(Optional.of(testMetadata));

            // When
            ResponseEntity<MetricMetadata> response = controller.getMetricMetadata("test.metric");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("test.metric", response.getBody().getName());
        }

        @Test
        @DisplayName("REG-008: 元数据不存在返回404")
        void testGetMetricMetadata_notFound() {
            // Given
            when(customMeterRegistry.getMetricMetadata("nonexistent")).thenReturn(Optional.empty());

            // When
            ResponseEntity<MetricMetadata> response = controller.getMetricMetadata("nonexistent");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }

    // ==================== 更新指标配置测试 ====================

    @Nested
    @DisplayName("PUT /api/monitoring/metrics/{metricName}/configuration - 更新指标配置测试")
    class UpdateMetricConfigurationTests {

        @Test
        @DisplayName("REG-009: 成功更新指标配置")
        void testUpdateMetricConfiguration_success() {
            // Given
            when(metricRegistrationService.updateMetricConfiguration(anyString(), anyBoolean(), anyDouble()))
                    .thenReturn(true);

            // When
            ResponseEntity<Map<String, Object>> response =
                    controller.updateMetricConfiguration("test.metric", true, 0.5);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue((Boolean) response.getBody().get("success"));
        }
    }

    // ==================== 获取指标统计信息测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/metrics/statistics - 获取指标统计信息测试")
    class GetMetricStatisticsTests {

        @Test
        @DisplayName("REG-010: 成功获取统计信息")
        void testGetMetricStatistics_success() {
            // Given
            MetricRegistrationService.MetricStatistics stats =
                    mock(MetricRegistrationService.MetricStatistics.class);
            when(metricRegistrationService.getMetricStatistics()).thenReturn(stats);

            // When
            ResponseEntity<MetricRegistrationService.MetricStatistics> response =
                    controller.getMetricStatistics();

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }
    }

    // ==================== 按类别获取指标测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/metrics/category/{category} - 按类别获取指标测试")
    class GetMetricsByCategoryTests {

        @Test
        @DisplayName("REG-011: 成功按类别获取指标")
        void testGetMetricsByCategory_success() {
            // Given
            when(metricRegistrationService.getMetricsByCategory("business"))
                    .thenReturn(List.of(testMetadata));

            // When
            ResponseEntity<List<MetricMetadata>> response = controller.getMetricsByCategory("business");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().size());
        }
    }

    // ==================== 搜索指标测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/metrics/search - 搜索指标测试")
    class SearchMetricsTests {

        @Test
        @DisplayName("REG-012: 成功搜索指标")
        void testSearchMetrics_success() {
            // Given
            when(metricRegistrationService.searchMetrics(anyString(), any())).thenReturn(List.of(testMetadata));

            // When
            ResponseEntity<List<MetricMetadata>> response = controller.searchMetrics("test.*", null);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().size());
        }
    }

    // ==================== 验证指标请求测试 ====================

    @Nested
    @DisplayName("POST /api/monitoring/metrics/validate - 验证指标请求测试")
    class ValidateMetricRequestTests {

        @Test
        @DisplayName("REG-013: 验证通过")
        void testValidateMetricRequest_valid() {
            // Given
            MetricRegistrationService.ValidationResult result =
                    new MetricRegistrationService.ValidationResult(true, List.of(), List.of());
            when(metricRegistrationService.validateMetricRequest(any())).thenReturn(result);

            // When
            ResponseEntity<MetricRegistrationService.ValidationResult> response =
                    controller.validateMetricRequest(testRequest);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isValid());
        }
    }

    // ==================== 清理过期指标测试 ====================

    @Nested
    @DisplayName("POST /api/monitoring/metrics/cleanup - 清理过期指标测试")
    class PerformMetricCleanupTests {

        @Test
        @DisplayName("REG-014: 成功清理过期指标")
        void testPerformMetricCleanup_success() {
            // Given
            MetricRegistrationService.CleanupResult result =
                    new MetricRegistrationService.CleanupResult(5, 10, List.of("metric1", "metric2"));
            when(metricRegistrationService.performMetricCleanup()).thenReturn(result);

            // When
            ResponseEntity<MetricRegistrationService.CleanupResult> response =
                    controller.performMetricCleanup();

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(5, response.getBody().getCleanedMetrics());
        }
    }

    // ==================== 检查指标是否存在测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/metrics/exists/{metricName} - 检查指标是否存在测试")
    class CheckMetricExistsTests {

        @Test
        @DisplayName("REG-015: 指标存在")
        void testCheckMetricExists_exists() {
            // Given
            when(customMeterRegistry.meterExists(anyString(), anyMap())).thenReturn(true);

            // When
            ResponseEntity<Map<String, Object>> response = controller.checkMetricExists("test.metric", null);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue((Boolean) response.getBody().get("exists"));
        }

        @Test
        @DisplayName("REG-016: 指标不存在")
        void testCheckMetricExists_notExists() {
            // Given
            when(customMeterRegistry.meterExists(anyString(), anyMap())).thenReturn(false);

            // When
            ResponseEntity<Map<String, Object>> response = controller.checkMetricExists("nonexistent", null);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertFalse((Boolean) response.getBody().get("exists"));
        }
    }
}

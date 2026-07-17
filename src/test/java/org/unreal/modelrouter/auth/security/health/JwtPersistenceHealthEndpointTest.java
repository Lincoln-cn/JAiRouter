package org.unreal.modelrouter.auth.security.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.metrics.CleanupMetricsService;
import org.unreal.modelrouter.auth.security.metrics.JwtPersistenceMetricsService;
import org.unreal.modelrouter.auth.security.metrics.StorageHealthMetricsService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JwtPersistenceHealthEndpoint 单元测试
 */
@ExtendWith(MockitoExtension.class)
class JwtPersistenceHealthEndpointTest {

    @Mock
    private JwtPersistenceMetricsService metricsService;

    @Mock
    private StorageHealthMetricsService storageHealthService;

    @Mock
    private CleanupMetricsService cleanupMetricsService;

    @Mock
    private JwtPersistenceHealthIndicator healthIndicator;

    @InjectMocks
    private JwtPersistenceHealthEndpoint endpoint;

    @BeforeEach
    void setUp() {
        lenient().when(healthIndicator.getHealthStatus()).thenReturn(Map.of(
                "status", "UP",
                "overallStatus", "UP"
        ));
    }

    @Nested
    @DisplayName("health() 测试")
    class HealthTests {
        @Test
        @DisplayName("应返回 200 状态码")
        void shouldReturn200() {
            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertEquals(200, response.getStatusCodeValue());
        }

        @Test
        @DisplayName("应包含 timestamp 字段")
        void shouldContainTimestamp() {
            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertNotNull(response.getBody().get("timestamp"));
        }

        @Test
        @DisplayName("应包含 status 字段")
        void shouldContainStatus() {
            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertNotNull(response.getBody().get("status"));
        }

        @Test
        @DisplayName("应包含 core 健康信息")
        void shouldContainCoreHealth() {
            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertNotNull(response.getBody().get("core"));
        }

        @Test
        @DisplayName("metricsService 可用时应包含 metrics")
        void shouldContainMetricsWhenAvailable() {
            when(metricsService.getMetricsSummary()).thenReturn(Map.of("totalTokens", 1000));

            ResponseEntity<Map<String, Object>> response = endpoint.health();

            assertNotNull(response.getBody().get("metrics"));
            verify(metricsService).getMetricsSummary();
        }

        @Test
        @DisplayName("metricsService 不可用时不应包含 metrics")
        void shouldNotContainMetricsWhenUnavailable() {
            // metricsService is null (not injected)
            endpoint = new JwtPersistenceHealthEndpoint();
            // healthIndicator is null too, need to set it
            try {
                var field = JwtPersistenceHealthEndpoint.class.getDeclaredField("healthIndicator");
                field.setAccessible(true);
                field.set(endpoint, healthIndicator);
            } catch (Exception e) {
                fail("Failed to set healthIndicator field");
            }

            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertNull(response.getBody().get("metrics"));
        }

        @Test
        @DisplayName("storageHealthService 可用时应包含 storage")
        void shouldContainStorageWhenAvailable() {
            when(storageHealthService.getHealthSummary()).thenReturn(Map.of("compositeHealthy", true));

            ResponseEntity<Map<String, Object>> response = endpoint.health();

            assertNotNull(response.getBody().get("storage"));
        }

        @Test
        @DisplayName("cleanupMetricsService 可用时应包含 cleanup")
        void shouldContainCleanupWhenAvailable() {
            when(cleanupMetricsService.getCleanupStats()).thenReturn(Map.of("totalCleanups", 10));

            ResponseEntity<Map<String, Object>> response = endpoint.health();

            assertNotNull(response.getBody().get("cleanup"));
            verify(cleanupMetricsService).getCleanupStats();
        }

        @Test
        @DisplayName("core 状态为 UP 时整体状态应为 UP")
        void shouldReturnUpWhenCoreHealthy() {
            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertEquals("UP", response.getBody().get("status"));
        }

        @Test
        @DisplayName("core 状态为 DOWN 时整体状态应为 DOWN")
        void shouldReturnDownWhenCoreUnhealthy() {
            when(healthIndicator.getHealthStatus()).thenReturn(Map.of(
                    "status", "DOWN",
                    "overallStatus", "DOWN"
            ));

            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertEquals("DOWN", response.getBody().get("status"));
        }

        @Test
        @DisplayName("storage 不健康时整体状态应为 DOWN")
        void shouldReturnDownWhenStorageUnhealthy() {
            when(storageHealthService.getHealthSummary()).thenReturn(Map.of("compositeHealthy", false));

            ResponseEntity<Map<String, Object>> response = endpoint.health();
            assertEquals("DOWN", response.getBody().get("status"));
        }
    }

    @Nested
    @DisplayName("healthByComponent() 测试")
    class HealthByComponentTests {
        @Test
        @DisplayName("core 组件应返回核心健康状态")
        void shouldReturnCoreHealthForCoreComponent() {
            ResponseEntity<Map<String, Object>> response = endpoint.healthByComponent("core");

            assertEquals(200, response.getStatusCodeValue());
            assertEquals("core", response.getBody().get("component"));
            assertNotNull(response.getBody().get("status"));
        }

        @Test
        @DisplayName("metrics 组件可用时应返回指标摘要")
        void shouldReturnMetricsWhenAvailable() {
            when(metricsService.getMetricsSummary()).thenReturn(Map.of("totalTokens", 1000));

            ResponseEntity<Map<String, Object>> response = endpoint.healthByComponent("metrics");

            assertEquals("metrics", response.getBody().get("component"));
            assertNotNull(response.getBody().get("totalTokens"));
        }

        @Test
        @DisplayName("storage 组件可用时应返回存储健康状态")
        void shouldReturnStorageWhenAvailable() {
            when(storageHealthService.getHealthSummary()).thenReturn(Map.of("status", "UP"));

            ResponseEntity<Map<String, Object>> response = endpoint.healthByComponent("storage");

            assertEquals("storage", response.getBody().get("component"));
            assertNotNull(response.getBody().get("status"));
        }

        @Test
        @DisplayName("cleanup 组件可用时应返回清理统计")
        void shouldReturnCleanupWhenAvailable() {
            when(cleanupMetricsService.getCleanupStats()).thenReturn(Map.of("totalCleanups", 10));
            when(cleanupMetricsService.isCleanupHealthy()).thenReturn(true);

            ResponseEntity<Map<String, Object>> response = endpoint.healthByComponent("cleanup");

            assertEquals("cleanup", response.getBody().get("component"));
            assertNotNull(response.getBody().get("totalCleanups"));
            assertTrue((Boolean) response.getBody().get("cleanupHealthy"));
        }

        @Test
        @DisplayName("未知组件应返回 NOT_FOUND")
        void shouldReturnNotFoundForUnknownComponent() {
            ResponseEntity<Map<String, Object>> response = endpoint.healthByComponent("unknown");

            assertEquals("NOT_FOUND", response.getBody().get("status"));
            assertNotNull(response.getBody().get("availableComponents"));
        }

        @Test
        @DisplayName("组件名应按原样保存")
        void shouldPreserveComponentNameCase() {
            ResponseEntity<Map<String, Object>> response = endpoint.healthByComponent("CORE");

            assertEquals("CORE", response.getBody().get("component"));
        }
    }
}

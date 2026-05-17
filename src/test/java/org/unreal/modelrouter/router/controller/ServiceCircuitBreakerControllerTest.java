package org.unreal.modelrouter.router.controller;

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
import org.unreal.modelrouter.config.core.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ServiceCircuitBreakerController RESTful 接口测试
 *
 * 测试范围：
 * - GET /api/services/{serviceType}/circuitbreaker - 获取熔断配置
 * - PUT /api/services/{serviceType}/circuitbreaker - 更新熔断配置
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("ServiceCircuitBreakerController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceCircuitBreakerControllerTest {

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @InjectMocks
    private ServiceCircuitBreakerController controller;

    @BeforeEach
    void setUp() {
        // 配置模拟熔断配置
        CircuitBreakerConfiguration cbConfig = mock(CircuitBreakerConfiguration.class);
        when(cbConfig.toMap()).thenReturn(Map.of(
                "enabled", true,
                "failureThreshold", 5,
                "successThreshold", 3,
                "timeout", 30000
        ));

        ServiceConfiguration serviceConfig = mock(ServiceConfiguration.class);
        when(serviceConfig.circuitBreaker()).thenReturn(cbConfig);

        lenient().when(serviceConfigManager.getServiceConfiguration(anyString())).thenReturn(serviceConfig);
    }

    // ==================== 获取熔断配置测试 ====================

    @Nested
    @DisplayName("GET /api/services/{serviceType}/circuitbreaker - 获取熔断配置测试")
    class GetCircuitBreakerConfigTests {

        @Test
        @DisplayName("CB-001: 成功获取熔断配置")
        void testGetCircuitBreakerConfig_success() {
            // When
            ResponseEntity<Map<String, Object>> result = controller.getCircuitBreakerConfig("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue((Boolean) result.getBody().get("enabled"));
        }

        @Test
        @DisplayName("CB-002: 服务配置不存在返回空配置")
        void testGetCircuitBreakerConfig_notFound() {
            // Given
            when(serviceConfigManager.getServiceConfiguration(anyString())).thenReturn(null);

            // When
            ResponseEntity<Map<String, Object>> result = controller.getCircuitBreakerConfig("unknown");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isEmpty());
        }

        @Test
        @DisplayName("CB-003: 熔断配置为空返回空配置")
        void testGetCircuitBreakerConfig_nullConfig() {
            // Given
            ServiceConfiguration serviceConfig = mock(ServiceConfiguration.class);
            when(serviceConfig.circuitBreaker()).thenReturn(null);
            when(serviceConfigManager.getServiceConfiguration(anyString())).thenReturn(serviceConfig);

            // When
            ResponseEntity<Map<String, Object>> result = controller.getCircuitBreakerConfig("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isEmpty());
        }
    }

    // ==================== 更新熔断配置测试 ====================

    @Nested
    @DisplayName("PUT /api/services/{serviceType}/circuitbreaker - 更新熔断配置测试")
    class UpdateCircuitBreakerConfigTests {

        @Test
        @DisplayName("CB-004: 成功更新熔断配置")
        void testUpdateCircuitBreakerConfig_success() {
            // Given
            Map<String, Object> newConfig = Map.of(
                    "enabled", true,
                    "failureThreshold", 10,
                    "successThreshold", 5,
                    "timeout", 60000
            );

            // When
            ResponseEntity<Map<String, Object>> result = controller.updateCircuitBreakerConfig("chat", newConfig);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(newConfig, result.getBody());
        }

        @Test
        @DisplayName("CB-005: 更新部分配置")
        void testUpdateCircuitBreakerConfig_partial() {
            // Given
            Map<String, Object> partialConfig = Map.of(
                    "enabled", false
            );

            // When
            ResponseEntity<Map<String, Object>> result = controller.updateCircuitBreakerConfig("embedding", partialConfig);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(false, result.getBody().get("enabled"));
        }
    }
}

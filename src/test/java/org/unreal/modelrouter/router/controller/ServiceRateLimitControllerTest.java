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
import org.unreal.modelrouter.config.core.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ServiceRateLimitController RESTful 接口测试
 *
 * 测试范围：
 * - GET /api/services/{serviceType}/ratelimit - 获取限流配置
 * - PUT /api/services/{serviceType}/ratelimit - 更新限流配置
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("ServiceRateLimitController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRateLimitControllerTest {

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @InjectMocks
    private ServiceRateLimitController controller;

    @BeforeEach
    void setUp() {
        // 配置模拟限流配置
        RateLimitConfiguration rlConfig = mock(RateLimitConfiguration.class);
        when(rlConfig.toMap()).thenReturn(Map.of(
                "enabled", true,
                "requestsPerSecond", 100,
                "burstCapacity", 200,
                "queueCapacity", 50
        ));

        ServiceConfiguration serviceConfig = mock(ServiceConfiguration.class);
        when(serviceConfig.rateLimit()).thenReturn(rlConfig);

        lenient().when(serviceConfigManager.getServiceConfiguration(anyString())).thenReturn(serviceConfig);
    }

    // ==================== 获取限流配置测试 ====================

    @Nested
    @DisplayName("GET /api/services/{serviceType}/ratelimit - 获取限流配置测试")
    class GetRateLimitConfigTests {

        @Test
        @DisplayName("RL-001: 成功获取限流配置")
        void testGetRateLimitConfig_success() {
            // When
            ResponseEntity<Map<String, Object>> result = controller.getRateLimitConfig("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue((Boolean) result.getBody().get("enabled"));
            assertEquals(100, result.getBody().get("requestsPerSecond"));
        }

        @Test
        @DisplayName("RL-002: 服务配置不存在返回空配置")
        void testGetRateLimitConfig_notFound() {
            // Given
            when(serviceConfigManager.getServiceConfiguration(anyString())).thenReturn(null);

            // When
            ResponseEntity<Map<String, Object>> result = controller.getRateLimitConfig("unknown");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isEmpty());
        }

        @Test
        @DisplayName("RL-003: 限流配置为空返回空配置")
        void testGetRateLimitConfig_nullConfig() {
            // Given
            ServiceConfiguration serviceConfig = mock(ServiceConfiguration.class);
            when(serviceConfig.rateLimit()).thenReturn(null);
            when(serviceConfigManager.getServiceConfiguration(anyString())).thenReturn(serviceConfig);

            // When
            ResponseEntity<Map<String, Object>> result = controller.getRateLimitConfig("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isEmpty());
        }
    }

    // ==================== 更新限流配置测试 ====================

    @Nested
    @DisplayName("PUT /api/services/{serviceType}/ratelimit - 更新限流配置测试")
    class UpdateRateLimitConfigTests {

        @Test
        @DisplayName("RL-004: 成功更新限流配置")
        void testUpdateRateLimitConfig_success() {
            // Given
            Map<String, Object> newConfig = Map.of(
                    "enabled", true,
                    "requestsPerSecond", 200,
                    "burstCapacity", 400,
                    "queueCapacity", 100
            );

            // When
            ResponseEntity<Map<String, Object>> result = controller.updateRateLimitConfig("chat", newConfig);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(newConfig, result.getBody());
        }

        @Test
        @DisplayName("RL-005: 更新部分配置")
        void testUpdateRateLimitConfig_partial() {
            // Given
            Map<String, Object> partialConfig = Map.of(
                    "enabled", false
            );

            // When
            ResponseEntity<Map<String, Object>> result = controller.updateRateLimitConfig("embedding", partialConfig);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(false, result.getBody().get("enabled"));
        }

        @Test
        @DisplayName("RL-006: 更新QPS配置")
        void testUpdateRateLimitConfig_qpsOnly() {
            // Given
            Map<String, Object> qpsConfig = Map.of(
                    "requestsPerSecond", 500
            );

            // When
            ResponseEntity<Map<String, Object>> result = controller.updateRateLimitConfig("rerank", qpsConfig);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(500, result.getBody().get("requestsPerSecond"));
        }
    }
}

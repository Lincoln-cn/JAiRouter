package org.unreal.modelrouter.config.controller;

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
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.InstanceCircuitBreakerDTO;
import org.unreal.modelrouter.common.dto.InstanceRateLimitDTO;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.config.core.ServiceInstanceManager;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ServiceTypeInstanceController 单元测试
 *
 * <p>测试按服务类型获取实例配置功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("ServiceTypeInstanceController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceTypeInstanceControllerTest {

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private ServiceInstanceManager serviceInstanceManager;

    @InjectMocks
    private ServiceTypeInstanceController controller;

    private ServiceConfigEntity testServiceConfig;
    private ServiceInstanceDTO testInstance;
    private CreateServiceInstanceRequest testRequest;

    @BeforeEach
    void setUp() {
        testServiceConfig = new ServiceConfigEntity();
        testServiceConfig.setId(1L);
        testServiceConfig.setServiceType("chat");
        testServiceConfig.setIsLatest(true);

        testInstance = ServiceInstanceDTO.builder()
                .id(1L)
                .serviceConfigId(1L)
                .name("instance-001")
                .baseUrl("http://localhost:8080")
                .status("active")
                .build();

        testRequest = CreateServiceInstanceRequest.builder()
                .name("instance-001")
                .baseUrl("http://localhost:8080")
                .status("active")
                .build();
    }

    // ==================== 获取服务类型实例列表测试 ====================

    @Nested
    @DisplayName("GET /api/config/instance/{serviceType} - 获取服务类型实例列表测试")
    class GetInstancesByServiceTypeTests {

        @Test
        @DisplayName("STYPE-001: 成功获取实例列表")
        void testGetInstancesByServiceType_success() {
            // Given
            when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
                    .thenReturn(Optional.of(testServiceConfig));
            when(serviceInstanceManager.getInstancesByServiceConfigId(1L))
                    .thenReturn(List.of(testInstance));

            // When
            ResponseEntity<RouterResponse<List<ServiceInstanceDTO>>> result = controller.getInstancesByServiceType("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(1, result.getBody().getData().size());
        }

        @Test
        @DisplayName("STYPE-002: 服务配置不存在返回空列表")
        void testGetInstancesByServiceType_notFound() {
            // Given
            when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("nonexistent"))
                    .thenReturn(Optional.empty());

            // When
            ResponseEntity<RouterResponse<List<ServiceInstanceDTO>>> result = controller.getInstancesByServiceType("nonexistent");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertTrue(result.getBody().getData().isEmpty());
        }
    }

    // ==================== 更新实例配置测试 ====================

    @Nested
    @DisplayName("PUT /api/config/instance/{serviceType}/{instanceId} - 更新实例配置测试")
    class UpdateInstanceByServiceTypeTests {

        @Test
        @DisplayName("STYPE-003: 成功更新实例配置")
        void testUpdateInstanceByServiceType_success() {
            // Given
            when(serviceInstanceManager.updateInstance(anyLong(), any(CreateServiceInstanceRequest.class)))
                    .thenReturn(testInstance);

            // When
            ResponseEntity<RouterResponse<ServiceInstanceDTO>> result = controller.updateInstanceByServiceType("chat", 1L, testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals("1", result.getBody().getData().getInstanceId()); // deprecated method returns String
        }
    }

    // ==================== 添加实例测试 ====================

    @Nested
    @DisplayName("POST /api/config/instance/{serviceType} - 添加实例测试")
    class AddInstanceByServiceTypeTests {

        @Test
        @DisplayName("STYPE-004: 成功添加实例")
        void testAddInstanceByServiceType_success() {
            // Given
            when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
                    .thenReturn(Optional.of(testServiceConfig));
            when(serviceInstanceManager.createInstance(anyLong(), any(CreateServiceInstanceRequest.class)))
                    .thenReturn(testInstance);

            // When
            ResponseEntity<RouterResponse<ServiceInstanceDTO>> result = controller.addInstanceByServiceType("chat", testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
        }

        @Test
        @DisplayName("STYPE-005: 服务配置不存在返回错误")
        void testAddInstanceByServiceType_serviceNotFound() {
            // Given
            when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("nonexistent"))
                    .thenReturn(Optional.empty());

            // When
            ResponseEntity<RouterResponse<ServiceInstanceDTO>> result = controller.addInstanceByServiceType("nonexistent", testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("不存在"));
        }
    }

    // ==================== 删除实例测试 ====================

    @Nested
    @DisplayName("DELETE /api/config/instance/{serviceType}/{instanceId} - 删除实例测试")
    class DeleteInstanceByServiceTypeTests {

        @Test
        @DisplayName("STYPE-006: 成功删除实例")
        void testDeleteInstanceByServiceType_success() {
            // Given
            doNothing().when(serviceInstanceManager).deleteInstance(1L);

            // When
            ResponseEntity<RouterResponse<Void>> result = controller.deleteInstanceByServiceType("chat", 1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
        }
    }

    // ==================== 获取限流器配置测试 ====================

    @Nested
    @DisplayName("GET /api/config/instance/{serviceType}/{instanceId}/rate-limit - 获取限流器配置测试")
    class GetRateLimitConfigTests {

        @Test
        @DisplayName("STYPE-007: 成功获取限流器配置")
        void testGetRateLimitConfig_success() {
            // Given
            InstanceRateLimitDTO config = InstanceRateLimitDTO.builder()
                    .instanceId(1L)
                    .enabled(true)
                    .capacity(100)
                    .rate(10)
                    .build();
            when(serviceInstanceManager.getRateLimitConfig(1L)).thenReturn(Optional.of(config));

            // When
            ResponseEntity<RouterResponse<InstanceRateLimitDTO>> result = controller.getRateLimitConfig("chat", 1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertTrue(result.getBody().getData().getEnabled());
        }

        @Test
        @DisplayName("STYPE-008: 限流器配置不存在返回默认值")
        void testGetRateLimitConfig_default() {
            // Given
            when(serviceInstanceManager.getRateLimitConfig(1L)).thenReturn(Optional.empty());

            // When
            ResponseEntity<RouterResponse<InstanceRateLimitDTO>> result = controller.getRateLimitConfig("chat", 1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertFalse(result.getBody().getData().getEnabled());
        }
    }

    // ==================== 保存限流器配置测试 ====================

    @Nested
    @DisplayName("PUT /api/config/instance/{serviceType}/{instanceId}/rate-limit - 保存限流器配置测试")
    class SaveRateLimitConfigTests {

        @Test
        @DisplayName("STYPE-009: 成功保存限流器配置")
        void testSaveRateLimitConfig_success() {
            // Given
            InstanceRateLimitDTO config = InstanceRateLimitDTO.builder()
                    .instanceId(1L)
                    .enabled(true)
                    .capacity(200)
                    .rate(20)
                    .build();
            when(serviceInstanceManager.saveRateLimitConfig(anyLong(), any(InstanceRateLimitDTO.class)))
                    .thenReturn(config);

            // When
            ResponseEntity<RouterResponse<InstanceRateLimitDTO>> result = controller.saveRateLimitConfig("chat", 1L, config);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(200, result.getBody().getData().getCapacity());
        }
    }

    // ==================== 获取熔断器配置测试 ====================

    @Nested
    @DisplayName("GET /api/config/instance/{serviceType}/{instanceId}/circuit-breaker - 获取熔断器配置测试")
    class GetCircuitBreakerConfigTests {

        @Test
        @DisplayName("STYPE-010: 成功获取熔断器配置")
        void testGetCircuitBreakerConfig_success() {
            // Given
            InstanceCircuitBreakerDTO config = InstanceCircuitBreakerDTO.builder()
                    .instanceId(1L)
                    .enabled(true)
                    .failureThreshold(5)
                    .timeout(60000)
                    .build();
            when(serviceInstanceManager.getCircuitBreakerConfig(1L)).thenReturn(Optional.of(config));

            // When
            ResponseEntity<RouterResponse<InstanceCircuitBreakerDTO>> result = controller.getCircuitBreakerConfig("chat", 1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertTrue(result.getBody().getData().getEnabled());
        }

        @Test
        @DisplayName("STYPE-011: 熔断器配置不存在返回默认值")
        void testGetCircuitBreakerConfig_default() {
            // Given
            when(serviceInstanceManager.getCircuitBreakerConfig(1L)).thenReturn(Optional.empty());

            // When
            ResponseEntity<RouterResponse<InstanceCircuitBreakerDTO>> result = controller.getCircuitBreakerConfig("chat", 1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertFalse(result.getBody().getData().getEnabled());
        }
    }

    // ==================== 保存熔断器配置测试 ====================

    @Nested
    @DisplayName("PUT /api/config/instance/{serviceType}/{instanceId}/circuit-breaker - 保存熔断器配置测试")
    class SaveCircuitBreakerConfigTests {

        @Test
        @DisplayName("STYPE-012: 成功保存熔断器配置")
        void testSaveCircuitBreakerConfig_success() {
            // Given
            InstanceCircuitBreakerDTO config = InstanceCircuitBreakerDTO.builder()
                    .instanceId(1L)
                    .enabled(true)
                    .failureThreshold(10)
                    .timeout(30000)
                    .build();
            when(serviceInstanceManager.saveCircuitBreakerConfig(anyLong(), any(InstanceCircuitBreakerDTO.class)))
                    .thenReturn(config);

            // When
            ResponseEntity<RouterResponse<InstanceCircuitBreakerDTO>> result = controller.saveCircuitBreakerConfig("chat", 1L, config);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(10, result.getBody().getData().getFailureThreshold());
        }
    }
}

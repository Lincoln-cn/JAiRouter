package org.unreal.modelrouter.config.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.config.core.ServiceInstanceManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * InstanceConfigController RESTful 接口测试
 * 
 * 测试范围：
 * - GET /api/instance-configs/service/{serviceConfigId} - 获取服务实例配置
 * - POST /api/instance-configs/service/{serviceConfigId} - 创建实例配置
 * - PUT /api/instance-configs/{id} - 更新实例配置
 * - DELETE /api/instance-configs/{id} - 删除实例配置
 * 
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("InstanceConfigController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class InstanceConfigControllerIntegrationTest {

    @Mock
    private ServiceInstanceManager serviceInstanceManager;

    @InjectMocks
    private InstanceConfigController controller;

    private static final Long TEST_SERVICE_CONFIG_ID = 1L;
    private static final Long TEST_INSTANCE_ID = 100L;

    private ServiceInstanceDTO testInstance;
    private CreateServiceInstanceRequest testCreateRequest;

    @BeforeEach
    void setUp() {
        // 创建测试用的 ServiceInstanceDTO
        testInstance = ServiceInstanceDTO.builder()
                .id(TEST_INSTANCE_ID)
                .serviceConfigId(TEST_SERVICE_CONFIG_ID)
                .name("ollama-instance-1")
                .baseUrl("http://localhost:11434")
                .path("/v1/chat/completions")
                .weight(100)
                .status("active")
                .healthStatus("healthy")
                .build();

        // 创建测试用的 CreateServiceInstanceRequest
        testCreateRequest = CreateServiceInstanceRequest.builder()
                .name("ollama-instance-1")
                .baseUrl("http://localhost:11434")
                .path("/v1/chat/completions")
                .weight(100)
                .status("active")
                .build();
    }

    // ==================== 获取实例配置测试 ====================

    @Nested
    @DisplayName("GET /api/instance-configs/service/{serviceConfigId} - 获取实例配置测试")
    class GetInstanceConfigsTests {

        @Test
        @DisplayName("INST-001: 获取服务实例配置成功")
        void testGetInstanceConfigs_success() {
            // Given
            List<ServiceInstanceDTO> instances = Arrays.asList(testInstance);
            when(serviceInstanceManager.getInstancesByServiceConfigId(TEST_SERVICE_CONFIG_ID))
                    .thenReturn(instances);

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = 
                    controller.getInstanceConfigs(TEST_SERVICE_CONFIG_ID);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals(1, result.getBody().size());
            assertEquals("ollama-instance-1", result.getBody().get(0).getName());
        }

        @Test
        @DisplayName("INST-002: 空实例配置列表")
        void testGetInstanceConfigs_empty() {
            // Given
            when(serviceInstanceManager.getInstancesByServiceConfigId(anyLong()))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = 
                    controller.getInstanceConfigs(999L);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertTrue(result.getBody().isEmpty());
        }

        @Test
        @DisplayName("INST-003: 获取多个实例配置")
        void testGetInstanceConfigs_multiple() {
            // Given
            ServiceInstanceDTO instance1 = ServiceInstanceDTO.builder()
                    .id(1L)
                    .serviceConfigId(TEST_SERVICE_CONFIG_ID)
                    .name("instance-1")
                    .baseUrl("http://host1:11434")
                    .build();
            ServiceInstanceDTO instance2 = ServiceInstanceDTO.builder()
                    .id(2L)
                    .serviceConfigId(TEST_SERVICE_CONFIG_ID)
                    .name("instance-2")
                    .baseUrl("http://host2:11434")
                    .build();

            when(serviceInstanceManager.getInstancesByServiceConfigId(TEST_SERVICE_CONFIG_ID))
                    .thenReturn(Arrays.asList(instance1, instance2));

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = 
                    controller.getInstanceConfigs(TEST_SERVICE_CONFIG_ID);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals(2, result.getBody().size());
        }
    }

    // ==================== 创建实例配置测试 ====================

    @Nested
    @DisplayName("POST /api/instance-configs/service/{serviceConfigId} - 创建实例配置测试")
    class CreateInstanceConfigTests {

        @Test
        @DisplayName("INST-004: 创建实例配置成功")
        void testCreateInstanceConfig_success() {
            // Given
            when(serviceInstanceManager.createInstance(anyLong(), any()))
                    .thenReturn(testInstance);

            // When
            ResponseEntity<ServiceInstanceDTO> result = 
                    controller.createInstanceConfig(TEST_SERVICE_CONFIG_ID, testCreateRequest);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals("ollama-instance-1", result.getBody().getName());
            assertEquals("http://localhost:11434", result.getBody().getBaseUrl());
            verify(serviceInstanceManager).createInstance(TEST_SERVICE_CONFIG_ID, testCreateRequest);
        }

        @Test
        @DisplayName("INST-005: 创建带自定义头部的实例")
        void testCreateInstanceConfig_withHeaders() {
            // Given
            CreateServiceInstanceRequest requestWithHeaders = CreateServiceInstanceRequest.builder()
                    .name("custom-instance")
                    .baseUrl("http://custom:11434")
                    .headers(java.util.Map.of("X-Custom-Header", "value"))
                    .build();

            ServiceInstanceDTO instanceWithHeaders = ServiceInstanceDTO.builder()
                    .id(101L)
                    .serviceConfigId(TEST_SERVICE_CONFIG_ID)
                    .name("custom-instance")
                    .baseUrl("http://custom:11434")
                    .headers(java.util.Map.of("X-Custom-Header", "value"))
                    .build();

            when(serviceInstanceManager.createInstance(anyLong(), any()))
                    .thenReturn(instanceWithHeaders);

            // When
            ResponseEntity<ServiceInstanceDTO> result = 
                    controller.createInstanceConfig(TEST_SERVICE_CONFIG_ID, requestWithHeaders);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertNotNull(result.getBody().getHeaders());
            assertEquals("value", result.getBody().getHeaders().get("X-Custom-Header"));
        }
    }

    // ==================== 更新实例配置测试 ====================

    @Nested
    @DisplayName("PUT /api/instance-configs/{id} - 更新实例配置测试")
    class UpdateInstanceConfigTests {

        @Test
        @DisplayName("INST-006: 更新实例配置成功")
        void testUpdateInstanceConfig_success() {
            // Given
            CreateServiceInstanceRequest updateRequest = CreateServiceInstanceRequest.builder()
                    .name("ollama-instance-1-updated")
                    .baseUrl("http://new-host:11434")
                    .weight(50)
                    .build();

            ServiceInstanceDTO updatedInstance = ServiceInstanceDTO.builder()
                    .id(TEST_INSTANCE_ID)
                    .serviceConfigId(TEST_SERVICE_CONFIG_ID)
                    .name("ollama-instance-1-updated")
                    .baseUrl("http://new-host:11434")
                    .weight(50)
                    .build();

            when(serviceInstanceManager.updateInstance(anyLong(), any()))
                    .thenReturn(updatedInstance);

            // When
            ResponseEntity<ServiceInstanceDTO> result = 
                    controller.updateInstanceConfig(TEST_INSTANCE_ID, updateRequest);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals("ollama-instance-1-updated", result.getBody().getName());
            assertEquals(50, result.getBody().getWeight());
        }
    }

    // ==================== 删除实例配置测试 ====================

    @Nested
    @DisplayName("DELETE /api/instance-configs/{id} - 删除实例配置测试")
    class DeleteInstanceConfigTests {

        @Test
        @DisplayName("INST-007: 删除实例配置成功")
        void testDeleteInstanceConfig_success() {
            // Given
            doNothing().when(serviceInstanceManager).deleteInstance(TEST_INSTANCE_ID);

            // When
            ResponseEntity<Void> result = controller.deleteInstanceConfig(TEST_INSTANCE_ID);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            verify(serviceInstanceManager).deleteInstance(TEST_INSTANCE_ID);
        }

        @Test
        @DisplayName("INST-008: 删除不存在的实例")
        void testDeleteInstanceConfig_nonexistent() {
            // Given
            doNothing().when(serviceInstanceManager).deleteInstance(999L);

            // When
            ResponseEntity<Void> result = controller.deleteInstanceConfig(999L);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
        }
    }
}

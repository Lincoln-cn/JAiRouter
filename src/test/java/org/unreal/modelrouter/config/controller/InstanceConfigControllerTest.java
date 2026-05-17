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
import org.unreal.modelrouter.config.core.ServiceInstanceManager;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * InstanceConfigController 单元测试
 *
 * <p>测试实例配置的 CRUD 操作</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("InstanceConfigController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstanceConfigControllerTest {

    @Mock
    private ServiceInstanceManager serviceInstanceManager;

    @InjectMocks
    private InstanceConfigController controller;

    private ServiceInstanceDTO testInstance;
    private CreateServiceInstanceRequest testRequest;

    @BeforeEach
    void setUp() {
        testInstance = ServiceInstanceDTO.builder()
                .id(1L)
                .serviceConfigId(100L)
                .name("test-instance")
                .baseUrl("http://localhost:8080")
                .build();

        testRequest = CreateServiceInstanceRequest.builder()
                .name("test-instance")
                .baseUrl("http://localhost:8080")
                .build();
    }

    // ==================== 获取实例配置列表测试 ====================

    @Nested
    @DisplayName("GET /api/instance-configs/service/{serviceConfigId} - 获取实例配置列表测试")
    class GetInstanceConfigsTests {

        @Test
        @DisplayName("INSTANCE-001: 成功获取实例配置列表")
        void testGetInstanceConfigs_success() {
            // Given
            when(serviceInstanceManager.getInstancesByServiceConfigId(100L))
                    .thenReturn(List.of(testInstance));

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = controller.getInstanceConfigs(100L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(1, result.getBody().size());
            assertEquals("test-instance", result.getBody().get(0).getName());
        }

        @Test
        @DisplayName("INSTANCE-002: 获取空列表")
        void testGetInstanceConfigs_empty() {
            // Given
            when(serviceInstanceManager.getInstancesByServiceConfigId(anyLong()))
                    .thenReturn(List.of());

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = controller.getInstanceConfigs(100L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isEmpty());
        }
    }

    // ==================== 创建实例配置测试 ====================

    @Nested
    @DisplayName("POST /api/instance-configs/service/{serviceConfigId} - 创建实例配置测试")
    class CreateInstanceConfigTests {

        @Test
        @DisplayName("INSTANCE-003: 成功创建实例配置")
        void testCreateInstanceConfig_success() {
            // Given
            when(serviceInstanceManager.createInstance(anyLong(), any()))
                    .thenReturn(testInstance);

            // When
            ResponseEntity<ServiceInstanceDTO> result = controller.createInstanceConfig(100L, testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals("test-instance", result.getBody().getName());
        }
    }

    // ==================== 更新实例配置测试 ====================

    @Nested
    @DisplayName("PUT /api/instance-configs/{id} - 更新实例配置测试")
    class UpdateInstanceConfigTests {

        @Test
        @DisplayName("INSTANCE-004: 成功更新实例配置")
        void testUpdateInstanceConfig_success() {
            // Given
            ServiceInstanceDTO updated = ServiceInstanceDTO.builder()
                    .id(1L)
                    .name("updated-instance")
                    .baseUrl("http://localhost:9090")
                    .build();
            when(serviceInstanceManager.updateInstance(anyLong(), any()))
                    .thenReturn(updated);

            // When
            ResponseEntity<ServiceInstanceDTO> result = controller.updateInstanceConfig(1L, testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals("updated-instance", result.getBody().getName());
        }
    }

    // ==================== 删除实例配置测试 ====================

    @Nested
    @DisplayName("DELETE /api/instance-configs/{id} - 删除实例配置测试")
    class DeleteInstanceConfigTests {

        @Test
        @DisplayName("INSTANCE-005: 成功删除实例配置")
        void testDeleteInstanceConfig_success() {
            // Given
            doNothing().when(serviceInstanceManager).deleteInstance(1L);

            // When
            ResponseEntity<Void> result = controller.deleteInstanceConfig(1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(serviceInstanceManager, times(1)).deleteInstance(1L);
        }
    }
}

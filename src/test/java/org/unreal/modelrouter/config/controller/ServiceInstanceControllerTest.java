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
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.config.core.ServiceInstanceManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ServiceInstanceController 单元测试
 * 
 * <p>测试服务实例的 CRUD 操作</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("ServiceInstanceController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceInstanceControllerTest {

    @Mock
    private ServiceInstanceManager serviceInstanceManager;

    @InjectMocks
    private ServiceInstanceController controller;

    private ServiceInstanceDTO testInstance;
    private CreateServiceInstanceRequest createRequest;

    @BeforeEach
    void setUp() {
        testInstance = new ServiceInstanceDTO();
        testInstance.setId(1L);
        testInstance.setName("test-instance");
        testInstance.setBaseUrl("http://localhost:8080");
        testInstance.setServiceConfigId(100L);

        createRequest = new CreateServiceInstanceRequest();
        createRequest.setName("new-instance");
        createRequest.setBaseUrl("http://localhost:8081");
    }

    // ==================== 获取所有实例测试 ====================

    @Nested
    @DisplayName("GET /api/instances - 获取所有实例测试")
    class GetAllInstancesTests {

        @Test
        @DisplayName("INSTANCE-001: 成功获取所有实例")
        void testGetAllInstances_success() {
            // Given
            when(serviceInstanceManager.getAllInstances()).thenReturn(List.of(testInstance));

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = controller.getAllInstances();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(1, result.getBody().size());
        }

        @Test
        @DisplayName("INSTANCE-002: 空实例列表")
        void testGetAllInstances_empty() {
            // Given
            when(serviceInstanceManager.getAllInstances()).thenReturn(List.of());

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = controller.getAllInstances();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isEmpty());
        }
    }

    // ==================== 按服务获取实例测试 ====================

    @Nested
    @DisplayName("GET /api/instances/service/{serviceConfigId} - 按服务获取实例测试")
    class GetInstancesByServiceTests {

        @Test
        @DisplayName("INSTANCE-003: 成功获取指定服务的实例")
        void testGetInstancesByService_success() {
            // Given
            when(serviceInstanceManager.getInstancesByServiceConfigId(100L)).thenReturn(List.of(testInstance));

            // When
            ResponseEntity<List<ServiceInstanceDTO>> result = controller.getInstancesByService(100L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(1, result.getBody().size());
        }
    }

    // ==================== 获取单个实例测试 ====================

    @Nested
    @DisplayName("GET /api/instances/{id} - 获取单个实例测试")
    class GetInstanceTests {

        @Test
        @DisplayName("INSTANCE-004: 成功获取实例")
        void testGetInstance_success() {
            // Given
            when(serviceInstanceManager.getInstance(1L)).thenReturn(Optional.of(testInstance));

            // When
            ResponseEntity<ServiceInstanceDTO> result = controller.getInstance(1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(1L, result.getBody().getId());
        }

        @Test
        @DisplayName("INSTANCE-005: 实例不存在返回404")
        void testGetInstance_notFound() {
            // Given
            when(serviceInstanceManager.getInstance(999L)).thenReturn(Optional.empty());

            // When
            ResponseEntity<ServiceInstanceDTO> result = controller.getInstance(999L);

            // Then
            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        }
    }

    // ==================== 创建实例测试 ====================

    @Nested
    @DisplayName("POST /api/instances/service/{serviceConfigId} - 创建实例测试")
    class CreateInstanceTests {

        @Test
        @DisplayName("INSTANCE-006: 成功创建实例")
        void testCreateInstance_success() {
            // Given
            when(serviceInstanceManager.createInstance(anyLong(), any())).thenReturn(testInstance);

            // When
            ResponseEntity<ServiceInstanceDTO> result = controller.createInstance(100L, createRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
        }
    }

    // ==================== 更新实例测试 ====================

    @Nested
    @DisplayName("PUT /api/instances/{id} - 更新实例测试")
    class UpdateInstanceTests {

        @Test
        @DisplayName("INSTANCE-007: 成功更新实例")
        void testUpdateInstance_success() {
            // Given
            when(serviceInstanceManager.updateInstance(anyLong(), any())).thenReturn(testInstance);

            // When
            ResponseEntity<ServiceInstanceDTO> result = controller.updateInstance(1L, createRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
        }
    }

    // ==================== 删除实例测试 ====================

    @Nested
    @DisplayName("DELETE /api/instances/{id} - 删除实例测试")
    class DeleteInstanceTests {

        @Test
        @DisplayName("INSTANCE-008: 成功删除实例")
        void testDeleteInstance_success() {
            // Given
            doNothing().when(serviceInstanceManager).deleteInstance(1L);

            // When
            ResponseEntity<Void> result = controller.deleteInstance(1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(serviceInstanceManager).deleteInstance(1L);
        }
    }

    // ==================== 更新健康状态测试 ====================

    @Nested
    @DisplayName("POST /api/instances/{id}/health - 更新健康状态测试")
    class UpdateHealthStatusTests {

        @Test
        @DisplayName("INSTANCE-009: 成功更新健康状态")
        void testUpdateHealthStatus_success() {
            // Given
            Map<String, String> healthData = Map.of(
                    "healthStatus", "HEALTHY",
                    "errorMessage", ""
            );
            doNothing().when(serviceInstanceManager).updateHealthStatus(anyLong(), anyString(), anyString());

            // When
            ResponseEntity<Void> result = controller.updateHealthStatus(1L, healthData);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(serviceInstanceManager).updateHealthStatus(1L, "HEALTHY", "");
        }
    }
}

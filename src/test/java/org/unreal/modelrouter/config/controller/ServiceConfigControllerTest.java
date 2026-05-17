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
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.config.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.config.dto.ServiceConfigDTO;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ServiceConfigController 单元测试
 *
 * <p>测试服务配置管理功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("ServiceConfigController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceConfigControllerTest {

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @InjectMocks
    private ServiceConfigController controller;

    private ServiceConfigDTO testServiceConfig;
    private CreateServiceConfigRequest testRequest;

    @BeforeEach
    void setUp() {
        testServiceConfig = ServiceConfigDTO.builder()
                .id(1L)
                .serviceType("chat")
                .adapter("openai")
                .loadBalanceType("round-robin")
                .build();

        testRequest = CreateServiceConfigRequest.builder()
                .serviceType("chat")
                .adapter("openai")
                .loadBalanceType("round-robin")
                .build();
    }

    // ==================== 获取所有服务配置测试 ====================

    @Nested
    @DisplayName("GET /api/services - 获取所有服务配置测试")
    class GetAllServicesTests {

        @Test
        @DisplayName("SERVICE-001: 成功获取所有服务配置")
        void testGetAllServices_success() {
            // Given
            when(serviceConfigManager.getAllServiceConfigs()).thenReturn(List.of(testServiceConfig));

            // When
            ResponseEntity<List<ServiceConfigDTO>> result = controller.getAllServices();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(1, result.getBody().size());
            assertEquals("chat", result.getBody().get(0).getServiceType());
        }

        @Test
        @DisplayName("SERVICE-002: 空列表返回成功")
        void testGetAllServices_emptyList() {
            // Given
            when(serviceConfigManager.getAllServiceConfigs()).thenReturn(List.of());

            // When
            ResponseEntity<List<ServiceConfigDTO>> result = controller.getAllServices();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isEmpty());
        }
    }

    // ==================== 获取指定服务配置测试 ====================

    @Nested
    @DisplayName("GET /api/services/{serviceType} - 获取指定服务配置测试")
    class GetServiceTests {

        @Test
        @DisplayName("SERVICE-003: 成功获取服务配置")
        void testGetService_success() {
            // Given
            when(serviceConfigManager.getServiceConfig("chat")).thenReturn(Optional.of(testServiceConfig));

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.getService("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("chat", result.getBody().getServiceType());
        }

        @Test
        @DisplayName("SERVICE-004: 服务配置不存在返回404")
        void testGetService_notFound() {
            // Given
            when(serviceConfigManager.getServiceConfig("nonexistent")).thenReturn(Optional.empty());

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.getService("nonexistent");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        }
    }

    // ==================== 创建/更新服务配置测试 ====================

    @Nested
    @DisplayName("POST /api/services/{serviceType} - 创建/更新服务配置测试")
    class SaveServiceTests {

        @Test
        @DisplayName("SERVICE-005: 成功创建服务配置")
        void testSaveService_create() {
            // Given
            when(serviceConfigManager.saveServiceConfig(anyString(), any(CreateServiceConfigRequest.class)))
                    .thenReturn(testServiceConfig);

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.saveService("chat", testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("chat", result.getBody().getServiceType());
        }

        @Test
        @DisplayName("SERVICE-006: 成功更新服务配置")
        void testSaveService_update() {
            // Given
            ServiceConfigDTO updated = ServiceConfigDTO.builder()
                    .id(1L)
                    .serviceType("chat")
                    .adapter("openai")
                    .loadBalanceType("weighted")
                    .build();

            when(serviceConfigManager.saveServiceConfig(anyString(), any(CreateServiceConfigRequest.class)))
                    .thenReturn(updated);

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.saveService("chat", testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("weighted", result.getBody().getLoadBalanceType());
        }
    }

    // ==================== 删除服务配置测试 ====================

    @Nested
    @DisplayName("DELETE /api/services/{serviceType} - 删除服务配置测试")
    class DeleteServiceTests {

        @Test
        @DisplayName("SERVICE-007: 成功删除服务配置")
        void testDeleteService_success() {
            // Given
            doNothing().when(serviceConfigManager).deleteServiceConfig("chat");

            // When
            ResponseEntity<Void> result = controller.deleteService("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(serviceConfigManager, times(1)).deleteServiceConfig("chat");
        }
    }
}

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
import org.unreal.modelrouter.config.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.config.dto.ServiceConfigDTO;
import org.unreal.modelrouter.config.core.ServiceConfigManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ServiceConfigController RESTful 接口测试
 * 
 * 测试范围：
 * - GET /api/services - 获取所有服务配置
 * - GET /api/services/{serviceType} - 获取指定服务配置
 * - POST /api/services/{serviceType} - 创建/更新服务配置
 * - DELETE /api/services/{serviceType} - 删除服务配置
 * 
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("ServiceConfigController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class ServiceConfigControllerIntegrationTest {

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @InjectMocks
    private ServiceConfigController controller;

    private static final String BASE_URL = "/api/services";
    private static final String TEST_SERVICE_TYPE = "ollama";

    private ServiceConfigDTO testServiceConfig;
    private CreateServiceConfigRequest testCreateRequest;

    @BeforeEach
    void setUp() {
        // 创建测试用的 ServiceConfigDTO
        testServiceConfig = ServiceConfigDTO.builder()
                .id(1L)
                .configKey("ollama-config")
                .serviceType(TEST_SERVICE_TYPE)
                .adapter("OllamaAdapter")
                .loadBalanceType("round-robin")
                .version(1)
                .isLatest(true)
                .build();

        // 创建测试用的 CreateServiceConfigRequest
        testCreateRequest = CreateServiceConfigRequest.builder()
                .serviceType(TEST_SERVICE_TYPE)
                .adapter("OllamaAdapter")
                .loadBalanceType("round-robin")
                .instances(Collections.emptyList())
                .build();
    }

    // ==================== 获取所有服务配置测试 ====================

    @Nested
    @DisplayName("GET /api/services - 获取所有服务配置测试")
    class GetAllServicesTests {

        @Test
        @DisplayName("SVC-001: 获取所有服务配置成功")
        void testGetAllServices_success() {
            // Given
            List<ServiceConfigDTO> configs = Arrays.asList(testServiceConfig);
            when(serviceConfigManager.getAllServiceConfigs()).thenReturn(configs);

            // When
            ResponseEntity<List<ServiceConfigDTO>> result = controller.getAllServices();

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals(1, result.getBody().size());
            assertEquals(TEST_SERVICE_TYPE, result.getBody().get(0).getServiceType());
            assertEquals("OllamaAdapter", result.getBody().get(0).getAdapter());
        }

        @Test
        @DisplayName("SVC-002: 空服务配置列表")
        void testGetAllServices_empty() {
            // Given
            when(serviceConfigManager.getAllServiceConfigs()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<ServiceConfigDTO>> result = controller.getAllServices();

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertTrue(result.getBody().isEmpty());
        }

        @Test
        @DisplayName("SVC-003: 获取多个服务配置")
        void testGetAllServices_multiple() {
            // Given
            ServiceConfigDTO config1 = ServiceConfigDTO.builder()
                    .serviceType("ollama")
                    .adapter("OllamaAdapter")
                    .build();
            ServiceConfigDTO config2 = ServiceConfigDTO.builder()
                    .serviceType("vllm")
                    .adapter("VllmAdapter")
                    .build();

            when(serviceConfigManager.getAllServiceConfigs())
                    .thenReturn(Arrays.asList(config1, config2));

            // When
            ResponseEntity<List<ServiceConfigDTO>> result = controller.getAllServices();

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals(2, result.getBody().size());
        }
    }

    // ==================== 获取指定服务配置测试 ====================

    @Nested
    @DisplayName("GET /api/services/{serviceType} - 获取指定服务配置测试")
    class GetServiceTests {

        @Test
        @DisplayName("SVC-004: 获取指定服务配置成功")
        void testGetService_success() {
            // Given
            when(serviceConfigManager.getServiceConfig(TEST_SERVICE_TYPE))
                    .thenReturn(Optional.of(testServiceConfig));

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.getService(TEST_SERVICE_TYPE);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals(TEST_SERVICE_TYPE, result.getBody().getServiceType());
            assertEquals("OllamaAdapter", result.getBody().getAdapter());
        }

        @Test
        @DisplayName("SVC-005: 获取不存在的服务配置")
        void testGetService_notFound() {
            // Given
            when(serviceConfigManager.getServiceConfig("nonexistent"))
                    .thenReturn(Optional.empty());

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.getService("nonexistent");

            // Then
            assertTrue(result.getStatusCode().is4xxClientError());
            assertEquals(404, result.getStatusCode().value());
        }
    }

    // ==================== 创建/更新服务配置测试 ====================

    @Nested
    @DisplayName("POST /api/services/{serviceType} - 创建/更新服务配置测试")
    class SaveServiceTests {

        @Test
        @DisplayName("SVC-006: 创建服务配置成功")
        void testSaveService_success() {
            // Given
            when(serviceConfigManager.saveServiceConfig(anyString(), any()))
                    .thenReturn(testServiceConfig);

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.saveService(TEST_SERVICE_TYPE, testCreateRequest);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals(TEST_SERVICE_TYPE, result.getBody().getServiceType());
            verify(serviceConfigManager).saveServiceConfig(TEST_SERVICE_TYPE, testCreateRequest);
        }

        @Test
        @DisplayName("SVC-007: 更新现有服务配置")
        void testSaveService_update() {
            // Given
            ServiceConfigDTO updatedConfig = ServiceConfigDTO.builder()
                    .id(1L)
                    .configKey("ollama-config-v2")
                    .serviceType(TEST_SERVICE_TYPE)
                    .adapter("OllamaAdapter")
                    .loadBalanceType("weighted")
                    .version(2)
                    .isLatest(true)
                    .build();

            CreateServiceConfigRequest updateRequest = CreateServiceConfigRequest.builder()
                    .serviceType(TEST_SERVICE_TYPE)
                    .adapter("OllamaAdapter")
                    .loadBalanceType("weighted")
                    .instances(Collections.emptyList())
                    .build();

            when(serviceConfigManager.saveServiceConfig(anyString(), any()))
                    .thenReturn(updatedConfig);

            // When
            ResponseEntity<ServiceConfigDTO> result = controller.saveService(TEST_SERVICE_TYPE, updateRequest);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            assertEquals("weighted", result.getBody().getLoadBalanceType());
            assertEquals(2, result.getBody().getVersion());
        }
    }

    // ==================== 删除服务配置测试 ====================

    @Nested
    @DisplayName("DELETE /api/services/{serviceType} - 删除服务配置测试")
    class DeleteServiceTests {

        @Test
        @DisplayName("SVC-008: 删除服务配置成功")
        void testDeleteService_success() {
            // Given
            doNothing().when(serviceConfigManager).deleteServiceConfig(TEST_SERVICE_TYPE);

            // When
            ResponseEntity<Void> result = controller.deleteService(TEST_SERVICE_TYPE);

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
            verify(serviceConfigManager).deleteServiceConfig(TEST_SERVICE_TYPE);
        }

        @Test
        @DisplayName("SVC-009: 删除不存在的服务配置")
        void testDeleteService_nonexistent() {
            // Given - ServiceConfigManager 允许删除不存在的配置
            doNothing().when(serviceConfigManager).deleteServiceConfig("nonexistent");

            // When
            ResponseEntity<Void> result = controller.deleteService("nonexistent");

            // Then
            assertTrue(result.getStatusCode().is2xxSuccessful());
        }
    }
}

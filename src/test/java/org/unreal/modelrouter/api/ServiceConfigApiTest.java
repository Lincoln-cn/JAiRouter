package org.unreal.modelrouter.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.config.controller.ServiceConfigController;
import org.unreal.modelrouter.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.dto.ServiceConfigDTO;
import org.unreal.modelrouter.config.core.ServiceConfigManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ServiceConfigController API 测试
 * v1.5.3: 验证服务配置 API 可用性
 */
@ExtendWith(MockitoExtension.class)
class ServiceConfigApiTest {

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @InjectMocks
    private ServiceConfigController serviceConfigController;

    @Test
    void shouldGetAllServices() {
        // Given
        List<ServiceConfigDTO> configs = Arrays.asList(
                createServiceConfigDTO("chat", "openai"),
                createServiceConfigDTO("embedding", "openai")
        );
        when(serviceConfigManager.getAllServiceConfigs()).thenReturn(configs);

        // When
        ResponseEntity<List<ServiceConfigDTO>> response = serviceConfigController.getAllServices();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getServiceType()).isEqualTo("chat");
    }

    @Test
    void shouldGetServiceByType() {
        // Given
        ServiceConfigDTO config = createServiceConfigDTO("chat", "openai");
        when(serviceConfigManager.getServiceConfig("chat")).thenReturn(Optional.of(config));

        // When
        ResponseEntity<ServiceConfigDTO> response = serviceConfigController.getService("chat");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getServiceType()).isEqualTo("chat");
    }

    @Test
    void shouldReturn404WhenServiceNotFound() {
        // Given
        when(serviceConfigManager.getServiceConfig("unknown")).thenReturn(Optional.empty());

        // When
        ResponseEntity<ServiceConfigDTO> response = serviceConfigController.getService("unknown");

        // Then
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void shouldCreateService() {
        // Given
        CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType("chat")
                .adapter("openai")
                .loadBalanceType("round-robin")
                .build();
        ServiceConfigDTO created = createServiceConfigDTO("chat", "openai");
        when(serviceConfigManager.saveServiceConfig(eq("chat"), any(CreateServiceConfigRequest.class)))
                .thenReturn(created);

        // When
        ResponseEntity<ServiceConfigDTO> response = serviceConfigController.saveService("chat", request);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getServiceType()).isEqualTo("chat");
    }

    @Test
    void shouldDeleteService() {
        // Given
        doNothing().when(serviceConfigManager).deleteServiceConfig("chat");

        // When
        ResponseEntity<Void> response = serviceConfigController.deleteService("chat");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(serviceConfigManager).deleteServiceConfig("chat");
    }

    private ServiceConfigDTO createServiceConfigDTO(String serviceType, String adapter) {
        return ServiceConfigDTO.builder()
                .id(1L)
                .configKey("model-router-config")
                .serviceType(serviceType)
                .adapter(adapter)
                .loadBalanceType("round-robin")
                .version(1)
                .isLatest(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}

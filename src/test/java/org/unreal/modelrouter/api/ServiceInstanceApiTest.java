package org.unreal.modelrouter.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.config.controller.ServiceInstanceController;
import org.unreal.modelrouter.common.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.config.core.ServiceInstanceManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ServiceInstanceController API 测试
 * v1.5.3: 验证服务实例 API 可用性
 */
@ExtendWith(MockitoExtension.class)
class ServiceInstanceApiTest {

    @Mock
    private ServiceInstanceManager serviceInstanceManager;

    @InjectMocks
    private ServiceInstanceController serviceInstanceController;

    @Test
    void shouldGetAllInstances() {
        // Given
        List<ServiceInstanceDTO> instances = Arrays.asList(
                createServiceInstanceDTO(1L, "instance-1"),
                createServiceInstanceDTO(2L, "instance-2")
        );
        when(serviceInstanceManager.getAllInstances()).thenReturn(instances);

        // When
        ResponseEntity<List<ServiceInstanceDTO>> response = serviceInstanceController.getAllInstances();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void shouldGetInstancesByService() {
        // Given
        Long serviceConfigId = 1L;
        List<ServiceInstanceDTO> instances = Arrays.asList(
                createServiceInstanceDTO(1L, "instance-1"),
                createServiceInstanceDTO(2L, "instance-2")
        );
        when(serviceInstanceManager.getInstancesByServiceConfigId(serviceConfigId)).thenReturn(instances);

        // When
        ResponseEntity<List<ServiceInstanceDTO>> response = 
                serviceInstanceController.getInstancesByService(serviceConfigId);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void shouldGetInstanceById() {
        // Given
        Long instanceId = 1L;
        ServiceInstanceDTO instance = createServiceInstanceDTO(instanceId, "instance-1");
        when(serviceInstanceManager.getInstance(instanceId)).thenReturn(Optional.of(instance));

        // When
        ResponseEntity<ServiceInstanceDTO> response = serviceInstanceController.getInstance(instanceId);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(instanceId);
    }

    @Test
    void shouldReturn404WhenInstanceNotFound() {
        // Given
        when(serviceInstanceManager.getInstance(999L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<ServiceInstanceDTO> response = serviceInstanceController.getInstance(999L);

        // Then
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void shouldCreateInstance() {
        // Given
        Long serviceConfigId = 1L;
        CreateServiceInstanceRequest request = CreateServiceInstanceRequest.builder()
                .name("new-instance")
                .baseUrl("http://localhost:8080")
                .path("/api")
                .weight(1)
                .build();
        ServiceInstanceDTO created = createServiceInstanceDTO(1L, "new-instance");
        when(serviceInstanceManager.createInstance(eq(serviceConfigId), any(CreateServiceInstanceRequest.class)))
                .thenReturn(created);

        // When
        ResponseEntity<ServiceInstanceDTO> response = 
                serviceInstanceController.createInstance(serviceConfigId, request);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldUpdateInstance() {
        // Given
        Long instanceId = 1L;
        CreateServiceInstanceRequest request = CreateServiceInstanceRequest.builder()
                .name("updated-instance")
                .baseUrl("http://localhost:9090")
                .path("/api/v2")
                .weight(2)
                .build();
        ServiceInstanceDTO updated = createServiceInstanceDTO(instanceId, "updated-instance");
        when(serviceInstanceManager.updateInstance(eq(instanceId), any(CreateServiceInstanceRequest.class)))
                .thenReturn(updated);

        // When
        ResponseEntity<ServiceInstanceDTO> response = serviceInstanceController.updateInstance(instanceId, request);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldDeleteInstance() {
        // Given
        Long instanceId = 1L;
        doNothing().when(serviceInstanceManager).deleteInstance(instanceId);

        // When
        ResponseEntity<Void> response = serviceInstanceController.deleteInstance(instanceId);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(serviceInstanceManager).deleteInstance(instanceId);
    }

    @Test
    void shouldUpdateHealthStatus() {
        // Given
        Long instanceId = 1L;
        Map<String, String> healthData = new HashMap<>();
        healthData.put("healthStatus", "HEALTHY");
        healthData.put("errorMessage", "");
        doNothing().when(serviceInstanceManager).updateHealthStatus(instanceId, "HEALTHY", "");

        // When
        ResponseEntity<Void> response = serviceInstanceController.updateHealthStatus(instanceId, healthData);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(serviceInstanceManager).updateHealthStatus(instanceId, "HEALTHY", "");
    }

    private ServiceInstanceDTO createServiceInstanceDTO(Long id, String name) {
        return ServiceInstanceDTO.builder()
                .id(id)
                .serviceConfigId(1L)
                .name(name)
                .baseUrl("http://localhost:8080")
                .path("/api")
                .weight(1)
                .status("ACTIVE")
                .healthStatus("HEALTHY")
                .errorMessage("")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}

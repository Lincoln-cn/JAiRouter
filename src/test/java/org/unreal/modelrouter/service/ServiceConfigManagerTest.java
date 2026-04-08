package org.unreal.modelrouter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.dto.ServiceConfigDTO;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.jpa.repository.ServiceConfigRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ServiceConfigManager 测试
 * v1.5.2: Service 层测试
 */
@ExtendWith(MockitoExtension.class)
class ServiceConfigManagerTest {

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @InjectMocks
    private ServiceConfigManager serviceConfigManager;

    @Test
    void shouldGetAllServiceConfigs() {
        // Given
        ServiceConfigEntity config = ServiceConfigEntity.builder()
                .id(1L)
                .serviceType("chat")
                .adapter("openai")
                .isLatest(true)
                .build();
        when(serviceConfigRepository.findAllByIsLatestTrue()).thenReturn(List.of(config));

        // When
        List<ServiceConfigDTO> result = serviceConfigManager.getAllServiceConfigs();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceType()).isEqualTo("chat");
    }

    @Test
    void shouldSaveServiceConfig() {
        // Given
        CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType("chat")
                .adapter("openai")
                .loadBalanceType("round-robin")
                .build();

        ServiceConfigEntity savedEntity = ServiceConfigEntity.builder()
                .id(1L)
                .serviceType("chat")
                .adapter("openai")
                .loadBalanceType("round-robin")
                .version(1)
                .isLatest(true)
                .build();

        when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
                .thenReturn(Optional.empty());
        when(serviceConfigRepository.save(any())).thenReturn(savedEntity);

        // When
        ServiceConfigDTO result = serviceConfigManager.saveServiceConfig("chat", request);

        // Then
        assertThat(result.getServiceType()).isEqualTo("chat");
        assertThat(result.getAdapter()).isEqualTo("openai");
        verify(serviceConfigRepository).save(any());
    }

    @Test
    void shouldGetServiceConfig() {
        // Given
        ServiceConfigEntity config = ServiceConfigEntity.builder()
                .id(1L)
                .serviceType("chat")
                .adapter("openai")
                .isLatest(true)
                .build();
        when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
                .thenReturn(Optional.of(config));

        // When
        Optional<ServiceConfigDTO> result = serviceConfigManager.getServiceConfig("chat");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getServiceType()).isEqualTo("chat");
    }
}

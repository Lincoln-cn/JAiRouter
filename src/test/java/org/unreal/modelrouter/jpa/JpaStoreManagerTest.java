package org.unreal.modelrouter.jpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.jpa.entity.ConfigEntity;
import org.unreal.modelrouter.jpa.repository.ConfigRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JpaStoreManager 单元测试
 * v1.5.2: StoreManager 实现测试
 */
@ExtendWith(MockitoExtension.class)
class JpaStoreManagerTest {

    @Mock
    private ConfigRepository configRepository;

    @InjectMocks
    private JpaStoreManager jpaStoreManager;

    @Test
    void shouldSaveConfig() {
        // Given
        String key = "test-key";
        Map<String, Object> config = Map.of("test", "value");

        ConfigEntity savedEntity = ConfigEntity.builder()
                .id(1L)
                .configKey(key)
                .configValue("{\"test\": \"value\"}")
                .version(1)
                .isLatest(true)
                .build();

        when(configRepository.findAllVersionsByConfigKey(key)).thenReturn(List.of());
        when(configRepository.save(any())).thenReturn(savedEntity);

        // When
        jpaStoreManager.saveConfig(key, config);

        // Then
        verify(configRepository).save(any());
    }

    @Test
    void shouldGetConfig() {
        // Given
        String key = "test-key";
        ConfigEntity entity = ConfigEntity.builder()
                .id(1L)
                .configKey(key)
                .configValue("{\"test\": \"value\"}")
                .version(1)
                .isLatest(true)
                .build();

        when(configRepository.findFirstByConfigKeyAndIsLatestTrue(key)).thenReturn(Optional.of(entity));

        // When
        Map<String, Object> result = jpaStoreManager.getConfig(key);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("test")).isEqualTo("value");
    }

    @Test
    void shouldGetConfigVersions() {
        // Given
        String key = "test-key";
        when(configRepository.findAllVersionsByConfigKey(key)).thenReturn(List.of(1, 2, 3));

        // When
        List<Integer> versions = jpaStoreManager.getConfigVersions(key);

        // Then
        assertThat(versions).containsExactly(1, 2, 3);
    }

    @Test
    void shouldCheckExists() {
        // Given
        String key = "test-key";
        when(configRepository.existsByConfigKeyAndIsLatestTrue(key)).thenReturn(true);

        // When
        boolean exists = jpaStoreManager.exists(key);

        // Then
        assertThat(exists).isTrue();
    }
}

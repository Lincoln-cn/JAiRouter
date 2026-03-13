package org.unreal.modelrouter.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.store.entity.ConfigEntity;
import org.unreal.modelrouter.store.repository.ConfigRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ReactiveH2StoreManager 测试类
 * 纯响应式测试，使用 StepVerifier 验证 Mono/Flux 行为
 */
@ExtendWith(MockitoExtension.class)
class ReactiveH2StoreManagerTest {

    @Mock
    private ConfigRepository configRepository;

    private ReactiveH2StoreManager storeManager;

    @BeforeEach
    void setUp() {
        storeManager = new ReactiveH2StoreManager(configRepository);
    }

    @Test
    void testSaveAndGetConfig() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key1", "value1");
        config.put("key2", 123);

        ConfigEntity savedEntity = ConfigEntity.builder()
                .id(1L)
                .configKey("test-config")
                .configValue("{\"key1\":\"value1\",\"key2\":123}")
                .version(1)
                .isLatest(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When - Mock repository behavior
        when(configRepository.findLatestByConfigKey("test-config"))
                .thenReturn(Mono.empty()); // First call for version check
        when(configRepository.markAllAsNotLatest("test-config"))
                .thenReturn(Mono.empty());
        when(configRepository.save(any(ConfigEntity.class)))
                .thenReturn(Mono.just(savedEntity));

        // Then - Test save operation
        StepVerifier.create(storeManager.saveConfig("test-config", config))
                .verifyComplete();
    }

    @Test
    void testGetConfig_Success() {
        // Given
        ConfigEntity entity = ConfigEntity.builder()
                .configKey("test-config")
                .configValue("{\"key1\":\"value1\",\"key2\":123}")
                .version(1)
                .isLatest(true)
                .build();

        // When
        when(configRepository.findLatestByConfigKey("test-config"))
                .thenReturn(Mono.just(entity));

        // Then
        StepVerifier.create(storeManager.getConfig("test-config"))
                .assertNext(config -> {
                    org.junit.jupiter.api.Assertions.assertEquals("value1", config.get("key1"));
                    org.junit.jupiter.api.Assertions.assertEquals(123, config.get("key2"));
                })
                .verifyComplete();
    }

    @Test
    void testGetConfig_NotFound() {
        // When
        when(configRepository.findLatestByConfigKey("non-existent"))
                .thenReturn(Mono.empty());

        // Then
        StepVerifier.create(storeManager.getConfig("non-existent"))
                .verifyComplete(); // Should complete without emitting value
    }

    @Test
    void testExists_True() {
        // When
        when(configRepository.existsByConfigKey("existing-config"))
                .thenReturn(Mono.just(true));

        // Then
        StepVerifier.create(storeManager.exists("existing-config"))
                .assertNext(org.junit.jupiter.api.Assertions::assertTrue)
                .verifyComplete();
    }

    @Test
    void testExists_False() {
        // When
        when(configRepository.existsByConfigKey("non-existing-config"))
                .thenReturn(Mono.just(false));

        // Then
        StepVerifier.create(storeManager.exists("non-existing-config"))
                .assertNext(org.junit.jupiter.api.Assertions::assertFalse)
                .verifyComplete();
    }

    @Test
    void testDeleteConfig() {
        // When
        when(configRepository.deleteAllByConfigKey("test-config"))
                .thenReturn(Mono.empty());

        // Then
        StepVerifier.create(storeManager.deleteConfig("test-config"))
                .verifyComplete();
    }

    @Test
    void testGetAllKeys() {
        // When
        when(configRepository.findAllLatestConfigKeys())
                .thenReturn(Flux.just("config1", "config2", "config3"));

        // Then
        StepVerifier.create(storeManager.getAllKeys().collectList())
                .assertNext(keys -> {
                    org.junit.jupiter.api.Assertions.assertEquals(3, keys.size());
                    org.junit.jupiter.api.Assertions.assertTrue(keys.contains("config1"));
                    org.junit.jupiter.api.Assertions.assertTrue(keys.contains("config2"));
                    org.junit.jupiter.api.Assertions.assertTrue(keys.contains("config3"));
                })
                .verifyComplete();
    }

    @Test
    void testSaveConfigVersion() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("version", 1);

        ConfigEntity savedEntity = ConfigEntity.builder()
                .id(1L)
                .configKey("test-config")
                .configValue("{\"version\":1}")
                .version(5)
                .isLatest(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        when(configRepository.save(any(ConfigEntity.class)))
                .thenReturn(Mono.just(savedEntity));

        // Then
        StepVerifier.create(storeManager.saveConfigVersion("test-config", config, 5))
                .verifyComplete();
    }

    @Test
    void testGetConfigVersions() {
        // Given
        ConfigEntity entity1 = ConfigEntity.builder().version(1).build();
        ConfigEntity entity2 = ConfigEntity.builder().version(2).build();
        ConfigEntity entity3 = ConfigEntity.builder().version(3).build();

        // When
        when(configRepository.findAllByConfigKey("test-config"))
                .thenReturn(Flux.just(entity1, entity2, entity3));

        // Then
        StepVerifier.create(storeManager.getConfigVersions("test-config").collectList())
                .assertNext(versions -> {
                    org.junit.jupiter.api.Assertions.assertEquals(3, versions.size());
                    org.junit.jupiter.api.Assertions.assertEquals(1, versions.get(0));
                    org.junit.jupiter.api.Assertions.assertEquals(2, versions.get(1));
                    org.junit.jupiter.api.Assertions.assertEquals(3, versions.get(2));
                })
                .verifyComplete();
    }

    @Test
    void testGetConfigByVersion() {
        // Given
        ConfigEntity entity = ConfigEntity.builder()
                .configKey("test-config")
                .configValue("{\"key\":\"value\"}")
                .version(2)
                .build();

        // When
        when(configRepository.findByConfigKeyAndVersion("test-config", 2))
                .thenReturn(Mono.just(entity));

        // Then
        StepVerifier.create(storeManager.getConfigByVersion("test-config", 2))
                .assertNext(config ->
                        org.junit.jupiter.api.Assertions.assertEquals("value", config.get("key")))
                .verifyComplete();
    }

    @Test
    void testDeleteConfigVersion() {
        // When
        when(configRepository.deleteByConfigKeyAndVersion("test-config", 1))
                .thenReturn(Mono.empty());

        // Then
        StepVerifier.create(storeManager.deleteConfigVersion("test-config", 1))
                .verifyComplete();
    }

    @Test
    void testVersionExists_True() {
        // When
        when(configRepository.existsByConfigKeyAndVersion("test-config", 1))
                .thenReturn(Mono.just(true));

        // Then
        StepVerifier.create(storeManager.versionExists("test-config", 1))
                .assertNext(org.junit.jupiter.api.Assertions::assertTrue)
                .verifyComplete();
    }

    @Test
    void testVersionExists_False() {
        // When
        when(configRepository.existsByConfigKeyAndVersion("test-config", 999))
                .thenReturn(Mono.just(false));

        // Then
        StepVerifier.create(storeManager.versionExists("test-config", 999))
                .assertNext(org.junit.jupiter.api.Assertions::assertFalse)
                .verifyComplete();
    }

    @Test
    void testGetVersionFilePath() {
        // Then
        StepVerifier.create(storeManager.getVersionFilePath("test-config", 1))
                .assertNext(path -> org.junit.jupiter.api.Assertions.assertEquals("h2://test-config/v1", path))
                .verifyComplete();
    }

    @Test
    void testSaveConfig_EmptyConfig() {
        // Given
        Map<String, Object> emptyConfig = new HashMap<>();

        // Then - Should complete immediately without calling repository
        StepVerifier.create(storeManager.saveConfig("test-config", emptyConfig))
                .verifyComplete();
    }

    @Test
    void testSaveConfig_NullConfig() {
        // Then - Should complete immediately without calling repository
        StepVerifier.create(storeManager.saveConfig("test-config", null))
                .verifyComplete();
    }
}

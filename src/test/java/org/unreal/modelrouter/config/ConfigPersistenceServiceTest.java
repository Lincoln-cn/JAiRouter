package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.config.event.ConfigurationChangedEvent;
import org.unreal.modelrouter.store.ReactiveVersionedStoreManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConfigPersistenceService 测试类
 * 纯响应式测试
 */
@ExtendWith(MockitoExtension.class)
class ConfigPersistenceServiceTest {

    @Mock
    private ReactiveVersionedStoreManager storeManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private VersionControlService versionControlService;

    private ConfigPersistenceService configPersistenceService;

    @BeforeEach
    void setUp() {
        configPersistenceService = new ConfigPersistenceService(storeManager, eventPublisher, versionControlService);
    }

    @Test
    void testGetCurrentConfig_Success() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(storeManager.getConfig("model-router-config"))
                .thenReturn(Mono.just(config));

        // Then
        StepVerifier.create(configPersistenceService.getCurrentConfig())
                .assertNext(result -> org.junit.jupiter.api.Assertions.assertEquals("value", result.get("key")))
                .verifyComplete();
    }

    @Test
    void testGetCurrentConfig_NotFound() {
        // When
        when(storeManager.getConfig("model-router-config"))
                .thenReturn(Mono.empty());

        // Then
        StepVerifier.create(configPersistenceService.getCurrentConfig())
                .verifyComplete();
    }

    @Test
    void testSaveConfig() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(storeManager.saveConfig("model-router-config", config))
                .thenReturn(Mono.empty());

        // Then
        StepVerifier.create(configPersistenceService.saveConfig(config))
                .verifyComplete();

        verify(storeManager).saveConfig("model-router-config", config);
    }

    @Test
    void testSaveConfigWithVersion() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(versionControlService.createNewVersion(config, "Test description", "test-user"))
                .thenReturn(Mono.just(5));
        when(storeManager.saveConfig("model-router-config", config))
                .thenReturn(Mono.empty());
        doNothing().when(eventPublisher).publishEvent(any(ConfigurationChangedEvent.class));

        // Then
        StepVerifier.create(configPersistenceService.saveConfigWithVersion(config, "Test description", "test-user"))
                .assertNext(version -> org.junit.jupiter.api.Assertions.assertEquals(5, version))
                .verifyComplete();

        // Verify event was published
        ArgumentCaptor<ConfigurationChangedEvent> eventCaptor = ArgumentCaptor.forClass(ConfigurationChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ConfigurationChangedEvent event = eventCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(5, event.getVersion());
        org.junit.jupiter.api.Assertions.assertEquals(ConfigurationChangedEvent.ChangeType.UPDATE, event.getChangeType());
    }

    @Test
    void testApplyVersion_Success() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(versionControlService.versionExists(5))
                .thenReturn(Mono.just(true));
        when(versionControlService.getVersionConfig(5))
                .thenReturn(Mono.just(config));
        when(storeManager.saveConfig("model-router-config", config))
                .thenReturn(Mono.empty());
        doNothing().when(eventPublisher).publishEvent(any(ConfigurationChangedEvent.class));

        // Then
        StepVerifier.create(configPersistenceService.applyVersion(5, "test-user"))
                .verifyComplete();

        verify(storeManager).saveConfig("model-router-config", config);
    }

    @Test
    void testApplyVersion_NotFound() {
        // When
        when(versionControlService.versionExists(999))
                .thenReturn(Mono.just(false));

        // Then
        StepVerifier.create(configPersistenceService.applyVersion(999, "test-user"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testApplyVersion_EmptyConfig() {
        // When
        when(versionControlService.versionExists(5))
                .thenReturn(Mono.just(true));
        when(versionControlService.getVersionConfig(5))
                .thenReturn(Mono.just(new HashMap<>())); // Empty config

        // Then
        StepVerifier.create(configPersistenceService.applyVersion(5, "test-user"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void testConfigExists_True() {
        // When
        when(storeManager.exists("model-router-config"))
                .thenReturn(Mono.just(true));

        // Then
        StepVerifier.create(configPersistenceService.configExists())
                .assertNext(org.junit.jupiter.api.Assertions::assertTrue)
                .verifyComplete();
    }

    @Test
    void testConfigExists_False() {
        // When
        when(storeManager.exists("model-router-config"))
                .thenReturn(Mono.just(false));

        // Then
        StepVerifier.create(configPersistenceService.configExists())
                .assertNext(org.junit.jupiter.api.Assertions::assertFalse)
                .verifyComplete();
    }

    @Test
    void testDeleteConfig() {
        // When
        when(storeManager.deleteConfig("model-router-config"))
                .thenReturn(Mono.empty());

        // Then
        StepVerifier.create(configPersistenceService.deleteConfig())
                .verifyComplete();

        verify(storeManager).deleteConfig("model-router-config");
    }

    @Test
    void testSaveConfigWithVersion_NullUser() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(versionControlService.createNewVersion(config, "Test description", null))
                .thenReturn(Mono.just(1));
        when(storeManager.saveConfig("model-router-config", config))
                .thenReturn(Mono.empty());
        doNothing().when(eventPublisher).publishEvent(any(ConfigurationChangedEvent.class));

        // Then
        StepVerifier.create(configPersistenceService.saveConfigWithVersion(config, "Test description", null))
                .assertNext(version -> org.junit.jupiter.api.Assertions.assertEquals(1, version))
                .verifyComplete();
    }
}

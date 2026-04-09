package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.config.event.ConfigurationChangedEvent;
import org.unreal.modelrouter.jpa.JpaStoreManager;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConfigPersistenceService 测试类
 * v1.5.x: JPA 版本（同步 API）
 */
@ExtendWith(MockitoExtension.class)
class ConfigPersistenceServiceTest {

    @Mock
    private JpaStoreManager storeManager;

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
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // Then
        Map<String, Object> result = configPersistenceService.getCurrentConfig();
        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testGetCurrentConfig_NotFound() {
        // When
        when(storeManager.getConfig("model-router-config")).thenReturn(null);

        // Then
        Map<String, Object> result = configPersistenceService.getCurrentConfig();
        assertNull(result);
    }

    @Test
    void testGetCurrentConfig_Error() {
        // When
        when(storeManager.getConfig("model-router-config")).thenThrow(new RuntimeException("DB error"));

        // Then
        assertThrows(RuntimeException.class, () -> configPersistenceService.getCurrentConfig());
    }

    @Test
    void testSaveConfig() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        doNothing().when(storeManager).saveConfig(anyString(), anyMap());

        // Then
        configPersistenceService.saveConfig(config);
        verify(storeManager).saveConfig("model-router-config", config);
    }

    @Test
    void testSaveConfigWithVersion() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(versionControlService.createNewVersion(config, "Test description", "test-user")).thenReturn(5);
        doNothing().when(storeManager).saveConfig(anyString(), anyMap());
        doNothing().when(eventPublisher).publishEvent(any(ConfigurationChangedEvent.class));

        // Then
        Integer version = configPersistenceService.saveConfigWithVersion(config, "Test description", "test-user");
        assertEquals(5, version);

        // Verify event was published
        ArgumentCaptor<ConfigurationChangedEvent> eventCaptor = ArgumentCaptor.forClass(ConfigurationChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ConfigurationChangedEvent event = eventCaptor.getValue();
        assertEquals(5, event.getVersion());
        assertEquals(ConfigurationChangedEvent.ChangeType.UPDATE, event.getChangeType());
    }

    @Test
    void testSaveConfigWithVersion_NullUser() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(versionControlService.createNewVersion(config, "Test description", null)).thenReturn(1);
        doNothing().when(storeManager).saveConfig(anyString(), anyMap());
        doNothing().when(eventPublisher).publishEvent(any(ConfigurationChangedEvent.class));

        // Then
        Integer version = configPersistenceService.saveConfigWithVersion(config, "Test description", null);
        assertEquals(1, version);
    }

    @Test
    void testSaveConfigWithVersion_NullConfig() {
        // When
        when(versionControlService.createNewVersion(null, "Empty", "user")).thenReturn(1);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(eventPublisher).publishEvent(any());

        // Then
        Integer version = configPersistenceService.saveConfigWithVersion(null, "Empty", "user");
        assertEquals(1, version);
    }
}
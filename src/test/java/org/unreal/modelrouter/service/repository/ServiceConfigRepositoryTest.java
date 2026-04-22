package org.unreal.modelrouter.service.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unreal.modelrouter.config.dto.ServiceConfiguration;
import org.unreal.modelrouter.store.StoreManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ServiceConfigRepository 单元测试
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
class ServiceConfigRepositoryTest {

    private ServiceConfigRepository repository;
    private StoreManager storeManager;

    @BeforeEach
    void setUp() {
        storeManager = mock(StoreManager.class);
        repository = new ServiceConfigRepository(storeManager);
    }

    @Test
    void testFindAll_Success() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of(
                "services", (Map<String, Object>) (Object) Map.of(
                        "chat", (Map<String, Object>) (Object) Map.of("adapter", "normal"),
                        "embedding", (Map<String, Object>) (Object) Map.of("adapter", "normal")
                )
        );
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        Map<String, ServiceConfiguration> result = repository.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("chat"));
        assertTrue(result.containsKey("embedding"));
    }

    @Test
    void testFindAll_EmptyConfig() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of();
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        Map<String, ServiceConfiguration> result = repository.findAll();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindAll_NullConfig() {
        // Given
        when(storeManager.getConfig("model-router-config")).thenReturn(null);

        // When
        Map<String, ServiceConfiguration> result = repository.findAll();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindAll_Exception() {
        // Given
        when(storeManager.getConfig("model-router-config")).thenThrow(new RuntimeException("Test error"));

        // When
        Map<String, ServiceConfiguration> result = repository.findAll();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindById_Success() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of(
                "services", (Map<String, Object>) (Object) Map.of(
                        "chat", (Map<String, Object>) (Object) Map.of("adapter", "normal")
                )
        );
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        Optional<ServiceConfiguration> result = repository.findById("chat");

        // Then
        assertTrue(result.isPresent());
        assertEquals("normal", result.get().adapter());
    }

    @Test
    void testFindById_NotFound() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of(
                "services", (Map<String, Object>) (Object) Map.of(
                        "chat", (Map<String, Object>) (Object) Map.of("adapter", "normal")
                )
        );
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        Optional<ServiceConfiguration> result = repository.findById("embedding");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindById_EmptyConfig() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of();
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        Optional<ServiceConfiguration> result = repository.findById("chat");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindById_NullConfig() {
        // Given
        when(storeManager.getConfig("model-router-config")).thenReturn(null);

        // When
        Optional<ServiceConfiguration> result = repository.findById("chat");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindById_Exception() {
        // Given
        when(storeManager.getConfig("model-router-config")).thenThrow(new RuntimeException("Test error"));

        // When
        Optional<ServiceConfiguration> result = repository.findById("chat");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testSave_Success() {
        // Given
        ServiceConfiguration config = ServiceConfiguration.defaultConfig();
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        repository.save("chat", config);

        // Then
        verify(storeManager).saveConfig(eq("model-router-config"), configCaptor.capture());

        Map<String, Object> savedConfig = configCaptor.getValue();
        assertNotNull(savedConfig);
        assertTrue(savedConfig.containsKey("services"));
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) savedConfig.get("services");
        assertTrue(services.containsKey("chat"));
    }

    @Test
    void testSave_Exception() {
        // Given
        ServiceConfiguration config = ServiceConfiguration.defaultConfig();
        doThrow(new RuntimeException("Save failed")).when(storeManager).saveConfig(anyString(), anyMap());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> repository.save("chat", config)
        );
        assertTrue(exception.getMessage().contains("保存服务配置失败"));
    }

    @Test
    void testDelete_Success() {
        // Given
        Map<String, Object> services = new HashMap<>();
        services.put("chat", (Map<String, Object>) (Object) Map.of("adapter", "normal"));
        Map<String, Object> config = new HashMap<>();
        config.put("services", services);
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        repository.delete("chat");

        // Then
        verify(storeManager).saveConfig(eq("model-router-config"), any());
    }

    @Test
    void testDelete_NotFound() {
        // Given
        Map<String, Object> services = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put("services", services);
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        repository.delete("chat");

        // Then
        verify(storeManager, never()).saveConfig(anyString(), anyMap());
    }

    @Test
    void testDelete_Exception() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of(
                "services", (Map<String, Object>) (Map<String, Object>) (Object) Map.of("chat", (Map<String, Object>) (Object) Map.of("adapter", "normal"))
        );
        when(storeManager.getConfig("model-router-config")).thenReturn(config);
        doThrow(new RuntimeException("Delete failed")).when(storeManager).saveConfig(anyString(), anyMap());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> repository.delete("chat")
        );
        assertTrue(exception.getMessage().contains("删除服务配置失败"));
    }

    @Test
    void testExists_True() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of(
                "services", (Map<String, Object>) (Object) Map.of(
                        "chat", (Map<String, Object>) (Object) Map.of("adapter", "normal")
                )
        );
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        boolean result = repository.exists("chat");

        // Then
        assertTrue(result);
    }

    @Test
    void testExists_False() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of(
                "services", (Map<String, Object>) (Object) Map.of(
                        "chat", (Map<String, Object>) (Object) Map.of("adapter", "normal")
                )
        );
        when(storeManager.getConfig("model-router-config")).thenReturn(config);

        // When
        boolean result = repository.exists("embedding");

        // Then
        assertFalse(result);
    }

    @Test
    void testExists_Exception() {
        // Given
        when(storeManager.getConfig("model-router-config")).thenThrow(new RuntimeException("Test error"));

        // When
        boolean result = repository.exists("chat");

        // Then
        assertFalse(result);
    }

    @Test
    void testGetConfigAsMap() {
        // Given
        Map<String, Object> expectedConfig = (Map<String, Object>) (Object) Map.of("key", "value");
        when(storeManager.getConfig("model-router-config")).thenReturn(expectedConfig);

        // When
        Map<String, Object> result = repository.getConfigAsMap();

        // Then
        assertEquals(expectedConfig, result);
    }

    @Test
    void testSaveConfigMap() {
        // Given
        Map<String, Object> config = (Map<String, Object>) (Object) Map.of("key", "value");

        // When
        repository.saveConfigMap(config);

        // Then
        verify(storeManager).saveConfig(eq("model-router-config"), eq(config));
    }

    @Test
    void testGetCurrentConfig_CreatesNew() {
        // Given
        when(storeManager.getConfig("model-router-config")).thenReturn(null);

        // When
        Map<String, Object> result = repository.getConfigAsMap();

        // Then
        assertNull(result);
    }

    @Test
    void testSave_AddsMetadata() {
        // Given
        ServiceConfiguration config = ServiceConfiguration.defaultConfig();
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        repository.save("chat", config);

        // Then
        verify(storeManager).saveConfig(eq("model-router-config"), configCaptor.capture());

        Map<String, Object> savedConfig = configCaptor.getValue();
        assertTrue(savedConfig.containsKey("_metadata"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) savedConfig.get("_metadata");
        assertEquals("save", metadata.get("operation"));
        assertEquals("chat", metadata.get("serviceType"));
        assertTrue(metadata.containsKey("timestamp"));
    }

    @Test
    void testDelete_AddsMetadata() {
        // Given
        Map<String, Object> services = new HashMap<>();
        services.put("chat", (Map<String, Object>) (Object) Map.of("adapter", "normal"));
        Map<String, Object> config = new HashMap<>();
        config.put("services", services);
        when(storeManager.getConfig("model-router-config")).thenReturn(config);
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        repository.delete("chat");

        // Then
        verify(storeManager).saveConfig(eq("model-router-config"), configCaptor.capture());

        Map<String, Object> savedConfig = configCaptor.getValue();
        assertTrue(savedConfig.containsKey("_metadata"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) savedConfig.get("_metadata");
        assertEquals("delete", metadata.get("operation"));
        assertEquals("chat", metadata.get("serviceType"));
    }
}

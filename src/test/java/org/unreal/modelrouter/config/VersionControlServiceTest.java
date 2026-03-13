package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.store.ReactiveVersionedStoreManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * VersionControlService 测试类
 * 纯响应式测试
 */
@ExtendWith(MockitoExtension.class)
class VersionControlServiceTest {

    @Mock
    private ReactiveVersionedStoreManager storeManager;

    private VersionControlService versionControlService;

    @BeforeEach
    void setUp() {
        versionControlService = new VersionControlService(storeManager);
    }

    @Test
    void testInitialize_NoExistingData() {
        // When - No existing metadata or history
        when(storeManager.exists("model-router-config.metadata")).thenReturn(Mono.just(false));
        when(storeManager.saveConfig(anyString(), any())).thenReturn(Mono.empty());

        // Then
        StepVerifier.create(versionControlService.initialize())
                .verifyComplete();
    }

    @Test
    void testInitialize_WithExistingData() {
        // Given - Existing metadata
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("currentVersion", 5);
        metadataMap.put("initialVersion", 1);
        metadataMap.put("totalVersions", 5);
        metadataMap.put("existingVersions", java.util.List.of(1, 2, 3, 4, 5));

        Map<String, Object> historyMap = new HashMap<>();
        historyMap.put("history", java.util.List.of());

        // When
        when(storeManager.exists("model-router-config.metadata")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.metadata")).thenReturn(Mono.just(metadataMap));
        when(storeManager.exists("model-router-config.history")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.history")).thenReturn(Mono.just(historyMap));

        // Then
        StepVerifier.create(versionControlService.initialize())
                .verifyComplete();
    }

    @Test
    void testGetAllVersions() {
        // Given
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("currentVersion", 3);
        metadataMap.put("initialVersion", 1);
        metadataMap.put("totalVersions", 3);
        metadataMap.put("existingVersions", java.util.List.of(1, 2, 3));

        // When
        when(storeManager.exists("model-router-config.metadata")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.metadata")).thenReturn(Mono.just(metadataMap));
        when(storeManager.exists("model-router-config.history")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.history")).thenReturn(Mono.just(Map.of("history", java.util.List.of())));

        // Initialize first
        StepVerifier.create(versionControlService.initialize())
                .verifyComplete();

        // Then
        StepVerifier.create(versionControlService.getAllVersions().collectList())
                .assertNext(versions -> {
                    org.junit.jupiter.api.Assertions.assertEquals(3, versions.size());
                    org.junit.jupiter.api.Assertions.assertEquals(1, versions.get(0));
                    org.junit.jupiter.api.Assertions.assertEquals(2, versions.get(1));
                    org.junit.jupiter.api.Assertions.assertEquals(3, versions.get(2));
                })
                .verifyComplete();
    }

    @Test
    void testCreateNewVersion() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(storeManager.saveConfigVersion(anyString(), any(), anyInt())).thenReturn(Mono.empty());
        when(storeManager.saveConfig(anyString(), any())).thenReturn(Mono.empty());

        // Initialize first
        when(storeManager.exists("model-router-config.metadata")).thenReturn(Mono.just(false));
        StepVerifier.create(versionControlService.initialize())
                .verifyComplete();

        // Then - Create version
        StepVerifier.create(versionControlService.createNewVersion(config, "Test version", "test-user"))
                .assertNext(version -> org.junit.jupiter.api.Assertions.assertTrue(version > 0))
                .verifyComplete();
    }

    @Test
    void testGetVersionConfig_VersionZero() {
        // Version 0 should return empty (YAML config handled elsewhere)
        StepVerifier.create(versionControlService.getVersionConfig(0))
                .verifyComplete();
    }

    @Test
    void testGetVersionConfig_ExistingVersion() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        when(storeManager.getConfigByVersion("model-router-config", 1))
                .thenReturn(Mono.just(config));

        // Then
        StepVerifier.create(versionControlService.getVersionConfig(1))
                .assertNext(result -> org.junit.jupiter.api.Assertions.assertEquals("value", result.get("key")))
                .verifyComplete();
    }

    @Test
    void testVersionExists_True() {
        // When
        when(storeManager.versionExists("model-router-config", 1))
                .thenReturn(Mono.just(true));

        // Then
        StepVerifier.create(versionControlService.versionExists(1))
                .assertNext(org.junit.jupiter.api.Assertions::assertTrue)
                .verifyComplete();
    }

    @Test
    void testVersionExists_False() {
        // When
        when(storeManager.versionExists("model-router-config", 999))
                .thenReturn(Mono.just(false));

        // Then
        StepVerifier.create(versionControlService.versionExists(999))
                .assertNext(org.junit.jupiter.api.Assertions::assertFalse)
                .verifyComplete();
    }

    @Test
    void testDeleteVersion_Success() {
        // Given - Need to initialize first with multiple versions
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("currentVersion", 2);
        metadataMap.put("initialVersion", 1);
        metadataMap.put("totalVersions", 2);
        metadataMap.put("existingVersions", java.util.List.of(1, 2));

        // When
        when(storeManager.exists("model-router-config.metadata")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.metadata")).thenReturn(Mono.just(metadataMap));
        when(storeManager.exists("model-router-config.history")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.history")).thenReturn(Mono.just(Map.of("history", java.util.List.of())));

        // Initialize
        StepVerifier.create(versionControlService.initialize())
                .verifyComplete();

        // Mock delete operations
        when(storeManager.getConfigVersions("model-router-config"))
                .thenReturn(Flux.just(1, 2));
        when(storeManager.deleteConfigVersion("model-router-config", 1))
                .thenReturn(Mono.empty());
        when(storeManager.saveConfig(anyString(), any()))
                .thenReturn(Mono.empty());

        // Then - Delete version 1 (not current version)
        StepVerifier.create(versionControlService.deleteVersion(1))
                .verifyComplete();
    }

    @Test
    void testDeleteVersion_CurrentVersion() {
        // Given - Initialize with version 1 as current
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("currentVersion", 1);
        metadataMap.put("initialVersion", 1);
        metadataMap.put("totalVersions", 1);
        metadataMap.put("existingVersions", java.util.List.of(1));

        // When
        when(storeManager.exists("model-router-config.metadata")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.metadata")).thenReturn(Mono.just(metadataMap));
        when(storeManager.exists("model-router-config.history")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.history")).thenReturn(Mono.just(Map.of("history", java.util.List.of())));

        // Initialize
        StepVerifier.create(versionControlService.initialize())
                .verifyComplete();

        // Then - Try to delete current version should fail
        StepVerifier.create(versionControlService.deleteVersion(1))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void testDeleteVersion_LastVersion() {
        // Given - Initialize with only one version
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("currentVersion", 2);
        metadataMap.put("initialVersion", 1);
        metadataMap.put("totalVersions", 1);
        metadataMap.put("existingVersions", java.util.List.of(2));

        // When
        when(storeManager.exists("model-router-config.metadata")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.metadata")).thenReturn(Mono.just(metadataMap));
        when(storeManager.exists("model-router-config.history")).thenReturn(Mono.just(true));
        when(storeManager.getConfig("model-router-config.history")).thenReturn(Mono.just(Map.of("history", java.util.List.of())));

        // Initialize
        StepVerifier.create(versionControlService.initialize())
                .verifyComplete();

        // Mock getConfigVersions to return only one version
        when(storeManager.getConfigVersions("model-router-config"))
                .thenReturn(Flux.just(2));

        // Then - Try to delete last version should fail
        StepVerifier.create(versionControlService.deleteVersion(1))
                .expectError(IllegalStateException.class)
                .verify();
    }
}

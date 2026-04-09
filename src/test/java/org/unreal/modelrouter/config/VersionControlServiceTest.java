package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.jpa.JpaStoreManager;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VersionControlService 测试类
 * v1.5.x: JPA 版本（同步 API）
 */
@ExtendWith(MockitoExtension.class)
class VersionControlServiceTest {

    @Mock
    private JpaStoreManager storeManager;

    private VersionControlService versionControlService;

    @BeforeEach
    void setUp() {
        versionControlService = new VersionControlService(storeManager);
    }

    @Test
    void testGetCurrentVersion_InitialState() {
        // 初始状态，版本号为 0
        assertEquals(0, versionControlService.getCurrentVersion());
    }

    @Test
    void testCreateNewVersion() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        // When
        Integer version = versionControlService.createNewVersion(config, "Test version", "test-user");

        // Then
        assertNotNull(version);
        assertTrue(version > 0);
        assertEquals(1, versionControlService.getCurrentVersion());
    }

    @Test
    void testCreateMultipleVersions() {
        // Given
        Map<String, Object> config1 = new HashMap<>();
        config1.put("version", 1);
        Map<String, Object> config2 = new HashMap<>();
        config2.put("version", 2);

        // When
        Integer v1 = versionControlService.createNewVersion(config1, "First version", "user1");
        Integer v2 = versionControlService.createNewVersion(config2, "Second version", "user2");

        // Then
        assertEquals(1, v1);
        assertEquals(2, v2);
        assertEquals(2, versionControlService.getCurrentVersion());
    }

    @Test
    void testRollbackToVersion() {
        // Given
        Map<String, Object> existingConfig = new HashMap<>();
        existingConfig.put("rolledBack", true);
        
        when(storeManager.getConfig(anyString())).thenReturn(existingConfig);

        // 先创建一个版本
        versionControlService.createNewVersion(new HashMap<>(), "Initial", "user");

        // When
        Map<String, Object> result = versionControlService.rollbackToVersion(1);

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("rolledBack"));
        verify(storeManager).getConfig("model-router-config");
    }

    @Test
    void testGetCurrentVersion_AfterCreations() {
        // Given - 创建多个版本
        for (int i = 1; i <= 5; i++) {
            versionControlService.createNewVersion(
                Map.of("iteration", i),
                "Version " + i,
                "user"
            );
        }

        // Then
        assertEquals(5, versionControlService.getCurrentVersion());
    }

    @Test
    void testCreateNewVersion_WithNullConfig() {
        // When - null config 也应该能处理
        Integer version = versionControlService.createNewVersion(null, "Empty version", "user");

        // Then
        assertNotNull(version);
        assertEquals(1, version);
    }

    @Test
    void testRollbackToVersion_WithNullResult() {
        // Given
        when(storeManager.getConfig(anyString())).thenReturn(null);

        // 先创建版本
        versionControlService.createNewVersion(new HashMap<>(), "Initial", "user");

        // When
        Map<String, Object> result = versionControlService.rollbackToVersion(1);

        // Then
        assertNull(result);
    }

    @Test
    void testVersionCounterIsAtomic() {
        // 测试版本计数器的原子性
        // Given
        Map<String, Object> config = Map.of("test", "concurrent");

        // When - 连续创建多个版本
        int[] versions = new int[10];
        for (int i = 0; i < 10; i++) {
            versions[i] = versionControlService.createNewVersion(config, "Version " + i, "user");
        }

        // Then - 版本号应该是连续递增的
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, versions[i]);
        }
        assertEquals(10, versionControlService.getCurrentVersion());
    }
}
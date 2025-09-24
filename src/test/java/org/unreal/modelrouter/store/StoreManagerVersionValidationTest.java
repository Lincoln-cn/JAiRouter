package org.unreal.modelrouter.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试StoreManager版本验证功能
 */
class StoreManagerVersionValidationTest {

    @TempDir
    Path tempDir;

    private FileStoreManager fileStoreManager;
    private ImprovedFileStoreManager improvedFileStoreManager;

    @BeforeEach
    void setUp() {
        fileStoreManager = new FileStoreManager(tempDir.toString());
        improvedFileStoreManager = new ImprovedFileStoreManager(tempDir.resolve("improved").toString());
    }

    @Test
    void testVersionExists_FileStoreManager() {
        String key = "test-config";
        Map<String, Object> config = createTestConfig();

        // 版本不存在时应该返回false
        assertFalse(fileStoreManager.versionExists(key, 1));

        // 保存版本后应该返回true
        fileStoreManager.saveConfigVersion(key, config, 1);
        assertTrue(fileStoreManager.versionExists(key, 1));

        // 其他版本应该返回false
        assertFalse(fileStoreManager.versionExists(key, 2));
    }

    @Test
    void testVersionExists_ImprovedFileStoreManager() {
        String key = "test-config";
        Map<String, Object> config = createTestConfig();

        // 版本不存在时应该返回false
        assertFalse(improvedFileStoreManager.versionExists(key, 1));

        // 保存版本后应该返回true
        improvedFileStoreManager.saveConfigVersion(key, config, 1);
        assertTrue(improvedFileStoreManager.versionExists(key, 1));

        // 其他版本应该返回false
        assertFalse(improvedFileStoreManager.versionExists(key, 2));
    }

    @Test
    void testGetVersionFilePath_FileStoreManager() {
        String key = "test-config";
        Map<String, Object> config = createTestConfig();

        // 版本不存在时应该返回null
        assertNull(fileStoreManager.getVersionFilePath(key, 1));

        // 保存版本后应该返回有效路径
        fileStoreManager.saveConfigVersion(key, config, 1);
        String filePath = fileStoreManager.getVersionFilePath(key, 1);
        assertNotNull(filePath);
        assertTrue(filePath.contains("test-config@1.json"));
    }

    @Test
    void testGetVersionFilePath_ImprovedFileStoreManager() {
        String key = "test-config";
        Map<String, Object> config = createTestConfig();

        // 版本不存在时应该返回null
        assertNull(improvedFileStoreManager.getVersionFilePath(key, 1));

        // 保存版本后应该返回有效路径
        improvedFileStoreManager.saveConfigVersion(key, config, 1);
        String filePath = improvedFileStoreManager.getVersionFilePath(key, 1);
        assertNotNull(filePath);
        assertTrue(filePath.contains("test-config.v1.json"));
    }

    @Test
    void testGetVersionCreatedTime_FileStoreManager() {
        String key = "test-config";
        Map<String, Object> config = createTestConfig();

        // 版本不存在时应该返回null
        assertNull(fileStoreManager.getVersionCreatedTime(key, 1));

        // 保存版本后应该返回有效时间
        LocalDateTime beforeSave = LocalDateTime.now().minusSeconds(1);
        fileStoreManager.saveConfigVersion(key, config, 1);
        LocalDateTime afterSave = LocalDateTime.now().plusSeconds(1);

        LocalDateTime createdTime = fileStoreManager.getVersionCreatedTime(key, 1);
        assertNotNull(createdTime);
        assertTrue(createdTime.isAfter(beforeSave));
        assertTrue(createdTime.isBefore(afterSave));
    }

    @Test
    void testGetVersionCreatedTime_ImprovedFileStoreManager() {
        String key = "test-config";
        Map<String, Object> config = createTestConfig();

        // 版本不存在时应该返回null
        assertNull(improvedFileStoreManager.getVersionCreatedTime(key, 1));

        // 保存版本后应该返回有效时间
        LocalDateTime beforeSave = LocalDateTime.now().minusSeconds(1);
        improvedFileStoreManager.saveConfigVersion(key, config, 1);
        LocalDateTime afterSave = LocalDateTime.now().plusSeconds(1);

        LocalDateTime createdTime = improvedFileStoreManager.getVersionCreatedTime(key, 1);
        assertNotNull(createdTime);
        assertTrue(createdTime.isAfter(beforeSave));
        assertTrue(createdTime.isBefore(afterSave));
    }

    @Test
    void testVersionValidation_EmptyFile() {
        String key = "test-config";

        // FileStoreManager不保存空配置，所以空配置的版本不会存在
        Map<String, Object> emptyConfig = new HashMap<>();
        fileStoreManager.saveConfigVersion(key, emptyConfig, 1);

        // 空配置不会被保存，所以版本不存在
        assertFalse(fileStoreManager.versionExists(key, 1));

        // 但是非空配置应该被保存并且存在
        Map<String, Object> nonEmptyConfig = createTestConfig();
        fileStoreManager.saveConfigVersion(key, nonEmptyConfig, 2);
        assertTrue(fileStoreManager.versionExists(key, 2));
    }

    private Map<String, Object> createTestConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("testKey", "testValue");
        config.put("number", 42);
        config.put("nested", Map.of("inner", "value"));
        return config;
    }
}
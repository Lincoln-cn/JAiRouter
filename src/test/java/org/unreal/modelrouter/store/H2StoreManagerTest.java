package org.unreal.modelrouter.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.unreal.modelrouter.store.repository.ConfigRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2StoreManager 测试类
 */
@SpringBootTest
@ActiveProfiles("test")
class H2StoreManagerTest {

    @Autowired(required = false)
    private ConfigRepository configRepository;

    private H2StoreManager storeManager;

    @BeforeEach
    void setUp() {
        if (configRepository != null) {
            storeManager = new H2StoreManager(configRepository);
            // 清理测试数据
            configRepository.deleteAll().block();
        }
    }

    @Test
    void testSaveAndGetConfig() {
        if (storeManager == null) {
            return; // Skip test if H2 is not configured
        }

        Map<String, Object> config = new HashMap<>();
        config.put("key1", "value1");
        config.put("key2", 123);

        storeManager.saveConfig("test-config", config);

        Map<String, Object> retrieved = storeManager.getConfig("test-config");
        assertNotNull(retrieved);
        assertEquals("value1", retrieved.get("key1"));
        assertEquals(123, retrieved.get("key2"));
    }

    @Test
    void testConfigVersions() {
        if (storeManager == null) {
            return;
        }

        Map<String, Object> config1 = new HashMap<>();
        config1.put("version", 1);
        storeManager.saveConfig("test-config", config1);

        Map<String, Object> config2 = new HashMap<>();
        config2.put("version", 2);
        storeManager.saveConfig("test-config", config2);

        List<Integer> versions = storeManager.getConfigVersions("test-config");
        assertTrue(versions.size() >= 2);
    }

    @Test
    void testDeleteConfig() {
        if (storeManager == null) {
            return;
        }

        Map<String, Object> config = new HashMap<>();
        config.put("test", "data");
        storeManager.saveConfig("test-config", config);

        assertTrue(storeManager.exists("test-config"));

        storeManager.deleteConfig("test-config");

        assertFalse(storeManager.exists("test-config"));
    }

    @Test
    void testGetAllKeys() {
        if (storeManager == null) {
            return;
        }

        Map<String, Object> config1 = new HashMap<>();
        config1.put("data", "1");
        storeManager.saveConfig("config1", config1);

        Map<String, Object> config2 = new HashMap<>();
        config2.put("data", "2");
        storeManager.saveConfig("config2", config2);

        Iterable<String> keys = storeManager.getAllKeys();
        assertNotNull(keys);
        
        int count = 0;
        for (String key : keys) {
            count++;
        }
        assertTrue(count >= 2);
    }
}

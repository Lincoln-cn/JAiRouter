package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InstanceIdUtils 单元测试 - v2.9.6
 */
@DisplayName("InstanceIdUtils v2.9.6 测试")
class InstanceIdUtilsTest {

    @Test
    @DisplayName("测试 1: getInstanceId - null 参数返回 null")
    void testGetInstanceIdNull() {
        String result = InstanceIdUtils.getInstanceId(null);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 2: getInstanceId - 空 Map 返回 null")
    void testGetInstanceIdEmptyMap() {
        Map<String, Object> config = new HashMap<>();
        String result = InstanceIdUtils.getInstanceId(config);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 3: getInstanceId - 有效 name 和 baseUrl 生成 ID")
    void testGetInstanceIdValidNameBaseUrl() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "gpt-4");
        config.put("baseUrl", "http://localhost:8080");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNotNull(result);
        // 生成的 ID 应该是 UUID 格式
        assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    @DisplayName("测试 4: getInstanceId - 已存在有效 instanceId 返回原值")
    void testGetInstanceIdExistingValidId() {
        Map<String, Object> config = new HashMap<>();
        String existingId = "123e4567-e89b-12d3-a456-426614174000";
        config.put("instanceId", existingId);
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertEquals(existingId, result);
    }

    @Test
    @DisplayName("测试 5: getInstanceId - 无效 instanceId 重新生成")
    void testGetInstanceIdExistingInvalidId() {
        Map<String, Object> config = new HashMap<>();
        config.put("instanceId", "invalid-uuid");
        config.put("name", "gpt-4");
        config.put("baseUrl", "http://localhost:8080");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNotNull(result);
        // 应该生成新的 UUID
        assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertNotEquals("invalid-uuid", result);
    }

    @Test
    @DisplayName("测试 6: getInstanceId - 空 instanceId 重新生成")
    void testGetInstanceIdEmptyInstanceId() {
        Map<String, Object> config = new HashMap<>();
        config.put("instanceId", "");
        config.put("name", "gpt-4");
        config.put("baseUrl", "http://localhost:8080");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNotNull(result);
        assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    @DisplayName("测试 7: getInstanceId - 缺少 name 返回 null")
    void testGetInstanceIdMissingName() {
        Map<String, Object> config = new HashMap<>();
        config.put("baseUrl", "http://localhost:8080");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 8: getInstanceId - 缺少 baseUrl 返回 null")
    void testGetInstanceIdMissingBaseUrl() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "gpt-4");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 9: getInstanceId - 支持 base-url 字段名")
    void testGetInstanceIdBaseUrlAlternativeName() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "gpt-4");
        config.put("base-url", "http://localhost:8080");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNotNull(result);
        assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    @DisplayName("测试 10: getInstanceId - 空 name 返回 null")
    void testGetInstanceIdEmptyName() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "");
        config.put("baseUrl", "http://localhost:8080");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 11: getInstanceId - 空 baseUrl 返回 null")
    void testGetInstanceIdEmptyBaseUrl() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "gpt-4");
        config.put("baseUrl", "");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 12: getInstanceId - name 为非字符串类型")
    void testGetInstanceIdNameNotString() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", 123);
        config.put("baseUrl", "http://localhost:8080");
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 13: getInstanceId - baseUrl 为非字符串类型")
    void testGetInstanceIdBaseUrlNotString() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "gpt-4");
        config.put("baseUrl", 8080);
        
        String result = InstanceIdUtils.getInstanceId(config);
        assertNull(result);
    }
}
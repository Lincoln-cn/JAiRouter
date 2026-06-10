package org.unreal.modelrouter.persistence.jpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigEntity 单元测试
 */
@DisplayName("ConfigEntity 测试")
class ConfigEntityTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        ConfigEntity entity = new ConfigEntity();

        assertNull(entity.getId());
        assertNull(entity.getConfigKey());
        assertNull(entity.getConfigValue());
        assertNull(entity.getVersion());
        assertNull(entity.getIsLatest());
    }

    @Test
    @DisplayName("测试Builder模式")
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        ConfigEntity entity = ConfigEntity.builder()
                .id(1L)
                .configKey("jairouter.tracing.enabled")
                .configValue("true")
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .isLatest(true)
                .build();

        assertEquals(1L, entity.getId());
        assertEquals("jairouter.tracing.enabled", entity.getConfigKey());
        assertEquals("true", entity.getConfigValue());
        assertEquals(1, entity.getVersion());
        assertTrue(entity.getIsLatest());
    }

    @Test
    @DisplayName("测试Setter和Getter")
    void testSetterGetter() {
        ConfigEntity entity = new ConfigEntity();

        entity.setId(2L);
        entity.setConfigKey("jairouter.rateLimit.enabled");
        entity.setConfigValue("false");
        entity.setVersion(2);
        entity.setIsLatest(false);

        assertEquals(2L, entity.getId());
        assertEquals("jairouter.rateLimit.enabled", entity.getConfigKey());
        assertEquals("false", entity.getConfigValue());
        assertEquals(2, entity.getVersion());
        assertFalse(entity.getIsLatest());
    }

    @Test
    @DisplayName("测试JSON格式配置值")
    void testJsonValue() {
        String jsonValue = "{\"enabled\":true,\"capacity\":100}";
        ConfigEntity entity = ConfigEntity.builder()
                .configKey("rateLimit.config")
                .configValue(jsonValue)
                .version(1)
                .isLatest(true)
                .build();

        assertEquals(jsonValue, entity.getConfigValue());
        assertTrue(entity.getConfigValue().contains("enabled"));
        assertTrue(entity.getConfigValue().contains("capacity"));
    }

    @Test
    @DisplayName("测试版本递增")
    void testVersionIncrement() {
        ConfigEntity entity = ConfigEntity.builder()
                .configKey("test.key")
                .configValue("v1")
                .version(1)
                .isLatest(true)
                .build();

        // 模拟版本更新
        entity.setVersion(2);
        entity.setConfigValue("v2");
        entity.setIsLatest(true);

        assertEquals(2, entity.getVersion());
        assertEquals("v2", entity.getConfigValue());
    }

    @Test
    @DisplayName("测试isLatest标志")
    void testIsLatestFlag() {
        ConfigEntity latestEntity = ConfigEntity.builder()
                .configKey("test.key")
                .configValue("latest")
                .version(3)
                .isLatest(true)
                .build();

        ConfigEntity oldEntity = ConfigEntity.builder()
                .configKey("test.key")
                .configValue("old")
                .version(2)
                .isLatest(false)
                .build();

        assertTrue(latestEntity.getIsLatest());
        assertFalse(oldEntity.getIsLatest());
    }
}

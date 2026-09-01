package org.unreal.modelrouter.config.core.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelInstanceConfiguration 单元测试
 * v2.9.7: 覆盖 fromMap/toMap/defaultConfig 中 tags 字段的同步
 */
@DisplayName("ModelInstanceConfiguration 测试")
class ModelInstanceConfigurationTest {

    @Nested
    @DisplayName("fromMap / toMap tags 同步")
    class TagsRoundTripTests {

        @Test
        @DisplayName("TAG-DTO-001: fromMap 解析 tags")
        void testFromMap_parsesTags() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "qwen2.5-72b");
            map.put("baseUrl", "http://10.0.0.1:8000");
            map.put("headers", Map.of("Authorization", "Bearer x"));
            map.put("tags", Map.of("gpu_type", "a100", "region", "cn-north"));

            ModelInstanceConfiguration config = ModelInstanceConfiguration.fromMap(map);

            assertNotNull(config);
            assertEquals(Map.of("gpu_type", "a100", "region", "cn-north"), config.tags(),
                    "fromMap 应解析 tags 键值对");
            assertEquals(Map.of("Authorization", "Bearer x"), config.headers());
        }

        @Test
        @DisplayName("TAG-DTO-002: toMap 输出 tags")
        void testToMap_outputsTags() {
            ModelInstanceConfiguration config = new ModelInstanceConfiguration(
                    "qwen2.5-72b", "http://10.0.0.1:8000", "/v1/chat/completions", null,
                    1, "active", null, null, null,
                    Map.of("Authorization", "Bearer x"),
                    Map.of("gpu_type", "a100"),
                    "inst-uuid");

            Map<String, Object> map = config.toMap();

            assertEquals(Map.of("gpu_type", "a100"), map.get("tags"), "toMap 应包含 tags");
            assertEquals("inst-uuid", map.get("instanceId"));
        }

        @Test
        @DisplayName("TAG-DTO-003: fromMap→toMap 往返保留 tags")
        void testRoundTrip_preservesTags() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "bge-reranker-v2-m3");
            map.put("baseUrl", "http://10.0.0.1:9090");
            map.put("status", "active");
            map.put("tags", Map.of("tier", "premium", "region", "cn-north"));

            ModelInstanceConfiguration config = ModelInstanceConfiguration.fromMap(map);
            Map<String, Object> roundTrip = config.toMap();

            assertEquals(Map.of("tier", "premium", "region", "cn-north"), roundTrip.get("tags"),
                    "fromMap→toMap 往返后 tags 应保留");
        }

        @Test
        @DisplayName("TAG-DTO-004: 无 tags 键时 fromMap 返回空 Map")
        void testFromMap_noTags_returnsEmpty() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "qwen2.5-72b");
            map.put("baseUrl", "http://10.0.0.1:8000");

            ModelInstanceConfiguration config = ModelInstanceConfiguration.fromMap(map);

            assertNotNull(config);
            assertTrue(config.tags().isEmpty(), "无 tags 键时 tags 应为空 Map");
        }

        @Test
        @DisplayName("TAG-DTO-005: toMap 中 tags 为 null 时不输出键")
        void testToMap_tagsNull_omitsKey() {
            ModelInstanceConfiguration config = new ModelInstanceConfiguration(
                    "qwen2.5-72b", "http://10.0.0.1:8000", "/v1/chat/completions", null,
                    1, "active", null, null, null, null, null, null);

            Map<String, Object> map = config.toMap();

            assertFalse(map.containsKey("tags"), "tags 为 null 时 toMap 不应输出 tags 键");
        }
    }

    @Nested
    @DisplayName("defaultConfig")
    class DefaultConfigTests {

        @Test
        @DisplayName("TAG-DTO-006: defaultConfig 含空 tags")
        void testDefaultConfig_hasEmptyTags() {
            ModelInstanceConfiguration config = ModelInstanceConfiguration.defaultConfig("m", "http://x.local");

            assertNotNull(config.tags());
            assertTrue(config.tags().isEmpty(), "defaultConfig 的 tags 应为空 Map");
        }
    }
}

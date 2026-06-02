package org.unreal.modelrouter.config.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.core.ConfigurationHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigValidator 测试")
class ConfigValidatorTest {

    @Mock
    private ConfigurationHelper configurationHelper;

    private ConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConfigValidator(configurationHelper);
    }

    @Nested
    @DisplayName("服务配置验证测试")
    class ValidateServiceConfigTests {

        @Test
        @DisplayName("VAL-065: 验证服务配置 - 添加缺失的instances字段")
        void testValidateAndNormalizeServiceConfigAddInstances() {
            Map<String, Object> config = new HashMap<>();
            config.put("loadBalance", Map.of("type", "round-robin"));

            Map<String, Object> result = validator.validateAndNormalizeServiceConfig(config);

            assertTrue(result.containsKey("instances"));
            assertTrue(result.get("instances") instanceof List);
            assertEquals("round-robin", ((Map<?, ?>) result.get("loadBalance")).get("type"));
        }

        @Test
        @DisplayName("VAL-066: 验证服务配置 - 保留现有instances")
        void testValidateAndNormalizeServiceConfigPreserveInstances() {
            Map<String, Object> config = new HashMap<>();
            List<Map<String, Object>> instances = new ArrayList<>();
            instances.add(Map.of("name", "test-instance"));
            config.put("instances", instances);

            Map<String, Object> result = validator.validateAndNormalizeServiceConfig(config);

            assertEquals(1, ((List<?>) result.get("instances")).size());
        }

        @Test
        @DisplayName("VAL-067: 验证服务配置 - 修复无效instances类型")
        void testValidateAndNormalizeServiceConfigFixInvalidInstances() {
            Map<String, Object> config = new HashMap<>();
            config.put("instances", "invalid-type");

            Map<String, Object> result = validator.validateAndNormalizeServiceConfig(config);

            assertTrue(result.get("instances") instanceof List);
            assertTrue(((List<?>) result.get("instances")).isEmpty());
        }
    }

    @Nested
    @DisplayName("实例配置验证测试")
    class ValidateInstanceConfigTests {

        @Test
        @DisplayName("VAL-068: 验证实例配置 - 成功")
        void testValidateAndNormalizeInstanceConfigSuccess() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "test-instance");
            config.put("baseUrl", "http://localhost:8080");

            Map<String, Object> result = validator.validateAndNormalizeInstanceConfig(config);

            assertEquals("test-instance", result.get("name"));
            assertEquals("http://localhost:8080", result.get("baseUrl"));
            assertEquals(1, result.get("weight")); // 默认值
            assertEquals("active", result.get("status")); // 默认值
            assertNotNull(result.get("instanceId")); // 自动生成
        }

        @Test
        @DisplayName("VAL-069: 验证实例配置 - 缺少name抛异常")
        void testValidateAndNormalizeInstanceConfigMissingName() {
            Map<String, Object> config = new HashMap<>();
            config.put("baseUrl", "http://localhost:8080");

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateAndNormalizeInstanceConfig(config)
            );

            assertTrue(ex.getMessage().contains("实例名称不能为空"));
        }

        @Test
        @DisplayName("VAL-070: 验证实例配置 - 缺少baseUrl抛异常")
        void testValidateAndNormalizeInstanceConfigMissingBaseUrl() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "test-instance");

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateAndNormalizeInstanceConfig(config)
            );

            assertTrue(ex.getMessage().contains("baseUrl 不能为空"));
        }

        @Test
        @DisplayName("VAL-071: 验证实例配置 - 保留自定义权重")
        void testValidateAndNormalizeInstanceConfigCustomWeight() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "test-instance");
            config.put("baseUrl", "http://localhost:8080");
            config.put("weight", 5);

            Map<String, Object> result = validator.validateAndNormalizeInstanceConfig(config);

            assertEquals(5, result.get("weight"));
        }
    }

    @Nested
    @DisplayName("服务类型验证测试")
    class IsValidServiceTypeTests {

        @Test
        @DisplayName("VAL-072: 服务类型验证 - 有效类型chat")
        void testIsValidServiceTypeChat() {
            assertTrue(validator.isValidServiceType("chat"));
        }

        @Test
        @DisplayName("VAL-073: 服务类型验证 - 有效类型embedding")
        void testIsValidServiceTypeEmbedding() {
            assertTrue(validator.isValidServiceType("embedding"));
        }

        @Test
        @DisplayName("VAL-074: 服务类型验证 - 有效类型rerank")
        void testIsValidServiceTypeRerank() {
            assertTrue(validator.isValidServiceType("rerank"));
        }

        @Test
        @DisplayName("VAL-075: 服务类型验证 - 无效类型")
        void testIsValidServiceTypeInvalid() {
            assertFalse(validator.isValidServiceType("invalid-type"));
        }

        @Test
        @DisplayName("VAL-076: 服务类型验证 - null值")
        void testIsValidServiceTypeNull() {
            assertFalse(validator.isValidServiceType(null));
        }

        @Test
        @DisplayName("VAL-077: 服务类型验证 - 别名chat-completion")
        void testIsValidServiceTypeAliasChatCompletion() {
            assertTrue(validator.isValidServiceType("chat-completion"));
        }

        @Test
        @DisplayName("VAL-078: 服务类型验证 - 别名embeddings")
        void testIsValidServiceTypeAliasEmbeddings() {
            assertTrue(validator.isValidServiceType("embeddings"));
        }
    }

    @Nested
    @DisplayName("实例配置合并测试")
    class MergeInstanceConfigTests {

        @Test
        @DisplayName("VAL-079: 合并实例配置 - 基本合并")
        void testMergeInstanceConfigBasic() {
            Map<String, Object> existing = new HashMap<>();
            existing.put("name", "test");
            existing.put("baseUrl", "http://localhost:8080");
            existing.put("weight", 1);

            Map<String, Object> updates = new HashMap<>();
            updates.put("weight", 5);
            updates.put("status", "inactive");

            Map<String, Object> result = validator.mergeInstanceConfig(existing, updates);

            assertEquals("test", result.get("name"));
            assertEquals(5, result.get("weight"));
            assertEquals("inactive", result.get("status"));
        }
    }

    @Nested
    @DisplayName("服务配置合并测试")
    class MergeServiceConfigTests {

        @Test
        @DisplayName("VAL-080: 合并服务配置 - 基本合并")
        void testMergeServiceConfigBasic() {
            Map<String, Object> existing = new HashMap<>();
            existing.put("adapter", "ollama");
            existing.put("timeout", 30);

            Map<String, Object> updates = new HashMap<>();
            updates.put("timeout", 60);
            updates.put("retries", 3);

            Map<String, Object> result = validator.mergeServiceConfig(existing, updates);

            assertEquals("ollama", result.get("adapter"));
            assertEquals(60, result.get("timeout"));
            assertEquals(3, result.get("retries"));
        }

        @Test
        @DisplayName("VAL-081: 合并服务配置 - instances直接替换")
        void testMergeServiceConfigInstancesReplace() {
            Map<String, Object> existing = new HashMap<>();
            existing.put("instances", Arrays.asList(Map.of("name", "old")));

            Map<String, Object> updates = new HashMap<>();
            updates.put("instances", Arrays.asList(Map.of("name", "new")));

            Map<String, Object> result = validator.mergeServiceConfig(existing, updates);

            List<?> instances = (List<?>) result.get("instances");
            assertEquals(1, instances.size());
            assertEquals("new", ((Map<?, ?>) instances.get(0)).get("name"));
        }
    }

    @Nested
    @DisplayName("默认配置创建测试")
    class CreateDefaultServiceConfigTests {

        @Test
        @DisplayName("VAL-082: 创建默认服务配置")
        void testCreateDefaultServiceConfig() {
            Map<String, Object> result = validator.createDefaultServiceConfig();

            assertTrue(result.containsKey("instances"));
            assertTrue(result.containsKey("loadBalance"));
            assertEquals("random", ((Map<?, ?>) result.get("loadBalance")).get("type"));
            assertEquals("md5", ((Map<?, ?>) result.get("loadBalance")).get("hashAlgorithm"));
        }
    }
}

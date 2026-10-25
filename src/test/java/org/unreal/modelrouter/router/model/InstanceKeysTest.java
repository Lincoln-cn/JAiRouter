package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link InstanceKeys} 的单元测试（#636）.
 *
 * <p>覆盖「instance-id 缺失」这一根因：旧实现直接用 {@code getInstanceId()}，
 * 结果为 {@code null}，同服务下所有实例共用 {@code chat:null} 一个键。
 */
@DisplayName("InstanceKeys 实例键解析")
class InstanceKeysTest {

    private static ModelRouterProperties.ModelInstance instance(
            final String instanceId, final String baseUrl, final String name) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId(instanceId);
        instance.setBaseUrl(baseUrl);
        instance.setName(name);
        return instance;
    }

    @Nested
    @DisplayName("identifier：instanceId → baseUrl → name → unknown")
    class Identifier {

        @Test
        @DisplayName("配置了 instance-id 时优先使用并去除首尾空白")
        void usesInstanceIdFirst() {
            final ModelRouterProperties.ModelInstance instance =
                    instance("  id-1  ", "http://host:8080", "qwen3:4b");

            assertEquals("id-1", InstanceKeys.identifier(instance));
        }

        @Test
        @DisplayName("未配置 instance-id 时回退 baseUrl（副本常同名，URL 才唯一）")
        void fallsBackToBaseUrl() {
            final ModelRouterProperties.ModelInstance instance =
                    instance(null, "http://172.16.30.7:9090", "qwen3:4b");

            assertEquals("http://172.16.30.7:9090", InstanceKeys.identifier(instance));
        }

        @Test
        @DisplayName("instance-id 为空白串时同样回退 baseUrl")
        void blankInstanceIdFallsBackToBaseUrl() {
            final ModelRouterProperties.ModelInstance instance =
                    instance("   ", "http://172.16.30.8:9090", "qwen3:4b");

            assertEquals("http://172.16.30.8:9090", InstanceKeys.identifier(instance));
        }

        @Test
        @DisplayName("instance-id 与 baseUrl 都缺失时回退 name")
        void fallsBackToName() {
            final ModelRouterProperties.ModelInstance instance = instance(null, null, "embedding-a");

            assertEquals("embedding-a", InstanceKeys.identifier(instance));
        }

        @Test
        @DisplayName("三个标识都缺失时返回占位符，不再产生 null 键")
        void fallsBackToUnknownPlaceholder() {
            final ModelRouterProperties.ModelInstance instance = instance(null, null, null);

            assertEquals(InstanceKeys.UNKNOWN_IDENTIFIER, InstanceKeys.identifier(instance));
            assertEquals("unknown", InstanceKeys.identifier(instance));
        }

        @Test
        @DisplayName("实例为 null 时返回占位符")
        void handlesNullInstance() {
            assertEquals(InstanceKeys.UNKNOWN_IDENTIFIER, InstanceKeys.identifier(null));
        }

        @Test
        @DisplayName("未配置任何标识的实例不再返回 null")
        void neverReturnsNull() {
            final ModelRouterProperties.ModelInstance instance = instance(null, null, null);

            assertNotNull(InstanceKeys.identifier(instance));
        }
    }

    @Nested
    @DisplayName("healthKey：serviceType:identifier")
    class HealthKey {

        @Test
        @DisplayName("已配 instance-id 的键格式保持不变")
        void keepsLegacyShapeForConfiguredInstance() {
            final ModelRouterProperties.ModelInstance instance =
                    instance("f0461c5b", "http://172.16.30.6:9090", "qwen3:4b");

            assertEquals("chat:f0461c5b", InstanceKeys.healthKey("chat", instance));
        }

        @Test
        @DisplayName("未配 instance-id 的键不再坍缩为 chat:null")
        void doesNotCollapseToNullKey() {
            final ModelRouterProperties.ModelInstance instance =
                    instance(null, "http://172.16.30.6:9090", "qwen3:4b");

            assertEquals("chat:http://172.16.30.6:9090", InstanceKeys.healthKey("chat", instance));
        }
    }
}

package org.unreal.modelrouter.router.checker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ServiceStateManager} 的实例健康键回归测试（#636）.
 *
 * <p>根因：早期键为 {@code serviceType + ":" + instance.getInstanceId()}，而
 * {@code getInstanceId()} 在未配置 {@code instance-id} 时返回 {@code null}，
 * 同服务下所有未配 id 的实例共用 {@code chat:null} —— 把其中一个标为不健康，
 * 其余实例会被一起判成不健康。下面的用例在旧实现上必然失败。
 */
@DisplayName("ServiceStateManager 实例健康状态键")
class ServiceStateManagerTest {

    private static ModelRouterProperties.ModelInstance instance(
            final String instanceId, final String baseUrl, final String name) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId(instanceId);
        instance.setBaseUrl(baseUrl);
        instance.setName(name);
        return instance;
    }

    @Test
    @DisplayName("未配 instance-id 的同服务实例互不影响（旧实现会一起变红）")
    void instancesWithoutInstanceIdAreIndependent() {
        final ServiceStateManager manager = new ServiceStateManager();
        final ModelRouterProperties.ModelInstance replicaA = instance(null, "http://172.16.30.7:9090", "qwen3:4b");
        final ModelRouterProperties.ModelInstance replicaB = instance(null, "http://172.16.30.8:9090", "qwen3:4b");

        manager.updateInstanceHealthStatus("chat", replicaA, false);

        assertFalse(manager.isInstanceHealthy("chat", replicaA));
        assertTrue(manager.isInstanceHealthy("chat", replicaB));

        final Map<String, Boolean> snapshot = manager.getAllInstanceHealthStatus();
        assertEquals(Boolean.FALSE, snapshot.get("chat:http://172.16.30.7:9090"));
        // 副本 B 从未被写入：旧实现里 A 的 false 会落在共享的 chat:null 上、把 B 一并判红
        assertNull(snapshot.get("chat:http://172.16.30.8:9090"));
    }

    @Test
    @DisplayName("已配 instance-id 的实例键格式保持原样")
    void configuredInstanceIdKeepsLegacyKey() {
        final ServiceStateManager manager = new ServiceStateManager();
        final ModelRouterProperties.ModelInstance instance =
                instance("f0461c5b-f894-4b6e-aace-6a133b2a1559", "http://172.16.30.6:9090", "qwen3:4b");

        manager.updateInstanceHealthStatus("chat", instance, false);

        assertFalse(manager.isInstanceHealthy("chat", instance));
        assertEquals(Boolean.FALSE,
                manager.getAllInstanceHealthStatus().get("chat:f0461c5b-f894-4b6e-aace-6a133b2a1559"));
    }

    @Test
    @DisplayName("未检查过的实例默认健康")
    void unknownInstanceDefaultsToHealthy() {
        final ServiceStateManager manager = new ServiceStateManager();

        assertTrue(manager.isInstanceHealthy("chat", instance(null, "http://172.16.30.9:9090", "qwen3:4b")));
    }

    @Test
    @DisplayName("服务类型参与键前缀：同一实例在不同服务下互不干扰")
    void serviceTypeIsPartOfKey() {
        final ServiceStateManager manager = new ServiceStateManager();
        final ModelRouterProperties.ModelInstance instance = instance(null, "http://172.16.30.10:9090", "qwen3:4b");

        manager.updateInstanceHealthStatus("chat", instance, false);

        assertFalse(manager.isInstanceHealthy("chat", instance));
        assertTrue(manager.isInstanceHealthy("embedding", instance));
    }

    @Test
    @DisplayName("无任何标识的实例落到 unknown 键，不再产生 chat:null")
    void instanceWithoutAnyIdentifierUsesUnknownKey() {
        final ServiceStateManager manager = new ServiceStateManager();

        manager.updateInstanceHealthStatus("chat", instance(null, null, null), false);

        assertTrue(manager.getAllInstanceHealthStatus().containsKey("chat:unknown"));
        assertFalse(manager.getAllInstanceHealthStatus().containsKey("chat:null"));
    }
}

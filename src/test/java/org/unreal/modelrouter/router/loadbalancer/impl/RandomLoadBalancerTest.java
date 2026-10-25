package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RandomLoadBalancer 的无状态与加权选择测试（v3.5.4，#648）。
 *
 * <p>核心约定：负载均衡器不再维护私有熔断器——连续失败不会让它在选路时剔除实例，
 * 熔断判定统一由 {@code CircuitBreakerManager} 在上游完成。</p>
 */
@DisplayName("RandomLoadBalancer 无状态选路（#648）")
class RandomLoadBalancerTest {

    private RandomLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        loadBalancer = new RandomLoadBalancer();
    }

    @Test
    @DisplayName("记录大量失败后仍能在候选内选路，不再抛「All instances are unavailable」")
    void failuresDoNotRemoveInstances() {
        List<ModelRouterProperties.ModelInstance> instances = instances("inst-1", "inst-2");

        for (int i = 0; i < 50; i++) {
            loadBalancer.recordCallFailure(instances.get(0));
        }

        for (int i = 0; i < 20; i++) {
            assertTrue(instances.contains(
                    loadBalancer.selectInstance(instances, "127.0.0.1", "chat")));
        }
    }

    @Test
    @DisplayName("权重为 0 或负数时退化为等概率选择")
    void nonPositiveWeightsFallBackToSimpleRandom() {
        List<ModelRouterProperties.ModelInstance> instances = instances("inst-1", "inst-2", "inst-3");
        instances.forEach(instance -> instance.setWeight(0));

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 600; i++) {
            String id = loadBalancer.selectInstance(instances, "127.0.0.1", "chat").getInstanceId();
            counts.merge(id, 1, Integer::sum);
        }

        assertEquals(3, counts.size(), "三个实例都应有机会被选中");
    }

    @Test
    @DisplayName("权重只落在加权更高的实例上")
    void weightedSelectionHonorsWeights() {
        List<ModelRouterProperties.ModelInstance> instances = instances("heavy", "light");
        instances.get(0).setWeight(9);
        instances.get(1).setWeight(1);

        int heavy = 0;
        for (int i = 0; i < 400; i++) {
            String id = loadBalancer.selectInstance(instances, "127.0.0.1", "chat").getInstanceId();
            if ("heavy".equals(id)) {
                heavy++;
            }
        }

        // 期望约 360/400；放宽到 280 以上，只验证权重确实生效
        assertTrue(heavy > 280, "权重 9:1 时高权重实例应占绝大多数，实际 " + heavy);
    }

    @Test
    @DisplayName("空候选列表抛 IllegalArgumentException")
    void emptyInstancesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> loadBalancer.selectInstance(new ArrayList<>(), "127.0.0.1", "chat"));
    }

    private static List<ModelRouterProperties.ModelInstance> instances(final String... ids) {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        for (String id : ids) {
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setName(id);
            instance.setInstanceId(id);
            instance.setBaseUrl("http://" + id + ":8080");
            instance.setPath("/v1/chat/completions");
            instances.add(instance);
        }
        return instances;
    }
}

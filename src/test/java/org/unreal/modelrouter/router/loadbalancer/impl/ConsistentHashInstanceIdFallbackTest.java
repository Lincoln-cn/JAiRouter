package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #691 一致性哈希的实例标识回退测试。
 *
 * <p>缺陷：虚拟节点键直接拼 {@code instance.getInstanceId()}，而该字段允许为空 —— 同服务下
 * 多个未配 {@code instance-id} 的实例会全部退化成 {@code "null#i"}，后写的节点覆盖先写的，
 * 环上只剩一个实例参与路由，其余副本空转。</p>
 *
 * <p>修复：虚拟节点键与缓存键统一走 {@code InstanceKeys.identifier(instance)}
 * （{@code instanceId → baseUrl → name → unknown}）。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#691 一致哈希虚拟节点键的标识回退")
class ConsistentHashInstanceIdFallbackTest {

    @Test
    @DisplayName("两个未配 instance-id 的实例都能分到流量")
    void instancesWithoutInstanceIdStillSpreadTraffic() {
        ConsistentHashLoadBalancer loadBalancer = new ConsistentHashLoadBalancer(50);
        List<ModelRouterProperties.ModelInstance> instances = List.of(
                instance(null, "http://10.0.0.1:8080", "gpt-4"),
                instance(null, "http://10.0.0.2:8080", "gpt-4"));

        Set<String> hit = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ModelRouterProperties.ModelInstance selected =
                    loadBalancer.selectInstance(instances, "203.0.113." + i, "chat");
            assertNotNull(selected, "每次选择都必须返回实例");
            hit.add(selected.getBaseUrl());
        }

        assertEquals(2, hit.size(), "两个实例都必须被命中，实际命中：" + hit);
    }

    @Test
    @DisplayName("空字符串 instance-id 同样回退到 baseUrl，不会与另一个实例共用节点")
    void blankInstanceIdFallsBackToBaseUrl() {
        ConsistentHashLoadBalancer loadBalancer = new ConsistentHashLoadBalancer(30);
        List<ModelRouterProperties.ModelInstance> instances = List.of(
                instance("", "http://10.0.0.11:8080", "gpt-4"),
                instance("   ", "http://10.0.0.12:8080", "gpt-4"));

        ConsistentHashLoadBalancer.HashRing ring = loadBalancer.ringFor(instances, "chat");

        assertEquals(2, ring.getInstanceCount(), "两个实例都必须进环");
        // 30 个虚拟节点 × 2 个实例 = 60；若标识退化成同一个，哈希键会互相覆盖导致节点数变少
        assertEquals(60, ring.size(), "两个实例的虚拟节点不得互相覆盖");
    }

    @Test
    @DisplayName("不同实例标识产生不同节点键；同一实例重复构建的环一致")
    void identifierChangeChangesTheRing() {
        ConsistentHashLoadBalancer withBaseUrl = new ConsistentHashLoadBalancer(10);
        ConsistentHashLoadBalancer withInstanceId = new ConsistentHashLoadBalancer(10);

        List<ModelRouterProperties.ModelInstance> fallbackSet = List.of(
                instance(null, "http://10.0.0.21:8080", "gpt-4"),
                instance(null, "http://10.0.0.22:8080", "gpt-4"));
        List<ModelRouterProperties.ModelInstance> idSet = List.of(
                instance("inst-21", "http://10.0.0.21:8080", "gpt-4"),
                instance("inst-22", "http://10.0.0.22:8080", "gpt-4"));

        ConsistentHashLoadBalancer.HashRing fallbackRing = withBaseUrl.ringFor(fallbackSet, "chat");
        ConsistentHashLoadBalancer.HashRing idRing = withInstanceId.ringFor(idSet, "chat");

        assertEquals(2, fallbackRing.getInstanceCount());
        assertEquals(2, idRing.getInstanceCount());
        // 环内容按各自标识计算，等价性不做断言（MurmurHash 不同键自然不同），只要求都能装下全部节点
        assertEquals(20, fallbackRing.size());
        assertEquals(20, idRing.size());

        // 同一批对象重复取环：必须命中缓存（构建次数不涨）
        withBaseUrl.ringFor(fallbackSet, "chat");
        assertEquals(1L, withBaseUrl.getRingBuildCount(), "同一实例集合不得重复构建");

        // 换成「同 baseUrl、不同 instanceId」的集合：键必须变化，因此是另一个环
        List<ModelRouterProperties.ModelInstance> distinct = new ArrayList<>(fallbackSet);
        distinct.set(0, instance("other-id", "http://10.0.0.21:8080", "gpt-4"));
        ConsistentHashLoadBalancer.HashRing otherRing = withBaseUrl.ringFor(distinct, "chat");
        assertNotEquals(fallbackRing, otherRing, "标识变化必须产生不同的环");
        assertEquals(2L, withBaseUrl.getRingBuildCount());
        assertTrue(withBaseUrl.getCachedRingCount() >= 2);
    }

    private static ModelRouterProperties.ModelInstance instance(final String instanceId,
                                                               final String baseUrl,
                                                               final String name) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId(instanceId);
        instance.setBaseUrl(baseUrl);
        instance.setName(name);
        instance.setHealthy(true);
        instance.setWeight(1);
        return instance;
    }
}

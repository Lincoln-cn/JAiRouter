package org.unreal.modelrouter.router.pool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.pool.model.PoolDefinition;
import org.unreal.modelrouter.router.pool.model.PoolMember;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * PoolSelector 资源池选择器测试
 * 池命中匹配 / 成员解析与权重覆盖 / 健康过滤 / 成员缺失跳过 / auto-model 回退
 */
@DisplayName("PoolSelector 资源池选择器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PoolSelectorTest {

    @Mock private ServiceStateManager serviceStateManager;
    @Mock private CircuitBreakerManager circuitBreakerManager;

    private PoolSelector selector;

    @BeforeEach
    void setUp() {
        selector = new PoolSelector(serviceStateManager, circuitBreakerManager);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(true);
    }

    private ModelRouterProperties.ModelInstance instance(final String id, final String name) {
        ModelRouterProperties.ModelInstance i = new ModelRouterProperties.ModelInstance();
        i.setInstanceId(id);
        i.setName(name);
        i.setBaseUrl("http://" + id + ".local");
        i.setStatus("active");
        i.setWeight(1);
        return i;
    }

    private PoolMember member(final String instanceId, final int weight) {
        PoolMember m = new PoolMember();
        m.setInstanceId(instanceId);
        m.setWeight(weight);
        return m;
    }

    private PoolDefinition pool(final String poolName, final String serviceType, final List<PoolMember> members) {
        PoolDefinition p = new PoolDefinition();
        p.setPoolName(poolName);
        p.setServiceType(serviceType);
        p.setStrategy("weighted-random");
        p.setMembers(members);
        return p;
    }

    @Test
    @DisplayName("POOL-001: findPool 按 (serviceType, poolName) 精确匹配启用池")
    void testFindPool_match() {
        selector.reloadPools(List.of(pool("auto-model", "chat", List.of(member("inst-gpt", 9)))));

        assertEquals("auto-model", selector.findPool(
                ModelServiceRegistry.ServiceType.chat, "auto-model").getPoolName());
        assertNull(selector.findPool(ModelServiceRegistry.ServiceType.chat, "gpt-4"),
                "非池名不命中");
        assertNull(selector.findPool(ModelServiceRegistry.ServiceType.embedding, "auto-model"),
                "serviceType 不匹配不命中");
    }

    @Test
    @DisplayName("POOL-002: 禁用的池不命中")
    void testFindPool_disabled() {
        PoolDefinition p = pool("auto-model", "chat", List.of(member("inst-gpt", 9)));
        p.setEnabled(false);
        selector.reloadPools(List.of(p));

        assertNull(selector.findPool(ModelServiceRegistry.ServiceType.chat, "auto-model"));
    }

    @Test
    @DisplayName("POOL-003: resolveCandidates 按 instanceId 解析成员并覆盖权重(副本不污染共享实例)")
    void testResolveCandidates_weightOverride() {
        PoolDefinition p = pool("auto-model", "chat", List.of(member("inst-gpt", 9), member("inst-claude", 1)));
        List<ModelRouterProperties.ModelInstance> instances = List.of(
                instance("inst-gpt", "gpt-4"), instance("inst-claude", "claude-3"));
        selector.reloadPools(List.of(p));

        List<ModelRouterProperties.ModelInstance> candidates =
                selector.resolveCandidates(p, instances);

        assertEquals(2, candidates.size());
        assertEquals("gpt-4", candidates.get(0).getName());
        assertEquals(9, candidates.get(0).getWeight());
        // 共享实例权重未被污染
        assertEquals(1, instances.get(0).getWeight());
    }

    @Test
    @DisplayName("POOL-004: 不健康成员被过滤")
    void testResolveCandidates_unhealthyFiltered() {
        PoolDefinition p = pool("auto-model", "chat", List.of(member("inst-gpt", 9), member("inst-claude", 1)));
        List<ModelRouterProperties.ModelInstance> instances = List.of(
                instance("inst-gpt", "gpt-4"), instance("inst-claude", "claude-3"));
        selector.reloadPools(List.of(p));
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenAnswer(inv ->
                !"inst-claude".equals(((ModelRouterProperties.ModelInstance) inv.getArgument(1)).getInstanceId()));

        List<ModelRouterProperties.ModelInstance> candidates =
                selector.resolveCandidates(p, instances);

        assertEquals(1, candidates.size());
        assertEquals("inst-gpt", candidates.get(0).getInstanceId());
    }

    @Test
    @DisplayName("POOL-005: 成员实例缺失(被删)跳过")
    void testResolveCandidates_missingMemberSkipped() {
        PoolDefinition p = pool("auto-model", "chat", List.of(member("inst-gpt", 9), member("gone", 1)));
        List<ModelRouterProperties.ModelInstance> instances = List.of(instance("inst-gpt", "gpt-4"));
        selector.reloadPools(List.of(p));

        List<ModelRouterProperties.ModelInstance> candidates =
                selector.resolveCandidates(p, instances);

        assertEquals(1, candidates.size());
        assertEquals("inst-gpt", candidates.get(0).getInstanceId());
    }

    @Test
    @DisplayName("POOL-006: autoFallbackCandidates 返回全部健康实例(跳过模型名过滤)")
    void testAutoFallback_allHealthy() {
        List<ModelRouterProperties.ModelInstance> instances = List.of(
                instance("inst-gpt", "gpt-4"), instance("inst-claude", "claude-3"));

        List<ModelRouterProperties.ModelInstance> candidates =
                selector.autoFallbackCandidates(instances, ModelServiceRegistry.ServiceType.chat);

        assertEquals(2, candidates.size(), "auto-model 回退应包含全部健康实例,不受模型名限制");
    }

    @Test
    @DisplayName("POOL-007: isPoolName 识别启用池名")
    void testIsPoolName() {
        PoolDefinition disabled = pool("disabled-pool", "chat", List.of(member("inst-gpt", 1)));
        disabled.setEnabled(false);
        selector.reloadPools(List.of(pool("auto-model", "chat", List.of(member("inst-gpt", 1))), disabled));

        assertTrue(selector.isPoolName("auto-model"));
        assertFalse(selector.isPoolName("disabled-pool"), "禁用池不应命中");
        assertFalse(selector.isPoolName("gpt-4"), "非池名不应命中");
        assertFalse(selector.isPoolName(null));
    }
}

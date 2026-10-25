package org.unreal.modelrouter.router.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.factory.ComponentFactory;
import org.unreal.modelrouter.router.loadbalancer.impl.ConsistentHashLoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.impl.RandomLoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.impl.RoundRobinLoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按策略名取用负载均衡器的缓存语义（v3.5.4，#649）。
 *
 * <p>修复前 `getLoadBalancerByStrategy` 每次调用都新建实例，round-robin / least-connections
 * 的计数随请求归零 ⇒ 池路由等价于「固定选第一个成员」。</p>
 */
@DisplayName("LoadBalancerManager 策略缓存（#649）")
class LoadBalancerManagerStrategyCacheTest {

    private LoadBalancerManager manager;

    @BeforeEach
    void setUp() {
        ComponentFactory factory = mock(ComponentFactory.class);
        when(factory.createLoadBalancer(any())).thenAnswer(invocation -> {
            ModelRouterProperties.LoadBalanceConfig config = invocation.getArgument(0);
            if (config != null && "bogus".equalsIgnoreCase(config.getType())) {
                throw new IllegalArgumentException("Unsupported load balancer: " + config.getType());
            }
            return "consistent-hash".equalsIgnoreCase(config == null ? null : config.getType())
                    ? new ConsistentHashLoadBalancer(150)
                    : new RoundRobinLoadBalancer();
        });

        manager = new LoadBalancerManager(factory,
                mock(ServiceTypeResolver.class),
                mock(ConfigConverterHelper.class),
                new ModelRouterProperties());
    }

    @Test
    @DisplayName("同一策略多次取用返回同一实例")
    void sameStrategyReturnsCachedInstance() {
        LoadBalancer first = manager.getLoadBalancerByStrategy("round-robin");
        LoadBalancer second = manager.getLoadBalancerByStrategy("ROUND-ROBIN ");

        assertNotNull(first);
        assertSame(first, second, "策略相同（忽略大小写与空格）时必须复用同一实例，否则计数每请求归零");
    }

    @Test
    @DisplayName("缓存的 round-robin 在多次请求间轮转，而不是固定选第一个")
    void cachedRoundRobinActuallyRotates() {
        LoadBalancer loadBalancer = manager.getLoadBalancerByStrategy("round-robin");
        List<ModelRouterProperties.ModelInstance> instances = instances();

        List<String> selected = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            selected.add(loadBalancer.selectInstance(instances, "127.0.0.1", "chat").getInstanceId());
        }

        assertEquals(List.of("inst-1", "inst-2", "inst-1", "inst-2"), selected);
    }

    @Test
    @DisplayName("不同策略各自缓存，互不干扰")
    void differentStrategiesGetDifferentInstances() {
        LoadBalancer roundRobin = manager.getLoadBalancerByStrategy("round-robin");
        LoadBalancer consistentHash = manager.getLoadBalancerByStrategy("consistent-hash");

        assertNotNull(roundRobin);
        assertNotNull(consistentHash);
        assertEquals(RoundRobinLoadBalancer.class, roundRobin.getClass());
        assertEquals(ConsistentHashLoadBalancer.class, consistentHash.getClass());
    }

    @Test
    @DisplayName("空白策略返回 null，未知策略创建失败也返回 null")
    void blankAndUnknownStrategiesReturnNull() {
        assertNull(manager.getLoadBalancerByStrategy(null));
        assertNull(manager.getLoadBalancerByStrategy("   "));
        assertNull(manager.getLoadBalancerByStrategy("bogus"));
    }

    @Test
    @DisplayName("策略缓存不污染服务级注册表")
    void strategyCacheDoesNotPolluteServiceRegistry() {
        int before = manager.getLoadBalancerCount();
        manager.getLoadBalancerByStrategy("round-robin");
        manager.getLoadBalancerByStrategy("consistent-hash");

        assertEquals(before, manager.getLoadBalancerCount(),
                "策略缓存是旁路缓存，服务级负载均衡器数量不应变化");
    }

    private static List<ModelRouterProperties.ModelInstance> instances() {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setName("qwen3:4b");
            instance.setInstanceId("inst-" + i);
            instance.setBaseUrl("http://10.0.0." + i + ":8080");
            instance.setPath("/v1/chat/completions");
            instances.add(instance);
        }
        return instances;
    }
}

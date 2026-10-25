package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 熔断记账口径测试（v3.5.4，#647 / #655）。
 *
 * <p>约定：只有 `success=true` 才重置熔断失败计数；「完成了但不算成功」的调用
 * （例如 4xx 客户端错误）只让负载均衡器的在途计数递减。</p>
 */
@DisplayName("ModelServiceRegistry 熔断记账口径（#647）")
class ModelServiceRegistryCircuitAccountingTest {

    private static final ModelServiceRegistry.ServiceType CHAT = ModelServiceRegistry.ServiceType.chat;

    private ModelServiceRegistry registry;

    private LoadBalancer loadBalancer;

    private CircuitBreakerManager circuitBreakerManager;

    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() throws Exception {
        registry = mock(ModelServiceRegistry.class, CALLS_REAL_METHODS);
        LoadBalancerManager loadBalancerManager = mock(LoadBalancerManager.class);
        loadBalancer = mock(LoadBalancer.class);
        circuitBreakerManager = mock(CircuitBreakerManager.class);

        when(loadBalancerManager.getLoadBalancer(CHAT)).thenReturn(loadBalancer);
        setField(registry, "loadBalancerManager", loadBalancerManager);
        setField(registry, "circuitBreakerManager", circuitBreakerManager);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setName("qwen3:4b");
        instance.setInstanceId("inst-1");
        instance.setBaseUrl("http://172.16.30.6:9090");
    }

    @Test
    @DisplayName("success=false 时不重置熔断计数，但负载均衡器仍在途计数递减")
    void completeWithoutSuccessKeepsBreakerCounters() {
        registry.recordCallComplete(CHAT, instance, 12L, false);

        verify(loadBalancer).recordCallComplete(instance, 12L, false);
        verify(circuitBreakerManager, never()).recordSuccess(any(), any());
    }

    @Test
    @DisplayName("success=true 时重置熔断计数")
    void completeWithSuccessResetsCounters() {
        registry.recordCallComplete(CHAT, instance, 9L, true);

        verify(loadBalancer).recordCallComplete(instance, 9L, true);
        verify(circuitBreakerManager).recordSuccess("inst-1", "http://172.16.30.6:9090");
    }

    @Test
    @DisplayName("失败路径计入熔断失败")
    void failureRecordsBreakerFailure() {
        registry.recordCallFailure(CHAT, instance, 40L, "UPSTREAM_500");

        verify(loadBalancer).recordCallFailure(instance, 40L, "UPSTREAM_500");
        verify(circuitBreakerManager).recordFailure("inst-1", "http://172.16.30.6:9090");
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        Field field = ModelServiceRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

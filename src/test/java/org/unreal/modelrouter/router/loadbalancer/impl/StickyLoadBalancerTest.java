package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * v2.9.0: StickyLoadBalancer 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StickyLoadBalancer 单元测试")
class StickyLoadBalancerTest {

    @Mock
    private LoadBalancer delegate;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private CircuitBreakerManager circuitBreakerManager;

    private StickyLoadBalancer stickyLb;

    @BeforeEach
    void setUp() {
        stickyLb = new StickyLoadBalancer(delegate, serviceStateManager, circuitBreakerManager);
    }

    @Test
    @DisplayName("affinityKey 为 null 时委托给 delegate")
    void nullAffinityKeyDelegates() {
        List<ModelRouterProperties.ModelInstance> instances = createInstances(3);
        ModelRouterProperties.ModelInstance expected = instances.get(1);
        when(delegate.selectInstance(eq(instances), anyString(), eq("chat")))
                .thenReturn(expected);

        ModelRouterProperties.ModelInstance result =
                stickyLb.selectInstance(instances, null, "192.168.1.1", "chat");

        assertEquals(expected, result);
        verify(delegate).selectInstance(instances, "192.168.1.1", "chat");
    }

    @Test
    @DisplayName("affinityKey 为空白时委托给 delegate")
    void blankAffinityKeyDelegates() {
        List<ModelRouterProperties.ModelInstance> instances = createInstances(3);
        ModelRouterProperties.ModelInstance expected = instances.get(0);
        when(delegate.selectInstance(eq(instances), anyString(), eq("chat")))
                .thenReturn(expected);

        ModelRouterProperties.ModelInstance result =
                stickyLb.selectInstance(instances, "  ", "192.168.1.1", "chat");

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("相同 affinityKey 总是选同一实例(粘性一致性)")
    void sameKeySelectsSameInstance() {
        List<ModelRouterProperties.ModelInstance> instances = createInstances(3);
        // 所有实例健康
        when(circuitBreakerManager.getState(anyString(), anyString()))
                .thenReturn(CircuitBreaker.State.CLOSED);

        ModelRouterProperties.ModelInstance first =
                stickyLb.selectInstance(instances, "tenant-1|chat|model-a", "10.0.0.1", "chat");
        ModelRouterProperties.ModelInstance second =
                stickyLb.selectInstance(instances, "tenant-1|chat|model-a", "10.0.0.2", "chat");

        assertNotNull(first);
        assertEquals(first.getName(), second.getName(),
                "same affinity key must route to the same instance");
    }

    @Test
    @DisplayName("不同 affinityKey 可能选不同实例")
    void differentKeysMaySelectDifferentInstances() {
        List<ModelRouterProperties.ModelInstance> instances = createInstances(10);
        when(circuitBreakerManager.getState(anyString(), anyString()))
                .thenReturn(CircuitBreaker.State.CLOSED);

        ModelRouterProperties.ModelInstance inst1 =
                stickyLb.selectInstance(instances, "tenant-1|chat|model-a", "10.0.0.1", "chat");
        ModelRouterProperties.ModelInstance inst2 =
                stickyLb.selectInstance(instances, "tenant-2|chat|model-b", "10.0.0.2", "chat");

        assertNotNull(inst1);
        assertNotNull(inst2);
        // 10个实例+不同key，高度可能选不同实例(非100%保证，但概率极高)
        // 至少验证两个都非null即可
    }

    @Test
    @DisplayName("粘性目标实例不可用时回退到 delegate")
    void fallbackWhenStickyTargetUnavailable() {
        List<ModelRouterProperties.ModelInstance> instances = createInstances(3);

        // Mock: 所有实例熔断(包括粘性目标)
        when(circuitBreakerManager.getState(anyString(), anyString()))
                .thenReturn(CircuitBreaker.State.OPEN);

        // delegate 回退时返回一个有效实例
        ModelRouterProperties.ModelInstance fallbackInstance = instances.get(2);
        when(delegate.selectInstance(eq(instances), anyString(), eq("chat")))
                .thenReturn(fallbackInstance);

        ModelRouterProperties.ModelInstance result =
                stickyLb.selectInstance(instances, "key-1|chat|model", "10.0.0.1", "chat");

        assertNotNull(result);
        assertEquals(fallbackInstance.getName(), result.getName());
        // 回退时应该调用 delegate
        verify(delegate).selectInstance(eq(instances), anyString(), eq("chat"));
    }

    @Test
    @DisplayName("空实例列表委托给 delegate")
    void emptyInstancesDelegates() {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        when(delegate.selectInstance(eq(instances), anyString(), eq("chat")))
                .thenReturn(null);

        ModelRouterProperties.ModelInstance result =
                stickyLb.selectInstance(instances, "key-1|chat|model", "10.0.0.1", "chat");

        assertNull(result);
        verify(delegate).selectInstance(instances, "10.0.0.1", "chat");
    }

    @Test
    @DisplayName("delegate 方法正确转发")
    void delegateMethodsForwarded() {
        ModelRouterProperties.ModelInstance instance = createInstance("inst-1");
        stickyLb.recordCall(instance);
        stickyLb.recordCallComplete(instance);
        stickyLb.recordCallFailure(instance);

        verify(delegate).recordCall(instance);
        verify(delegate).recordCallComplete(instance);
        verify(delegate).recordCallFailure(instance);
    }

    @Test
    @DisplayName("getDelegate 返回委托 LB")
    void getDelegateReturnsDelegate() {
        assertSame(delegate, stickyLb.getDelegate());
    }

    @Test
    @DisplayName("2参数 selectInstance 委托给 delegate")
    void twoArgSelectDelegates() {
        List<ModelRouterProperties.ModelInstance> instances = createInstances(2);
        ModelRouterProperties.ModelInstance expected = instances.get(0);
        when(delegate.selectInstance(instances, "10.0.0.1")).thenReturn(expected);

        ModelRouterProperties.ModelInstance result =
                stickyLb.selectInstance(instances, "10.0.0.1");

        assertEquals(expected, result);
    }

    // ==================== Helper Methods ====================

    private List<ModelRouterProperties.ModelInstance> createInstances(int count) {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            instances.add(createInstance("instance-" + i));
        }
        return instances;
    }

    private ModelRouterProperties.ModelInstance createInstance(String name) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setInstanceId(name);
        instance.setBaseUrl("http://" + name + ":8080");
        instance.setHealthy(true);
        return instance;
    }
}

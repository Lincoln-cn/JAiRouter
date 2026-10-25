package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实例级限流拒绝后的回退测试（v3.5.4，#654）。
 *
 * <p>旧实现只尝试前 3 个候选：前 3 个都被实例级限流拒绝时直接返回 null，
 * 调用方据此回 503「All instances rate limited」——即使第 4 个实例仍在配额内。</p>
 */
@DisplayName("ServiceInstanceSelector 实例级限流回退（#654）")
class ServiceInstanceSelectorRateLimitFallbackTest {

    private static final ModelServiceRegistry.ServiceType CHAT = ModelServiceRegistry.ServiceType.chat;

    private RateLimitManager rateLimitManager;

    private LoadBalancer loadBalancer;

    private ServiceInstanceSelector selector;

    @BeforeEach
    void setUp() {
        rateLimitManager = mock(RateLimitManager.class);
        loadBalancer = mock(LoadBalancer.class);
        selector = new ServiceInstanceSelector(
                mock(ServiceStateManager.class),
                rateLimitManager,
                mock(CircuitBreakerManager.class),
                mock(RoutingMonitorService.class));

        // 负载均衡器始终返回当前候选列表的第一个：被拒的实例会被移除，因此下一次自然轮到下一个
        when(loadBalancer.selectInstance(anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> firstOf(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("前三个实例被限流时仍会选中第四个仍在配额内的实例")
    void skipsRateLimitedCandidatesUntilOneAccepts() {
        List<ModelRouterProperties.ModelInstance> instances = instances(5);
        when(rateLimitManager.tryAcquireInstance(any(RateLimitContext.class)))
                .thenAnswer(invocation -> {
                    RateLimitContext context = invocation.getArgument(0);
                    return "inst-4".equals(context.getInstanceId()) || "inst-5".equals(context.getInstanceId());
                });

        ModelRouterProperties.ModelInstance selected = selector.selectWithRateLimit(
                instances, loadBalancer, "127.0.0.1", CHAT, "gpt-4");

        assertSame(instances.get(3), selected, "应跳过前三个被限流的实例，选中 inst-4");
        verify(rateLimitManager, times(4)).tryAcquireInstance(any(RateLimitContext.class));
    }

    @Test
    @DisplayName("全部候选都被限流时返回 null，且遍历所有候选而不是只试三个")
    void returnsNullOnlyAfterExhaustingAllCandidates() {
        List<ModelRouterProperties.ModelInstance> instances = instances(5);
        when(rateLimitManager.tryAcquireInstance(any(RateLimitContext.class))).thenReturn(false);

        assertNull(selector.selectWithRateLimit(instances, loadBalancer, "127.0.0.1", CHAT, "gpt-4"));
        verify(rateLimitManager, times(5)).tryAcquireInstance(any(RateLimitContext.class));
    }

    @Test
    @DisplayName("有会话亲和键时走带亲和键的选择重载")
    void passesAffinityKeyToLoadBalancer() {
        List<ModelRouterProperties.ModelInstance> instances = instances(1);
        when(rateLimitManager.tryAcquireInstance(any(RateLimitContext.class))).thenReturn(true);
        when(loadBalancer.selectInstance(anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> firstOf(invocation.getArgument(0)));

        ModelRouterProperties.ModelInstance selected = selector.selectWithRateLimit(
                instances, loadBalancer, "apiKey:abc", "127.0.0.1", CHAT, "gpt-4");

        assertEquals("inst-1", selected.getInstanceId());
        verify(loadBalancer).selectInstance(instances, "apiKey:abc", "127.0.0.1", "chat");
    }

    @Test
    @DisplayName("负载均衡器返回 null 时安全退出，不抛 NPE")
    void nullSelectionFromLoadBalancerReturnsNull() {
        when(loadBalancer.selectInstance(anyList(), anyString(), anyString())).thenReturn(null);

        assertNull(selector.selectWithRateLimit(instances(2), loadBalancer, "127.0.0.1", CHAT, "gpt-4"));
    }

    private static List<ModelRouterProperties.ModelInstance> instances(final int count) {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setName("qwen3:4b");
            instance.setInstanceId("inst-" + i);
            instance.setBaseUrl("http://10.0.0." + i + ":8080");
            instance.setPath("/v1/chat/completions");
            instances.add(instance);
        }
        return instances;
    }

    @SuppressWarnings("unchecked")
    private static ModelRouterProperties.ModelInstance firstOf(final Object candidates) {
        List<ModelRouterProperties.ModelInstance> list =
                (List<ModelRouterProperties.ModelInstance>) candidates;
        return list.isEmpty() ? null : list.get(0);
    }
}

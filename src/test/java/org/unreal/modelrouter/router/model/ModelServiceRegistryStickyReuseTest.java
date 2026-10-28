package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.loadbalancer.AffinityContextHolder;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.loadbalancer.impl.StickyLoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #690 粘性负载均衡器复用测试.
 *
 * <p>缺陷：`ModelServiceRegistry` 在每次选择实例时 `new StickyLoadBalancer(...)`，而该类用实例字段缓存
 * 哈希环快照（指纹不变就不重建）⇒ 每次都是新对象，缓存永远命中不了，每个请求都全量重建
 * {@code virtualNodes × 实例数} 次哈希。修法是按委托 LB 复用包装器。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#690 粘性负载均衡器复用")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistryStickyReuseTest {

    @Mock private ModelRouterProperties properties;
    @Mock private ServiceStateManager serviceStateManager;
    @Mock private RateLimitManager rateLimitManager;
    @Mock private LoadBalancerManager loadBalancerManager;
    @Mock private CircuitBreakerManager circuitBreakerManager;
    @Mock private FallbackManager fallbackManager;
    @Mock private ConfigMergeService configMergeService;
    @Mock private ServiceTypeResolver serviceTypeResolver;
    @Mock private ConfigConverterHelper configConverterHelper;
    @Mock private WebClientCacheManager webClientCacheManager;
    @Mock private RoutingMonitorService routingMonitorService;
    @Mock private LoadBalancer delegate;

    private ModelServiceRegistry registry;
    private ModelRouterProperties.ModelInstance instanceA;
    private ModelRouterProperties.ModelInstance instanceB;

    @BeforeEach
    void setUp() throws Exception {
        when(serviceTypeResolver.getServiceConfigKey(any())).thenReturn("chat");
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(delegate);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(any(), any())).thenReturn(true);
        when(delegate.selectInstance(any(), any(), any())).thenAnswer(inv -> {
            List<ModelRouterProperties.ModelInstance> instances = inv.getArgument(0);
            return instances.get(0);
        });
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(true);

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);

        instanceA = instance("inst-a", "gpt-4", "http://a.local");
        instanceB = instance("inst-b", "gpt-4", "http://b.local");

        ServiceRuntimeConfig runtimeConfig = new ServiceRuntimeConfig();
        runtimeConfig.setInstances(List.of(instanceA, instanceB));
        Map<String, ServiceRuntimeConfig> cache = new ConcurrentHashMap<>();
        cache.put("chat", runtimeConfig);
        setField(registry, "serviceConfigCache", cache);

        // 粘性：服务配置里开启（实例数 > 1 才会真正生效）
        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
        serviceConfig.setSticky(new ModelRouterProperties.StickyConfig());
        services.put("chat", serviceConfig);
        when(properties.getServices()).thenReturn(services);
    }

    @AfterEach
    void tearDown() {
        AffinityContextHolder.clear();
    }

    @Test
    @DisplayName("STICKY-001: 同一委托 LB 永远拿到同一个粘性包装器")
    void stickyWrapperIsReusedPerDelegate() {
        LoadBalancer first = registry.stickyLoadBalancerFor(delegate);
        LoadBalancer second = registry.stickyLoadBalancerFor(delegate);

        assertSame(first, second, "同一委托 LB 必须复用同一个包装器，否则内部哈希环缓存永不命中");
        assertEquals(1, registry.getStickyLoadBalancerCacheSize(), "同一个委托只应占一条缓存");
        assertNull(registry.stickyLoadBalancerFor(null), "null 委托返回 null");
    }

    @Test
    @DisplayName("STICKY-002: 不同委托 LB 得到不同包装器，互不串用")
    void differentDelegatesGetDifferentWrappers() {
        LoadBalancer otherDelegate = mock(LoadBalancer.class);

        LoadBalancer first = registry.stickyLoadBalancerFor(delegate);
        LoadBalancer second = registry.stickyLoadBalancerFor(otherDelegate);

        assertNotSame(first, second);
        assertEquals(2, registry.getStickyLoadBalancerCacheSize());
    }

    @Test
    @DisplayName("STICKY-003: 连续两次 selectInstance 使用的是同一个粘性包装器")
    void selectInstanceReusesTheSameStickyWrapper() throws Exception {
        ServiceInstanceSelector selector = mock(ServiceInstanceSelector.class);
        when(selector.selectWithRateLimit(any(), any(), any(), any(), any(), any()))
                .thenReturn(instanceA);
        setField(registry, "instanceSelector", selector);

        AffinityContextHolder.set("key-1", "203.0.113.7", "chat", "gpt-4");
        registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "203.0.113.7");
        registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "203.0.113.7");

        ArgumentCaptor<LoadBalancer> captor = ArgumentCaptor.forClass(LoadBalancer.class);
        verify(selector, org.mockito.Mockito.times(2))
                .selectWithRateLimit(any(), captor.capture(), any(), any(), any(), any());

        List<LoadBalancer> used = captor.getAllValues();
        assertSame(used.get(0), used.get(1),
                "两次选择必须复用同一个粘性包装器（旧实现每次 new，哈希环缓存永不命中）");
        assertEquals(StickyLoadBalancer.class, used.get(0).getClass(), "粘性路径应包装为 StickyLoadBalancer");
        assertEquals(1, registry.getStickyLoadBalancerCacheSize());
    }

    private ModelRouterProperties.ModelInstance instance(final String id, final String name, final String baseUrl) {
        ModelRouterProperties.ModelInstance inst = new ModelRouterProperties.ModelInstance();
        inst.setInstanceId(id);
        inst.setId(id);
        inst.setName(name);
        inst.setBaseUrl(baseUrl);
        inst.setStatus("active");
        return inst;
    }

    private void setField(final Object target, final String fieldName, final Object value) throws Exception {
        Field field = ModelServiceRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.model.WebClientCacheManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.ratelimit.ServiceRateLimitHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * #901：实例级限流把候选实例全部拒绝时，必须回 429（限流），而不是 503（服务不可用）。
 *
 * <p>回归意义：503 会被客户端 SDK 当成可重试的 5xx 故障（OpenAI 官方 SDK 默认重试 5xx），
 * 在限流桶已空的情况下继续放大流量；监控与负载均衡也会把它记成实例故障。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistryRateLimitStatusTest {

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

    @BeforeEach
    void setUp() {
        when(serviceTypeResolver.getServiceConfigKey(any())).thenReturn("chat");
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(delegate);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);
        when(circuitBreakerManager.canExecute(any(), any())).thenReturn(true);
        when(delegate.selectInstance(any(), any(), any())).thenAnswer(inv -> {
            List<ModelRouterProperties.ModelInstance> instances = inv.getArgument(0);
            return instances.isEmpty() ? null : instances.get(0);
        });

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);

        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("gpt-4");
        instance.setInstanceId("inst-a");
        instance.setBaseUrl("http://a.local");
        instance.setStatus("active");

        ServiceRuntimeConfig runtimeConfig = new ServiceRuntimeConfig();
        runtimeConfig.setInstances(List.of(instance));
        Map<String, ServiceRuntimeConfig> cache = new ConcurrentHashMap<>();
        cache.put("chat", runtimeConfig);
        setField(registry, "serviceConfigCache", cache);

        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        services.put("chat", new ModelRouterProperties.ServiceConfig());
        when(properties.getServices()).thenReturn(services);
    }

    @AfterEach
    void tearDown() {
        ServiceRateLimitHolder.clear();
    }

    @Test
    @DisplayName("901-001: 实例级限流全拒时状态码是 429 而不是 503")
    void allInstancesRateLimitedReturnsTooManyRequests() {
        // 服务级限流在 handler 层已预扣（缓存命中路径），此处跳过以免先抛「服务级 429」
        ServiceRateLimitHolder.markAcquired();
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> registry.selectInstance(
                        ModelServiceRegistry.ServiceType.chat, "gpt-4", "127.0.0.1", Map.of()));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode(),
                "限流拒绝必须是 429；503 会让客户端 SDK 当成服务故障继续重试");
        assertEquals("All instances rate limited for model 'gpt-4'", error.getReason(),
                "文案保持不变，便于既有告警规则继续匹配");
    }

    private static void setField(final Object target, final String name, final Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

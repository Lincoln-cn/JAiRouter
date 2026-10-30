package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * #819 运行时配置热更新的「快照式整体替换 + 失败回滚」测试.
 *
 * <p>缺陷：动态更新（{@code updateServiceInstances} / {@code updateServiceAdapter}）直接就地
 * set 缓存里的 {@link ServiceRuntimeConfig}，并发请求会读到「实例已换、适配器还是旧的」这种
 * 半更新状态；而按服务刷新把缓存整体换掉之后，若负载均衡器/限流器应用失败，只留一句 warn，
 * 运行时停在「缓存是新的、LB 是旧的」这种不一致状态，且没有任何回滚。</p>
 *
 * @author JAiRouter Team
 */
@DisplayName("#819 运行时配置快照替换与回滚")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistrySnapshotUpdateTest {

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

    private ModelServiceRegistry registry;
    private Map<String, Object> services;

    @BeforeEach
    void setUp() {
        when(serviceTypeResolver.getServiceConfigKey(any())).thenAnswer(inv -> {
            ModelServiceRegistry.ServiceType type = inv.getArgument(0);
            return type == null ? null : type.name();
        });
        when(serviceTypeResolver.parseServiceType(any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            for (ModelServiceRegistry.ServiceType type : ModelServiceRegistry.ServiceType.values()) {
                if (type.name().equalsIgnoreCase(key)) {
                    return type;
                }
            }
            return null;
        });
        when(configConverterHelper.convertMapToInstance(any())).thenAnswer(inv -> {
            Map<String, Object> map = inv.getArgument(0);
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setInstanceId((String) map.get("id"));
            instance.setId((String) map.get("id"));
            instance.setName((String) map.get("name"));
            instance.setBaseUrl((String) map.get("baseUrl"));
            instance.setStatus("active");
            return instance;
        });
        when(configConverterHelper.convertMapToServiceConfig(any())).thenAnswer(inv -> {
            Map<String, Object> map = inv.getArgument(0);
            ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
            serviceConfig.setAdapter((String) map.get("adapter"));
            return serviceConfig;
        });
        when(configConverterHelper.createDefaultLoadBalanceConfig()).thenAnswer(inv -> {
            ModelRouterProperties.LoadBalanceConfig loadBalance = new ModelRouterProperties.LoadBalanceConfig();
            loadBalance.setType("round-robin");
            return loadBalance;
        });

        Map<String, Object> persistedConfig = new LinkedHashMap<>();
        services = new LinkedHashMap<>();
        persistedConfig.put("services", services);
        services.put("chat", serviceConfig("chat-v1"));
        when(configMergeService.getPersistedConfig()).thenReturn(persistedConfig);

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);
        registry.refreshFromMergedConfig();
    }

    @Test
    @DisplayName("819-001: 实例更新走写时复制，旧快照不被就地改写")
    void instanceUpdateIsCopyOnWrite() throws Exception {
        ServiceRuntimeConfig before = cache().get("chat");
        ModelRouterProperties.ModelInstance next = instance("inst-next", "http://next.local");

        registry.updateServiceInstances(ModelServiceRegistry.ServiceType.chat, List.of(next));

        ServiceRuntimeConfig after = cache().get("chat");
        assertNotSame(before, after, "更新必须换新快照，而不是就地 set");
        assertEquals(1, before.getInstances().size(), "已被读走的旧快照必须保持原样");
        assertEquals("inst-chat-v1", before.getInstances().get(0).getInstanceId());
        assertEquals(1, after.getInstances().size());
        assertEquals("inst-next", after.getInstances().get(0).getInstanceId());
        assertEquals("chat-v1", after.getAdapter(), "只改实例时适配器必须保持不变");
    }

    @Test
    @DisplayName("819-002: 适配器更新走写时复制，旧快照不被就地改写")
    void adapterUpdateIsCopyOnWrite() throws Exception {
        ServiceRuntimeConfig before = cache().get("chat");

        registry.updateServiceAdapter(ModelServiceRegistry.ServiceType.chat, "chat-v2");

        ServiceRuntimeConfig after = cache().get("chat");
        assertNotSame(before, after);
        assertEquals("chat-v1", before.getAdapter(), "已被读走的旧快照必须保持原样");
        assertEquals("chat-v2", after.getAdapter());
        assertEquals(1, after.getInstances().size(), "只改适配器时实例必须保持不变");
    }

    @Test
    @DisplayName("819-003: 负载均衡器应用失败时整体回滚，缓存仍指向旧快照")
    void failedRuntimeEffectRollsBackTheSnapshot() throws Exception {
        ServiceRuntimeConfig before = cache().get("chat");
        services.put("chat", serviceConfig("chat-v2"));
        doThrow(new IllegalStateException("load balancer exploded"))
                .when(loadBalancerManager).reinitializeLoadBalancer(
                        eq(ModelServiceRegistry.ServiceType.chat), any());

        assertThrows(IllegalStateException.class,
                () -> registry.refreshServiceConfig("chat"),
                "派生组件没跟上时必须让调用方知道，而不是静默半更新");

        assertSame(before, cache().get("chat"), "回滚后缓存必须还是刷新前的那个快照");
        assertEquals("chat-v1", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.chat));
    }

    private ModelRouterProperties.ModelInstance instance(final String id, final String baseUrl) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId(id);
        instance.setId(id);
        instance.setName("model-x");
        instance.setBaseUrl(baseUrl);
        instance.setStatus("active");
        return instance;
    }

    private Map<String, Object> serviceConfig(final String adapter) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("adapter", adapter);
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("id", "inst-" + adapter);
        instance.put("name", "model-x");
        instance.put("baseUrl", "http://" + adapter + ".local");
        config.put("instances", List.of(instance));
        Map<String, Object> loadBalance = new LinkedHashMap<>();
        loadBalance.put("type", "round-robin");
        config.put("loadBalance", loadBalance);
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ServiceRuntimeConfig> cache() throws Exception {
        Field field = ModelServiceRegistry.class.getDeclaredField("serviceConfigCache");
        field.setAccessible(true);
        return new ConcurrentHashMap<>((Map<String, ServiceRuntimeConfig>) field.get(registry));
    }
}

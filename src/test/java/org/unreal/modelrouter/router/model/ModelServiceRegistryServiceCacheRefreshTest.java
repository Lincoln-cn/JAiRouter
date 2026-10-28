package org.unreal.modelrouter.router.model;

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
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #707 服务类型配置写入后本机运行时缓存刷新测试.
 *
 * <p>缺陷：{@code ConfigurationService} 写入服务配置后只把变更广播出去（{@code ConfigSyncEvent}），
 * 从未重建本进程的 {@code ModelServiceRegistry.serviceConfigCache} ⇒ 控制台报保存成功，
 * 路由继续按旧 adapter / 负载均衡 / 限流配置跑，直到进程重启。</p>
 *
 * <p>本测试直接作用于 {@link ModelServiceRegistry#refreshServiceConfig(String)} /
 * {@link ModelServiceRegistry#refreshServiceConfigs(java.util.Collection)}，断言：写入后立刻读到新值、
 * 未涉及的服务不被重建、被删掉的服务不会继续参与路由、单个服务配置写坏不影响已落库的写入。</p>
 *
 * @author JAiRouter Team
 */
@DisplayName("#707 运行时缓存按服务精确刷新")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistryServiceCacheRefreshTest {

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
    private Map<String, Object> persistedConfig;
    private Map<String, Object> services;

    @BeforeEach
    void setUp() throws Exception {
        when(serviceTypeResolver.getServiceConfigKey(any())).thenAnswer(inv -> {
            ModelServiceRegistry.ServiceType type = inv.getArgument(0);
            if (type == null) {
                return null;
            }
            switch (type) {
                case chat:
                    return "chat";
                case embedding:
                    return "embedding";
                case imgGen:
                    return "img-gen";
                case imgEdit:
                    return "img-edit";
                default:
                    return type.name();
            }
        });
        when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);
        when(serviceTypeResolver.parseServiceType("embedding")).thenReturn(ModelServiceRegistry.ServiceType.embedding);
        when(serviceTypeResolver.parseServiceType("img-gen")).thenReturn(ModelServiceRegistry.ServiceType.imgGen);
        when(serviceTypeResolver.parseServiceType("imgGen")).thenReturn(ModelServiceRegistry.ServiceType.imgGen);
        when(serviceTypeResolver.parseServiceType("IMG-GEN")).thenReturn(ModelServiceRegistry.ServiceType.imgGen);

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

        persistedConfig = new LinkedHashMap<>();
        services = new LinkedHashMap<>();
        persistedConfig.put("services", services);
        services.put("chat", serviceConfig("chat-v1", true, true));
        services.put("embedding", serviceConfig("embedding-v1", true, false));
        when(configMergeService.getPersistedConfig()).thenReturn(persistedConfig);

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);
    }

    @Test
    @DisplayName("CACHE-001: 写入后立刻读到新值，未涉及的服务不被重建")
    void refreshRebuildsOnlyTheRequestedService() throws Exception {
        registry.refreshFromMergedConfig();
        assertEquals("chat-v1", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.chat));
        clearInvocations(loadBalancerManager, rateLimitManager);

        ServiceRuntimeConfig chatBefore = cache().get("chat");
        ServiceRuntimeConfig embeddingBefore = cache().get("embedding");
        assertNotNull(chatBefore);
        assertNotNull(embeddingBefore);

        services.put("chat", serviceConfig("chat-v2", true, true));

        assertTrue(registry.refreshServiceConfig("chat"), "chat 的缓存条目应被重建");
        assertEquals("chat-v2", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.chat),
                "写入后必须立刻读到新 adapter（旧实现要等重启）");
        assertNotSame(chatBefore, cache().get("chat"), "被刷新的服务必须换新对象");
        assertSame(embeddingBefore, cache().get("embedding"), "未涉及的服务不应被重建（精确失效，不是整表清空）");

        verify(loadBalancerManager).reinitializeLoadBalancer(
                eq(ModelServiceRegistry.ServiceType.chat),
                any(ModelRouterProperties.LoadBalanceConfig.class));
        verify(loadBalancerManager, never()).reinitializeLoadBalancer(
                eq(ModelServiceRegistry.ServiceType.embedding),
                any(ModelRouterProperties.LoadBalanceConfig.class));
    }

    @Test
    @DisplayName("CACHE-002: 限流段只应用到被刷新的服务")
    void rateLimitIsAppliedOnlyToTheRefreshedService() {
        registry.refreshFromMergedConfig();
        clearInvocations(rateLimitManager);
        services.put("chat", serviceConfig("chat-v2", true, true));

        registry.refreshServiceConfig("chat");

        verify(rateLimitManager).setRateLimiter(eq(ModelServiceRegistry.ServiceType.chat), any(RateLimitConfig.class));
        verify(rateLimitManager, never())
                .setRateLimiter(eq(ModelServiceRegistry.ServiceType.embedding), any(RateLimitConfig.class));
    }

    @Test
    @DisplayName("CACHE-003: 配置里已经没有的服务会被摘出缓存，不再参与路由")
    void removedServiceIsDroppedFromCache() {
        registry.refreshFromMergedConfig();
        assertNotNull(registry.getServiceAdapter(ModelServiceRegistry.ServiceType.chat));

        services.remove("chat");

        assertFalse(registry.refreshServiceConfig("chat"), "服务已不在配置里，不应报告重建成功");
        assertNull(registry.getServiceAdapter(ModelServiceRegistry.ServiceType.chat),
                "已删除的服务不能继续留在缓存里参与路由");
        assertNotNull(registry.getServiceAdapter(ModelServiceRegistry.ServiceType.embedding),
                "删除一个服务不能殃及其它服务");
    }

    @Test
    @DisplayName("CACHE-004: 别名/大小写不同的键能解析到真实配置键")
    void aliasAndCaseInsensitiveKeysResolve() {
        services.put("img-gen", serviceConfig("img-v1", false, false));
        registry.refreshFromMergedConfig();

        services.put("img-gen", serviceConfig("img-v2", false, false));

        assertTrue(registry.refreshServiceConfig("imgGen"), "别名 imgGen 应解析到 img-gen");
        assertEquals("img-v2", registry.getServiceConfig(ModelServiceRegistry.ServiceType.imgGen).getAdapter());

        services.put("img-gen", serviceConfig("img-v3", false, false));
        assertTrue(registry.refreshServiceConfig("IMG-GEN"), "大小写差异不应导致刷新落空");
        assertEquals("img-v3", registry.getServiceConfig(ModelServiceRegistry.ServiceType.imgGen).getAdapter());
    }

    @Test
    @DisplayName("CACHE-005: 单个服务配置写坏时保留旧缓存、不向外抛异常")
    void brokenConfigKeepsPreviousCacheEntry() {
        registry.refreshFromMergedConfig();
        Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("instances", "not-a-list");
        services.put("chat", broken);

        assertFalse(registry.refreshServiceConfig("chat"), "构建失败不应该报告重建成功");
        assertEquals("chat-v1", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.chat),
                "配置写坏时保留旧缓存，且不能把「已落库成功」的写入变成对外失败");
    }

    @Test
    @DisplayName("CACHE-006: 服务配置副本同步给 ModelRouterProperties（粘性判定读它）")
    @SuppressWarnings("unchecked")
    void serviceConfigCopyIsUpdatedOnProperties() {
        registry.refreshFromMergedConfig();
        clearInvocations(properties);
        services.put("chat", serviceConfig("chat-v2", true, false));

        registry.refreshServiceConfig("chat");

        ArgumentCaptor<Map<String, ModelRouterProperties.ServiceConfig>> captor =
                ArgumentCaptor.forClass(Map.class);
        verify(properties).setServices(captor.capture());
        assertEquals("chat-v2", captor.getValue().get("chat").getAdapter());
    }

    @Test
    @DisplayName("CACHE-007: 空键集合是 no-op，不碰缓存")
    void emptyKeyCollectionIsNoOp() throws Exception {
        registry.refreshFromMergedConfig();
        ServiceRuntimeConfig chatBefore = cache().get("chat");

        assertTrue(registry.refreshServiceConfigs(List.of()).isEmpty());
        assertTrue(registry.refreshServiceConfigs(null).isEmpty());
        assertSame(chatBefore, cache().get("chat"));
    }

    private Map<String, Object> serviceConfig(final String adapter, final boolean withInstances,
                                              final boolean withRateLimit) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("adapter", adapter);
        if (withInstances) {
            Map<String, Object> instance = new LinkedHashMap<>();
            instance.put("id", "inst-" + adapter);
            instance.put("name", "model-x");
            instance.put("baseUrl", "http://" + adapter + ".local");
            config.put("instances", List.of(instance));
            Map<String, Object> loadBalance = new LinkedHashMap<>();
            loadBalance.put("type", adapter.endsWith("v2") ? "least-connections" : "round-robin");
            config.put("loadBalance", loadBalance);
        } else {
            config.put("instances", List.of());
        }
        if (withRateLimit) {
            Map<String, Object> rateLimit = new LinkedHashMap<>();
            rateLimit.put("enabled", true);
            rateLimit.put("capacity", 10L);
            rateLimit.put("rate", 5L);
            config.put("rateLimit", rateLimit);
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ServiceRuntimeConfig> cache() throws Exception {
        Field field = ModelServiceRegistry.class.getDeclaredField("serviceConfigCache");
        field.setAccessible(true);
        return new ConcurrentHashMap<>((Map<String, ServiceRuntimeConfig>) field.get(registry));
    }
}

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * #905 驼峰服务键（imgGen / imgEdit）的运行时配置可读性测试.
 *
 * <p>缺陷：缓存键用配置里的原始键（{@code imgGen}），读取侧一律用
 * {@link ServiceTypeResolver#getServiceConfigKey} 得到的 {@code img-gen} ⇒ 图像能力整体 404，
 * 模型清单里也看不到这两类服务。</p>
 */
@DisplayName("#905 驼峰服务键的缓存键口径")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistryCamelCaseServiceKeyTest {

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
            if (type == null) {
                return null;
            }
            return switch (type) {
                case chat -> "chat";
                case embedding -> "embedding";
                case rerank -> "rerank";
                case tts -> "tts";
                case stt -> "stt";
                case imgGen -> "img-gen";
                case imgEdit -> "img-edit";
            };
        });
        when(serviceTypeResolver.parseServiceType("imgGen")).thenReturn(ModelServiceRegistry.ServiceType.imgGen);
        when(serviceTypeResolver.parseServiceType("img-gen")).thenReturn(ModelServiceRegistry.ServiceType.imgGen);
        when(serviceTypeResolver.parseServiceType("imgEdit")).thenReturn(ModelServiceRegistry.ServiceType.imgEdit);
        when(serviceTypeResolver.parseServiceType("img-edit")).thenReturn(ModelServiceRegistry.ServiceType.imgEdit);
        when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);

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

        services = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("services", services);
        when(configMergeService.getPersistedConfig()).thenReturn(config);

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);
    }

    @Test
    @DisplayName("905-001: 配置用驼峰键 imgGen，全量刷新后按规范化键能读到 adapter")
    void camelCaseKeyIsReadableAfterFullRefresh() {
        services.put("imgGen", serviceConfig("sd-v1", "stable-diffusion-2-1"));
        services.put("imgEdit", serviceConfig("sd-edit-v1", "stable-diffusion-2-1"));

        registry.refreshFromMergedConfig();

        assertNotNull(registry.getServiceConfig(ModelServiceRegistry.ServiceType.imgGen),
                "配置键是 imgGen 时，按 img-gen 必须能读到运行时配置（旧实现读到 null ⇒ 图像能力 404）");
        assertEquals("sd-v1", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.imgGen));
        assertEquals("sd-edit-v1", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("905-002: 驼峰键配置下 selectInstance 能选到实例，不再抛 404")
    void selectInstanceWorksForCamelCaseKey() {
        services.put("imgGen", serviceConfig("sd-v1", "stable-diffusion-2-1"));
        registry.refreshFromMergedConfig();
        // 选择路径会过健康/熔断判据：这里只验证「配置读得到」，把实例与熔断都判为可用
        when(serviceStateManager.isInstanceHealthy(any(), any())).thenReturn(true);
        when(serviceStateManager.isServiceHealthy(any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(any(), any())).thenReturn(true);
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(true);
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(new org.unreal.modelrouter.router.loadbalancer.impl.RoundRobinLoadBalancer());

        ModelRouterProperties.ModelInstance instance = registry.selectInstance(
                ModelServiceRegistry.ServiceType.imgGen, "stable-diffusion-2-1", null, null);

        assertNotNull(instance, "应能选到 stable-diffusion-2-1 实例");
        assertEquals("stable-diffusion-2-1", instance.getName());
    }

    @Test
    @DisplayName("905-003: 增量刷新写入的也是规范化键，读侧立刻可见")
    void incrementalRefreshUsesCanonicalKey() {
        services.put("imgGen", serviceConfig("sd-v1", "stable-diffusion-2-1"));
        services.put("chat", serviceConfig("chat-v1", "model-x"));
        registry.refreshFromMergedConfig();

        services.put("imgGen", serviceConfig("sd-v2", "stable-diffusion-2-1"));
        registry.refreshServiceConfig("imgGen");

        assertEquals("sd-v2", registry.getServiceAdapter(ModelServiceRegistry.ServiceType.imgGen),
                "驼峰键的增量刷新必须写进规范化键，否则读侧仍然拿到旧值/空值");
    }

    private Map<String, Object> serviceConfig(final String adapter, final String instanceName) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("adapter", adapter);
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("id", "inst-" + adapter);
        instance.put("name", instanceName);
        instance.put("baseUrl", "http://" + adapter + ".local");
        config.put("instances", List.of(instance));
        return config;
    }
}

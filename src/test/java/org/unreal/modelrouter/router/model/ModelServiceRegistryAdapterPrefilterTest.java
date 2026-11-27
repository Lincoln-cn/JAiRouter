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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.rule.RuleEngineService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #1238：选路前的 adapter 可解析性前置过滤.
 *
 * <p>修复前，实例引用了未注册的 adapter 时路由照常选中它，错误要拖到转发阶段
 * {@code AdapterRegistry#getAdapter(serviceType, instance)} 才抛（客户端看到 5xx）；
 * 健康检查只探网络可达性，一直报 HEALTHY，运维只能翻日志。</p>
 *
 * <p>修复后：不可解析的实例在选路阶段被跳过并打 WARN + 计数；全部候选都不可解析时
 * 抛 503 且消息里点明实例名与 adapter 名（避免落到「all instances busy or rate limited」这种误导文案）。</p>
 *
 * @author JAiRouter Team
 * @since v3.9.1
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("#1238 选路前剔除 adapter 未注册的实例")
class ModelServiceRegistryAdapterPrefilterTest {

    private static final String SERVICE_ADAPTER = "normal";
    private static final String UNREGISTERED_ADAPTER = "no-such-adapter";

    @Mock
    private ModelRouterProperties properties;
    @Mock
    private ServiceStateManager serviceStateManager;
    @Mock
    private RateLimitManager rateLimitManager;
    @Mock
    private LoadBalancerManager loadBalancerManager;
    @Mock
    private CircuitBreakerManager circuitBreakerManager;
    @Mock
    private FallbackManager fallbackManager;
    @Mock
    private ConfigMergeService configMergeService;
    @Mock
    private ServiceTypeResolver serviceTypeResolver;
    @Mock
    private ConfigConverterHelper configConverterHelper;
    @Mock
    private WebClientCacheManager webClientCacheManager;
    @Mock
    private RoutingMonitorService routingMonitorService;
    @Mock
    private AdapterRegistry adapterRegistry;
    @Mock
    private ObjectProvider<AdapterRegistry> adapterRegistryProvider;
    @Mock
    private LoadBalancer loadBalancer;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Counter counter;

    private Map<String, Object> services;
    private ModelServiceRegistry registry;

    @BeforeEach
    void setUp() {
        when(serviceTypeResolver.getServiceConfigKey(any())).thenAnswer(inv -> {
            ModelServiceRegistry.ServiceType type = inv.getArgument(0);
            return type == null ? null : type.name();
        });
        when(serviceTypeResolver.parseServiceType(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return "chat".equalsIgnoreCase(key) ? ModelServiceRegistry.ServiceType.chat : null;
        });

        when(configConverterHelper.convertMapToInstance(any())).thenAnswer(inv -> {
            Map<String, Object> map = inv.getArgument(0);
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setInstanceId((String) map.get("id"));
            instance.setId((String) map.get("id"));
            instance.setName((String) map.get("name"));
            instance.setBaseUrl((String) map.get("baseUrl"));
            instance.setAdapter((String) map.get("adapter"));
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

        // 选路的可用性判据全部放行，测试只观察 adapter 维度
        when(serviceStateManager.isInstanceHealthy(any(), any())).thenReturn(true);
        when(serviceStateManager.isServiceHealthy(any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(any(), any())).thenReturn(true);
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(true);
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(loadBalancer);
        when(loadBalancerManager.getLoadBalancerByStrategy(any())).thenReturn(loadBalancer);
        // 返回候选列表的第一个：候选集被裁剪后，选中的必然只剩可解析实例
        when(loadBalancer.selectInstance(anyList(), any())).thenAnswer(inv -> firstOf(inv.getArgument(0)));
        when(loadBalancer.selectInstance(anyList(), any(), any()))
                .thenAnswer(inv -> firstOf(inv.getArgument(0)));

        // 解析口径：实例级 adapter 优先，否则服务级；注册表只认 normal / claude
        when(adapterRegistry.resolveAdapterName(any(), any())).thenAnswer(inv -> {
            ModelRouterProperties.ModelInstance instance = inv.getArgument(1);
            if (instance != null && instance.getAdapter() != null && !instance.getAdapter().isBlank()) {
                return instance.getAdapter();
            }
            return SERVICE_ADAPTER;
        });
        when(adapterRegistry.isAdapterSupported(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return SERVICE_ADAPTER.equals(name) || "claude".equals(name);
        });
        when(adapterRegistryProvider.getIfAvailable()).thenReturn(adapterRegistry);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        registry = newRegistry();
        registry.setAdapterRegistryProvider(adapterRegistryProvider);
        registry.setMeterRegistry(meterRegistry);
    }

    private static ModelRouterProperties.ModelInstance firstOf(final List<?> candidates) {
        return candidates.isEmpty() ? null : (ModelRouterProperties.ModelInstance) candidates.get(0);
    }

    private ModelServiceRegistry newRegistry() {
        return new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);
    }

    /**
     * 组装一个服务的配置.
     *
     * @param serviceAdapter 服务级 adapter
     * @param instances      实例配置（形如 {@code Map.of("id","i1","name","m1","adapter","x")}）
     * @return 可直接放进 services 的服务配置
     */
    private Map<String, Object> chatService(final String serviceAdapter,
                                            final List<Map<String, Object>> instances) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("adapter", serviceAdapter);
        config.put("instances", new ArrayList<>(instances));
        return config;
    }

    /**
     * 组装一个实例配置.
     *
     * @param id      实例 id
     * @param name    实例/模型名
     * @param adapter 实例级 adapter，{@code null} 表示回退到服务级
     * @return 实例配置
     */
    private Map<String, Object> instance(final String id, final String name, final String adapter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("baseUrl", "http://" + id + ".local");
        map.put("adapter", adapter);
        return map;
    }

    @Test
    @DisplayName("1238-001: 实例级 adapter 未注册时该实例被跳过，仍能选到可用的那个")
    void skipsInstanceWithUnregisteredAdapter() {
        // 两个实例同名（否则 i-bad 会先被「模型名过滤」剔掉，测不到 adapter 维度），
        // 且坏实例排在前面：过滤若失效，负载均衡按候选顺序选到的就是 i-bad
        services.put("chat", chatService(SERVICE_ADAPTER, List.of(
                instance("i-bad", "model-a", UNREGISTERED_ADAPTER),
                instance("i-good", "model-a", null))));
        registry.refreshFromMergedConfig();

        ModelRouterProperties.ModelInstance selected = registry.selectInstance(
                ModelServiceRegistry.ServiceType.chat, "model-a", null, null);

        assertNotNull(selected, "存在可解析实例时必须选到它，而不是抛异常");
        assertEquals("i-good", selected.getId(), "必须留下 adapter 可解析的那个实例");

        // 核心断言：交给负载均衡的候选集里已经没有 i-bad
        ArgumentCaptor<List<ModelRouterProperties.ModelInstance>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(loadBalancer).selectInstance(captor.capture(), any(), any());
        List<ModelRouterProperties.ModelInstance> fed = captor.getValue();
        assertEquals(1, fed.size(), "未注册 adapter 的实例必须在进入负载均衡前就被剔除");
        assertEquals("i-good", fed.get(0).getId());
    }

    @Test
    @DisplayName("1238-002: 全部实例的 adapter 都未注册时抛 503，且消息点明实例与 adapter")
    void throwsServiceUnavailableWhenEveryInstanceIsUnresolvable() {
        services.put("chat", chatService(SERVICE_ADAPTER, List.of(
                instance("i-bad", "model-a", UNREGISTERED_ADAPTER))));
        registry.refreshFromMergedConfig();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "model-a", null, null));

        assertEquals(503, ex.getStatusCode().value(),
                "配置错误导致的「无可路由实例」应回 503（可重试语义），而不是让请求走到转发阶段变 5xx");
        String message = String.valueOf(ex.getReason());
        assertTrue(message.contains(UNREGISTERED_ADAPTER), "消息里要出现未注册的 adapter 名: " + message);
        assertTrue(message.contains("model-a"), "消息里要出现实例名，运维才能定位: " + message);
        assertTrue(message.contains("/api/config/adapter/list"), "消息里要给出核对清单的出口: " + message);
    }

    @Test
    @DisplayName("1238-003: adapter 全部可解析时行为与修复前一致（回归）")
    void selectsNormallyWhenAdaptersAreRegistered() {
        services.put("chat", chatService(SERVICE_ADAPTER, List.of(
                instance("i1", "model-a", null),
                instance("i2", "model-a", "claude"))));
        registry.refreshFromMergedConfig();

        ModelRouterProperties.ModelInstance selected = registry.selectInstance(
                ModelServiceRegistry.ServiceType.chat, "model-a", null, null);

        assertNotNull(selected);
        assertEquals("model-a", selected.getName());
    }

    @Test
    @DisplayName("1238-004: 未注入 adapter 注册表时不过滤（保持旧行为，不因可选依赖缺失而报错）")
    void skipsFilterWhenRegistryIsAbsent() {
        services.put("chat", chatService(SERVICE_ADAPTER, List.of(
                instance("i-bad", "model-a", UNREGISTERED_ADAPTER))));
        registry.refreshFromMergedConfig();

        ModelServiceRegistry withoutAdapter = newRegistry();
        withoutAdapter.refreshFromMergedConfig();
        ModelRouterProperties.ModelInstance selected = withoutAdapter.selectInstance(
                ModelServiceRegistry.ServiceType.chat, "model-a", null, null);

        assertNotNull(selected, "拿不到注册表时按旧行为放行，不能反过来把请求全拦掉");
        assertEquals("model-a", selected.getName());
    }

    @Test
    @DisplayName("1238-005: 跳过时打点（运维侧能看到不可解析的 adapter 与次数）")
    void recordsCounterForUnresolvableAdapter() {
        services.put("chat", chatService(SERVICE_ADAPTER, List.of(
                instance("i-good", "model-a", null),
                instance("i-bad", "model-a", UNREGISTERED_ADAPTER))));
        registry.refreshFromMergedConfig();

        registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "model-a", null, null);

        verify(meterRegistry).counter(eq("jairouter.routing.adapter.unresolvable"),
                eq("service"), eq("chat"), eq("adapter"), eq(UNREGISTERED_ADAPTER));
        verify(counter).increment();
    }
}

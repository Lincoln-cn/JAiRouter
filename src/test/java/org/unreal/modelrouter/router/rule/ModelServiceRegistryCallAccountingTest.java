package org.unreal.modelrouter.router.rule;

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
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ServiceRuntimeConfig;
import org.unreal.modelrouter.router.model.WebClientCacheManager;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #688 在途计数记账对象一致性测试（池/规则策略 LB 的情形）。
 *
 * <p>缺陷：选择实例时 `recordCall` 打在「规则/池策略 LB」上，而 `recordCallComplete` 按
 * {@code serviceType} 另取一个 LB 减计数 ⇒ 加与减落在不同对象，策略 LB 的在途数只增不减，
 * {@code least-connections} 退化成「历史累计调用最少者优先」。</p>
 *
 * <p>修复后：完成/失败回调走选择时实际记账的那个 LB。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#688 在途计数记账对象一致性")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistryCallAccountingTest {

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

    /** 按服务类型取到的普通 LB。 */
    @Mock private LoadBalancer serviceTypeLoadBalancer;

    /** 规则指定策略时用到的策略 LB（记账落点）。 */
    @Mock private LoadBalancer strategyLoadBalancer;

    private ModelServiceRegistry registry;
    private RuleEngineService ruleEngine;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() throws Exception {
        when(serviceTypeResolver.getServiceConfigKey(any())).thenReturn("chat");
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(serviceTypeLoadBalancer);
        when(loadBalancerManager.getLoadBalancerByStrategy("least-connections")).thenReturn(strategyLoadBalancer);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(any(), any())).thenReturn(true);
        when(strategyLoadBalancer.selectInstance(anyList(), any(), any())).thenAnswer(inv -> {
            List<ModelRouterProperties.ModelInstance> instances = inv.getArgument(0);
            return instances.get(0);
        });
        when(serviceTypeLoadBalancer.selectInstance(anyList(), any(), any())).thenAnswer(inv -> {
            List<ModelRouterProperties.ModelInstance> instances = inv.getArgument(0);
            return instances.get(0);
        });
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(true);
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-gpt");
        instance.setId("inst-gpt");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://gpt.local");
        instance.setStatus("active");
        instance.setTags(Map.of("gpu_type", "a100"));

        ServiceRuntimeConfig runtimeConfig = new ServiceRuntimeConfig();
        runtimeConfig.setInstances(List.of(instance));
        Field cacheField = ModelServiceRegistry.class.getDeclaredField("serviceConfigCache");
        cacheField.setAccessible(true);
        cacheField.set(registry, new java.util.concurrent.ConcurrentHashMap<>(Map.of("chat", runtimeConfig)));

        ruleEngine = new RuleEngineService();
        registry.setRuleEngine(ruleEngine);
    }

    @Test
    @DisplayName("ACC-001: 规则指定 lb-strategy 时，完成回调落在策略 LB 上（不是按服务类型取的那个）")
    void completionGoesToTheStrategyLoadBalancer() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId("r-lb");
        rule.setName("r-lb");
        rule.setPriority(10);
        rule.setEnabled(true);
        rule.setConditions(List.of(new RuleDefinition.Condition(
                RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4")));
        RuleDefinition.Action action = new RuleDefinition.Action(RuleDefinition.ActionType.LB_STRATEGY, null);
        action.setLbStrategy("least-connections");
        rule.setAction(action);
        ruleEngine.reloadRules(List.of(rule));

        ModelRouterProperties.ModelInstance selected =
                registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

        assertNotNull(selected);
        assertEquals(1, registry.getOutstandingCalls(ModelServiceRegistry.ServiceType.chat, selected),
                "选择后必须记下 1 个在途调用");
        verify(strategyLoadBalancer).recordCall(selected);

        registry.recordCallComplete(ModelServiceRegistry.ServiceType.chat, selected, 120L, true);

        verify(strategyLoadBalancer).recordCallComplete(selected, 120L, true);
        verify(serviceTypeLoadBalancer, never()).recordCallComplete(any(), anyLong(), anyBoolean());
        assertEquals(0, registry.getOutstandingCalls(ModelServiceRegistry.ServiceType.chat, selected),
                "完成回调后不得残留未完成调用");
    }

    @Test
    @DisplayName("ACC-002: 失败回调同样落在策略 LB 上，且在途数不残留")
    void failureAlsoGoesToTheStrategyLoadBalancer() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId("r-lb");
        rule.setName("r-lb");
        rule.setPriority(10);
        rule.setEnabled(true);
        rule.setConditions(List.of(new RuleDefinition.Condition(
                RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4")));
        RuleDefinition.Action action = new RuleDefinition.Action(RuleDefinition.ActionType.LB_STRATEGY, null);
        action.setLbStrategy("least-connections");
        rule.setAction(action);
        ruleEngine.reloadRules(List.of(rule));

        ModelRouterProperties.ModelInstance selected =
                registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");
        assertNotNull(selected);

        registry.recordCallFailure(ModelServiceRegistry.ServiceType.chat, selected, 300L, "DOWNSTREAM_ERROR");

        verify(strategyLoadBalancer).recordCallFailure(selected, 300L, "DOWNSTREAM_ERROR");
        assertEquals(0, registry.getOutstandingCalls(ModelServiceRegistry.ServiceType.chat, selected));
    }

    @Test
    @DisplayName("ACC-003: 没有记账记录时退回按服务类型取 LB（保持修复前行为）")
    void fallsBackToServiceTypeLoadBalancerWhenNothingWasTracked() {
        ModelRouterProperties.ModelInstance unselected = new ModelRouterProperties.ModelInstance();
        unselected.setInstanceId("inst-never-selected");
        unselected.setBaseUrl("http://never.local");

        registry.recordCallComplete(ModelServiceRegistry.ServiceType.chat, unselected);

        verify(serviceTypeLoadBalancer).recordCallComplete(unselected);
        assertEquals(0, registry.getOutstandingCalls(ModelServiceRegistry.ServiceType.chat, unselected));
    }
}

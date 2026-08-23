package org.unreal.modelrouter.router.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ModelServiceRegistry 与规则引擎集成测试
 * 验证:兼容金路径(无规则/无匹配走原逻辑)、规则重写 modelName、目标实例锁定、LB 覆盖
 */
@DisplayName("ModelServiceRegistry 规则引擎集成测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistryRuleIntegrationTest {

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
    @Mock private LoadBalancer loadBalancer;

    private ModelServiceRegistry registry;
    private RuleEngineService ruleEngine;

    @BeforeEach
    void setUp() throws Exception {
        when(serviceTypeResolver.getServiceConfigKey(any())).thenReturn("chat");
        when(loadBalancerManager.getLoadBalancer(any())).thenReturn(loadBalancer);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(any(), any())).thenReturn(true);
        when(loadBalancer.selectInstance(anyList(), any(), any())).thenAnswer(inv -> {
            List<ModelRouterProperties.ModelInstance> instances = inv.getArgument(0);
            return instances.get(0);
        });
        when(rateLimitManager.tryAcquireInstance(any())).thenReturn(true);

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);

        // 注入 serviceConfigCache(chat 服务, 2 个实例:gpt-4-a / claude-3-a)
        ServiceRuntimeConfig runtimeConfig = new ServiceRuntimeConfig();
        runtimeConfig.setInstances(List.of(
                instance("inst-gpt", "gpt-4", "http://gpt.local"),
                instance("inst-claude", "claude-3", "http://claude.local")));
        Field cacheField = ModelServiceRegistry.class.getDeclaredField("serviceConfigCache");
        cacheField.setAccessible(true);
        cacheField.set(registry, new java.util.concurrent.ConcurrentHashMap<>(Map.of("chat", runtimeConfig)));

        ruleEngine = new RuleEngineService();
        registry.setRuleEngine(ruleEngine);
    }

    private ModelRouterProperties.ModelInstance instance(final String id, final String name, final String baseUrl) {
        ModelRouterProperties.ModelInstance i = new ModelRouterProperties.ModelInstance();
        i.setInstanceId(id);
        i.setName(name);
        i.setBaseUrl(baseUrl);
        i.setStatus("active");
        return i;
    }

    private RuleDefinition rule(final String id, final int priority,
                                final RuleDefinition.Condition condition,
                                final RuleDefinition.Action action) {
        RuleDefinition r = new RuleDefinition();
        r.setId(id);
        r.setName(id);
        r.setPriority(priority);
        r.setEnabled(true);
        if (condition != null) {
            r.setConditions(List.of(condition));
        }
        r.setAction(action);
        return r;
    }

    private RuleDefinition.Condition cond(final RuleDefinition.ConditionType type,
                                          final RuleDefinition.Operator op, final String value) {
        RuleDefinition.Condition c = new RuleDefinition.Condition(type, op, value);
        return c;
    }

    // ==================== 兼容金路径 ====================

    @Nested
    @DisplayName("兼容金路径")
    class CompatibilityTests {

        @Test
        @DisplayName("INTEG-001: 无规则引擎时走原逻辑")
        void testNoRuleEngine_originalBehavior() {
            registry.setRuleEngine(null);

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId());
        }

        @Test
        @DisplayName("INTEG-002: 规则引擎为空规则列表时走原逻辑")
        void testEmptyRules_originalBehavior() {
            ruleEngine.reloadRules(List.of());

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId());
        }

        @Test
        @DisplayName("INTEG-003: 规则不匹配时走原逻辑")
        void testNoMatch_originalBehavior() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "nonexistent"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId());
        }

        @Test
        @DisplayName("INTEG-004: headers=null 时 HEADER 条件不匹配(内部调用路径)")
        void testNullHeaders_headerConditionNoMatch() {
            RuleDefinition.Condition c = cond(RuleDefinition.ConditionType.HEADER,
                    RuleDefinition.Operator.EQUALS, "vllm");
            c.setField("x-routing");
            RuleDefinition r = rule("r1", 10, c,
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            ruleEngine.reloadRules(List.of(r));

            // 3 参方法(headers=null)→ HEADER 条件不可达 → 原逻辑
            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId());
        }
    }

    // ==================== 规则重写 modelName ====================

    @Nested
    @DisplayName("规则重写 modelName")
    class TargetModelTests {

        @Test
        @DisplayName("INTEG-005: 规则重写 modelName 后按新模型名过滤实例")
        void testTargetModel_rewritesModelName() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-claude", selected.getInstanceId(),
                    "重写模型名后应路由到 claude-3 实例");
        }
    }

    // ==================== 目标实例锁定 ====================

    @Nested
    @DisplayName("目标实例锁定")
    class TargetInstanceTests {

        @Test
        @DisplayName("INTEG-006: 规则锁定目标实例")
        void testTargetInstance_locksInstance() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_INSTANCE, "inst-claude"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-claude", selected.getInstanceId(),
                    "锁定实例后应只从目标实例选择");
        }

        @Test
        @DisplayName("INTEG-007: 目标实例不存在抛 404")
        void testTargetInstance_missing_throws404() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_INSTANCE, "nonexistent"));
            ruleEngine.reloadRules(List.of(r));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    // ==================== LB 策略覆盖 ====================

    @Nested
    @DisplayName("LB 策略覆盖")
    class LbStrategyTests {

        @Test
        @DisplayName("INTEG-008: 规则指定 LB 策略时覆盖")
        void testLbStrategy_override() {
            LoadBalancer ruleLb = mock(LoadBalancer.class);
            when(ruleLb.selectInstance(anyList(), any(), any())).thenAnswer(inv -> {
                List<ModelRouterProperties.ModelInstance> instances = inv.getArgument(0);
                return instances.get(instances.size() - 1);
            });
            when(loadBalancerManager.getLoadBalancerByStrategy("round-robin")).thenReturn(ruleLb);

            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.LB_STRATEGY, "round-robin"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            verify(loadBalancerManager).getLoadBalancerByStrategy("round-robin");
        }

        @Test
        @DisplayName("INTEG-009: 未知 LB 策略降级为默认")
        void testLbStrategy_unknown_fallsBack() {
            when(loadBalancerManager.getLoadBalancerByStrategy("unknown")).thenReturn(null);

            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.LB_STRATEGY, "unknown"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected, "未知策略应降级为默认 LB");
        }
    }

    // ==================== TARGET_ADAPTER 解析 ====================

    @Nested
    @DisplayName("TARGET_ADAPTER 适配器解析")
    class TargetAdapterTests {

        @Test
        @DisplayName("INTEG-010: 规则指定适配器时 resolveRuleAdapterName 返回该名")
        void testResolveRuleAdapterName_hit() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_ADAPTER, "openai-compat"));
            ruleEngine.reloadRules(List.of(r));

            String adapterName = registry.resolveRuleAdapterName(
                    ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null);

            assertEquals("openai-compat", adapterName);
        }

        @Test
        @DisplayName("INTEG-011: 无匹配规则时 resolveRuleAdapterName 返回 null")
        void testResolveRuleAdapterName_noMatch_returnsNull() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_ADAPTER, "openai-compat"));
            ruleEngine.reloadRules(List.of(r));

            assertNull(registry.resolveRuleAdapterName(
                    ModelServiceRegistry.ServiceType.chat, "claude-3", "1.1.1.1", null));
        }

        @Test
        @DisplayName("INTEG-012: 空规则引擎时 resolveRuleAdapterName 返回 null")
        void testResolveRuleAdapterName_noEngine_returnsNull() {
            registry.setRuleEngine(null);

            assertNull(registry.resolveRuleAdapterName(
                    ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
        }

        @Test
        @DisplayName("INTEG-013: TARGET_ADAPTER 规则不影响 selectInstance 实例选择")
        void testTargetAdapter_doesNotAffectInstanceSelection() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_ADAPTER, "openai-compat"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId(), "适配器切换不应改变实例选择");
        }
    }
}

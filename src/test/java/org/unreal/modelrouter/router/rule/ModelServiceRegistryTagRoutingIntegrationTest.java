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
 * ModelServiceRegistry 标签路由集成测试(v2.9.7)
 * 验证:规则 TARGET_TAGS 圈选 / 请求 header X-JAiRouter-Tags 圈选 /
 * 无 tags 走原逻辑 / 过滤后空候选 404 / TARGET_INSTANCE 与 TARGET_TAGS 单一动作互斥
 */
@DisplayName("ModelServiceRegistry 标签路由集成测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceRegistryTagRoutingIntegrationTest {

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
        when(rateLimitManager.tryAcquire(any())).thenReturn(true);

        registry = new ModelServiceRegistry(properties, serviceStateManager, rateLimitManager,
                loadBalancerManager, circuitBreakerManager, fallbackManager, configMergeService,
                serviceTypeResolver, configConverterHelper, webClientCacheManager, routingMonitorService);

        // 注入 serviceConfigCache(chat 服务, 2 个实例:gpt-4-a(gpu_type=a100,region=cn-north) / claude-3-a(region=us-west))
        ServiceRuntimeConfig runtimeConfig = new ServiceRuntimeConfig();
        runtimeConfig.setInstances(List.of(
                instance("inst-gpt", "gpt-4", "http://gpt.local",
                        Map.of("gpu_type", "a100", "region", "cn-north")),
                instance("inst-claude", "claude-3", "http://claude.local",
                        Map.of("region", "us-west"))));
        Field cacheField = ModelServiceRegistry.class.getDeclaredField("serviceConfigCache");
        cacheField.setAccessible(true);
        cacheField.set(registry, new java.util.concurrent.ConcurrentHashMap<>(Map.of("chat", runtimeConfig)));

        ruleEngine = new RuleEngineService();
        registry.setRuleEngine(ruleEngine);
    }

    private ModelRouterProperties.ModelInstance instance(final String id, final String name, final String baseUrl,
                                                         final Map<String, String> tags) {
        ModelRouterProperties.ModelInstance i = new ModelRouterProperties.ModelInstance();
        i.setInstanceId(id);
        i.setName(name);
        i.setBaseUrl(baseUrl);
        i.setStatus("active");
        i.setTags(tags);
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
        return new RuleDefinition.Condition(type, op, value);
    }

    // ==================== 规则 TARGET_TAGS 圈选 ====================

    @Nested
    @DisplayName("规则 TARGET_TAGS 圈选")
    class RuleTargetTagsTests {

        @Test
        @DisplayName("TAG-INTEG-001: 规则 TARGET_TAGS 按标签圈选实例")
        void testRuleTargetTags_filtersInstances() {
            RuleDefinition r = rule("r-tags", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_TAGS, null));
            r.getAction().setTags(Map.of("gpu_type", "a100"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId(), "TARGET_TAGS 应圈选 gpu_type=a100 的实例");
        }

        @Test
        @DisplayName("TAG-INTEG-002: 规则 TARGET_TAGS 多键 AND 圈选")
        void testRuleTargetTags_multiKeyAnd() {
            RuleDefinition r = rule("r-tags", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_TAGS, null));
            r.getAction().setTags(Map.of("gpu_type", "a100", "region", "cn-north"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId(),
                    "AND 语义:同时满足 gpu_type=a100 与 region=cn-north 才命中");
        }

        @Test
        @DisplayName("TAG-INTEG-003: 规则 TARGET_TAGS 无匹配实例抛 404")
        void testRuleTargetTags_noMatch_throws404() {
            RuleDefinition r = rule("r-tags", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_TAGS, null));
            r.getAction().setTags(Map.of("gpu_type", "h100"));
            ruleEngine.reloadRules(List.of(r));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    // ==================== 请求 header 圈选 ====================

    @Nested
    @DisplayName("请求 header X-JAiRouter-Tags 圈选")
    class HeaderTagsTests {

        @Test
        @DisplayName("TAG-INTEG-004: header 单键圈选实例")
        void testHeaderTags_filtersInstances() {
            Map<String, String> headers = Map.of("X-JAiRouter-Tags", "gpu_type=a100");

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", headers);

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId(), "header 应圈选 gpu_type=a100 的实例");
        }

        @Test
        @DisplayName("TAG-INTEG-005: header 多标签、容忍空格与空项")
        void testHeaderTags_multipleWithSpaces() {
            Map<String, String> headers = Map.of("X-JAiRouter-Tags", "gpu_type=a100, region=cn-north ,,");

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", headers);

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId(), "多标签 AND + 容忍空格/空项");
        }

        @Test
        @DisplayName("TAG-INTEG-006: header 标签无匹配实例抛 404")
        void testHeaderTags_noMatch_throws404() {
            Map<String, String> headers = Map.of("X-JAiRouter-Tags", "region=eu-central");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", headers));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("TAG-INTEG-007: header 为空白时不过滤")
        void testHeaderTags_blank_originalBehavior() {
            Map<String, String> headers = Map.of("X-JAiRouter-Tags", "   ");

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", headers);

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId(), "空 header 应走原逻辑");
        }
    }

    // ==================== 无 tags 走原逻辑 ====================

    @Nested
    @DisplayName("无 tags 走原逻辑")
    class NoTagsTests {

        @Test
        @DisplayName("TAG-INTEG-008: 规则非 TARGET_TAGS 且无 header 时走原逻辑")
        void testNoTags_originalBehavior() {
            RuleDefinition r = rule("r1", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            ruleEngine.reloadRules(List.of(r));

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-claude", selected.getInstanceId(),
                    "无 tags 要求时行为与现状完全一致(TARGET_MODEL 重写后按模型名过滤)");
        }
    }

    // ==================== 触发来源优先级 ====================

    @Nested
    @DisplayName("触发来源优先级与动作互斥")
    class PriorityTests {

        @Test
        @DisplayName("TAG-INTEG-009: 规则 TARGET_TAGS 优先于请求 header")
        void testRuleTags_priorityOverHeader() {
            RuleDefinition r = rule("r-tags", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_TAGS, null));
            r.getAction().setTags(Map.of("region", "cn-north"));
            ruleEngine.reloadRules(List.of(r));

            // header 指向 inst-claude(region=us-west),但规则要求 region=cn-north → 应选 inst-gpt
            Map<String, String> headers = Map.of("X-JAiRouter-Tags", "region=us-west");

            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", headers);

            assertNotNull(selected);
            assertEquals("inst-gpt", selected.getInstanceId(), "规则 TARGET_TAGS 应优先于 header");
        }

        @Test
        @DisplayName("TAG-INTEG-010: TARGET_INSTANCE 动作不产生 tags 要求(单一动作互斥)")
        void testTargetInstance_noTagsFromRule() {
            RuleDefinition r = rule("r-lock", 10,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_INSTANCE, "inst-claude"));
            ruleEngine.reloadRules(List.of(r));

            // 无 header;TARGET_INSTANCE 动作下 decision.getTargetTags() 为 null → 无 tags 过滤
            ModelRouterProperties.ModelInstance selected =
                    registry.selectInstance(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1");

            assertNotNull(selected);
            assertEquals("inst-claude", selected.getInstanceId(),
                    "TARGET_INSTANCE 锁定应生效,不因实例标签产生 tags 过滤");
        }
    }
}

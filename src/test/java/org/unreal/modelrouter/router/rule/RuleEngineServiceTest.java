package org.unreal.modelrouter.router.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleEngineService 规则引擎测试
 * TDD 测试先行:条件匹配(5类×操作符)、优先级、启停、首条命中
 */
@DisplayName("RuleEngineService 规则引擎测试")
class RuleEngineServiceTest {

    private RuleEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new RuleEngineService();
    }

    private RuleDefinition rule(final String id, final int priority, final boolean enabled,
                                final RuleDefinition.Condition condition,
                                final RuleDefinition.Action action) {
        RuleDefinition r = new RuleDefinition();
        r.setId(id);
        r.setName(id);
        r.setPriority(priority);
        r.setEnabled(enabled);
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

    private RuleDefinition.Action action(final RuleDefinition.ActionType type, final String target) {
        return new RuleDefinition.Action(type, target);
    }

    // ==================== MODEL_NAME 条件匹配 ====================

    @Nested
    @DisplayName("MODEL_NAME 条件匹配")
    class ModelNameMatchTests {

        @Test
        @DisplayName("RULE-001: MODEL_NAME EQUALS 命中")
        void testModelNameEquals_hit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            RuleDecision d = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null);
            assertNotNull(d, "modelName 精确匹配应命中");
            assertEquals("claude-3", d.getTargetModelName());
        }

        @Test
        @DisplayName("RULE-002: MODEL_NAME EQUALS 不命中(大小写不敏感)")
        void testModelNameEquals_caseInsensitive() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "GPT-4"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            RuleDecision d = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null);
            assertNotNull(d);
        }

        @Test
        @DisplayName("RULE-003: MODEL_NAME CONTAINS 命中")
        void testModelNameContains_hit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.CONTAINS, "gpt"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4-turbo", "1.1.1.1", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "claude-3", "1.1.1.1", null));
        }

        @Test
        @DisplayName("RULE-004: MODEL_NAME STARTS_WITH 命中")
        void testModelNameStartsWith_hit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.STARTS_WITH, "llama"),
                    action(RuleDefinition.ActionType.TARGET_INSTANCE, "local-1"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "llama-3-70b", "1.1.1.1", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
        }

        @Test
        @DisplayName("RULE-005: MODEL_NAME REGEX 命中")
        void testModelNameRegex_hit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.REGEX, "^gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4o", "1.1.1.1", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "claude-3", "1.1.1.1", null));
        }

        @Test
        @DisplayName("RULE-006: 非法正则不抛异常,视为不命中")
        void testModelNameInvalidRegex_noThrow() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.REGEX, "[invalid"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
        }
    }

    // ==================== HEADER 条件匹配 ====================

    @Nested
    @DisplayName("HEADER 条件匹配")
    class HeaderMatchTests {

        @Test
        @DisplayName("RULE-007: HEADER EQUALS 命中")
        void testHeaderEquals_hit() {
            RuleDefinition.Condition c = cond(RuleDefinition.ConditionType.HEADER,
                    RuleDefinition.Operator.EQUALS, "vllm");
            c.setField("x-routing");
            RuleDefinition r = rule("r1", 1, true, c,
                    action(RuleDefinition.ActionType.TARGET_ADAPTER, "vllm-adapter"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4",
                    "1.1.1.1", Map.of("x-routing", "vllm")));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4",
                    "1.1.1.1", Map.of("x-routing", "other")));
        }

        @Test
        @DisplayName("RULE-008: HEADER 大小写不敏感")
        void testHeaderCaseInsensitive() {
            RuleDefinition.Condition c = cond(RuleDefinition.ConditionType.HEADER,
                    RuleDefinition.Operator.EQUALS, "vllm");
            c.setField("x-routing");
            RuleDefinition r = rule("r1", 1, true, c,
                    action(RuleDefinition.ActionType.TARGET_ADAPTER, "vllm-adapter"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4",
                    "1.1.1.1", Map.of("X-Routing", "VLLM")));
        }

        @Test
        @DisplayName("RULE-009: HEADER 缺失时不命中")
        void testHeaderMissing_noHit() {
            RuleDefinition.Condition c = cond(RuleDefinition.ConditionType.HEADER,
                    RuleDefinition.Operator.EQUALS, "vllm");
            c.setField("x-routing");
            RuleDefinition r = rule("r1", 1, true, c,
                    action(RuleDefinition.ActionType.TARGET_ADAPTER, "vllm-adapter"));
            engine.reloadRules(List.of(r));

            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", Map.of()));
        }
    }

    // ==================== CLIENT_IP 条件匹配 ====================

    @Nested
    @DisplayName("CLIENT_IP 条件匹配")
    class ClientIpMatchTests {

        @Test
        @DisplayName("RULE-010: CLIENT_IP EQUALS 命中")
        void testClientIpEquals_hit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.CLIENT_IP, RuleDefinition.Operator.EQUALS, "10.0.0.5"),
                    action(RuleDefinition.ActionType.TARGET_INSTANCE, "internal-1"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.0.5", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.0.6", null));
        }

        @Test
        @DisplayName("RULE-011: CLIENT_IP CIDR 命中")
        void testClientIpCidr_hit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.CLIENT_IP, RuleDefinition.Operator.CIDR_MATCH, "10.0.0.0/24"),
                    action(RuleDefinition.ActionType.TARGET_INSTANCE, "internal-1"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.0.123", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.1.123", null));
        }

        @Test
        @DisplayName("RULE-012: CLIENT_IP 非法 CIDR 不命中")
        void testClientIpInvalidCidr_noHit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.CLIENT_IP, RuleDefinition.Operator.CIDR_MATCH, "invalid"),
                    action(RuleDefinition.ActionType.TARGET_INSTANCE, "internal-1"));
            engine.reloadRules(List.of(r));

            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.0.123", null));
        }
    }

    // ==================== SERVICE_TYPE / WEIGHT 条件 ====================

    @Nested
    @DisplayName("SERVICE_TYPE 与 WEIGHT 条件")
    class ServiceTypeAndWeightTests {

        @Test
        @DisplayName("RULE-013: SERVICE_TYPE EQUALS 命中")
        void testServiceTypeEquals_hit() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.SERVICE_TYPE, RuleDefinition.Operator.EQUALS, "embedding"),
                    action(RuleDefinition.ActionType.TARGET_ADAPTER, "embed-adapter"));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.embedding, "bge", "1.1.1.1", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
        }

        @Test
        @DisplayName("RULE-014: WEIGHT 条件稳定命中(同一请求一致)")
        void testWeight_stable() {
            RuleDefinition.Condition c = cond(RuleDefinition.ConditionType.WEIGHT,
                    RuleDefinition.Operator.EQUALS, "50");
            c.setWeight(50);
            RuleDefinition r = rule("r1", 1, true, c,
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            // 同一 (clientIp, modelName) 结果必须稳定
            RuleDecision d1 = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.0.5", null);
            RuleDecision d2 = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.0.5", null);
            assertEquals(d1 != null, d2 != null, "同一请求权重命中结果必须一致");
        }

        @Test
        @DisplayName("RULE-015: WEIGHT 0 永不命中")
        void testWeightZero_neverHit() {
            RuleDefinition.Condition c = cond(RuleDefinition.ConditionType.WEIGHT,
                    RuleDefinition.Operator.EQUALS, "0");
            c.setWeight(0);
            RuleDefinition r = rule("r1", 1, true, c,
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.0.0.5", null));
        }
    }

    // ==================== 优先级与首条命中 ====================

    @Nested
    @DisplayName("优先级与首条命中")
    class PriorityTests {

        @Test
        @DisplayName("RULE-016: 多规则命中取最高优先级")
        void testPriority_highestWins() {
            RuleDefinition low = rule("low", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            RuleDefinition high = rule("high", 100, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_INSTANCE, "premium-1"));
            engine.reloadRules(List.of(low, high));

            RuleDecision d = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null);
            assertNotNull(d);
            assertEquals("premium-1", d.getTargetInstanceId(), "高优先级规则应命中");
        }

        @Test
        @DisplayName("RULE-017: enabled=false 跳过")
        void testDisabled_skipped() {
            RuleDefinition disabled = rule("d1", 100, false,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(disabled));

            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
        }

        @Test
        @DisplayName("RULE-018: 首条命中即终止(规则间 OR)")
        void testFirstMatchWins() {
            RuleDefinition first = rule("f1", 10, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.CONTAINS, "gpt"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            RuleDefinition second = rule("f2", 5, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_INSTANCE, "x-1"));
            engine.reloadRules(List.of(first, second));

            RuleDecision d = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null);
            assertNotNull(d);
            assertEquals("claude-3", d.getTargetModelName(), "优先级高的首条命中");
        }

        @Test
        @DisplayName("RULE-019: 无匹配返回 null(兼容金路径)")
        void testNoMatch_returnsNull() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            engine.reloadRules(List.of(r));

            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "claude-3", "1.1.1.1", null));
        }

        @Test
        @DisplayName("RULE-020: 空规则列表返回 null")
        void testEmptyRules_returnsNull() {
            engine.reloadRules(List.of());
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
        }
    }

    // ==================== 多条件 AND 组合 ====================

    @Nested
    @DisplayName("多条件 AND 组合")
    class MultiConditionTests {

        @Test
        @DisplayName("RULE-021: 多条件全部满足才命中")
        void testAllConditionsMustMatch() {
            RuleDefinition r = rule("r1", 1, true, null,
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            RuleDefinition.Condition c1 = cond(RuleDefinition.ConditionType.MODEL_NAME,
                    RuleDefinition.Operator.EQUALS, "gpt-4");
            RuleDefinition.Condition c2 = cond(RuleDefinition.ConditionType.CLIENT_IP,
                    RuleDefinition.Operator.CIDR_MATCH, "10.0.0.0/8");
            r.setConditions(List.of(c1, c2));
            engine.reloadRules(List.of(r));

            assertNotNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "10.1.2.3", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "192.168.1.1", null));
            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "other", "10.1.2.3", null));
        }

        @Test
        @DisplayName("RULE-022: 空条件规则跳过")
        void testEmptyConditions_skipped() {
            RuleDefinition r = rule("r1", 1, true, null,
                    action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
            r.setConditions(List.of());
            engine.reloadRules(List.of(r));

            assertNull(engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null));
        }
    }

    // ==================== 动作目标 ====================

    @Nested
    @DisplayName("动作目标")
    class ActionTargetTests {

        @Test
        @DisplayName("RULE-023: LB_STRATEGY 动作返回策略名")
        void testLbStrategyAction() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.LB_STRATEGY, "round-robin"));
            engine.reloadRules(List.of(r));

            RuleDecision d = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null);
            assertNotNull(d);
            assertEquals("round-robin", d.getLbStrategy());
            assertNull(d.getTargetModelName());
            assertNull(d.getTargetInstanceId());
        }

        @Test
        @DisplayName("RULE-024: TARGET_ADAPTER 动作返回适配器名")
        void testTargetAdapterAction() {
            RuleDefinition r = rule("r1", 1, true,
                    cond(RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4"),
                    action(RuleDefinition.ActionType.TARGET_ADAPTER, "vllm-adapter"));
            engine.reloadRules(List.of(r));

            RuleDecision d = engine.evaluate(ModelServiceRegistry.ServiceType.chat, "gpt-4", "1.1.1.1", null);
            assertNotNull(d);
            assertEquals("vllm-adapter", d.getTargetAdapterName());
        }
    }
}

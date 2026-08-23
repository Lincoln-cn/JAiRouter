package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.rule.RuleDefinitionPersistenceService;
import org.unreal.modelrouter.router.rule.RuleEngineService;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleConfigController 规则 API 测试
 * TDD 测试先行:CRUD、启停、优先级、校验(沿用 v2.8.4 直接调用控制器方法风格)
 */
@DisplayName("RuleConfigController 规则 API 测试")
class RuleConfigControllerTest {

    private RuleEngineService ruleEngineService;
    private RuleDefinitionPersistenceService persistenceService;
    private RuleConfigController controller;

    /** 内存版持久化,避免依赖 StoreManager */
    private static class InMemoryPersistence extends RuleDefinitionPersistenceService {
        private final List<RuleDefinition> store = new ArrayList<>();

        InMemoryPersistence() {
            super(null, null);
        }

        @Override
        public void saveAll(final List<RuleDefinition> rules) {
            store.clear();
            store.addAll(rules);
        }

        @Override
        public List<RuleDefinition> loadAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void save(final RuleDefinition rule) {
            store.removeIf(r -> r.getId().equals(rule.getId()));
            store.add(rule);
        }

        @Override
        public void remove(final String id) {
            store.removeIf(r -> r.getId().equals(id));
        }
    }

    @BeforeEach
    void setUp() {
        ruleEngineService = new RuleEngineService();
        persistenceService = new InMemoryPersistence();
        controller = new RuleConfigController(ruleEngineService, persistenceService);
    }

    private RuleDefinition validRule(final String name) {
        RuleDefinition r = new RuleDefinition();
        r.setName(name);
        r.setPriority(10);
        r.setEnabled(true);
        RuleDefinition.Condition c = new RuleDefinition.Condition(
                RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4");
        r.setConditions(List.of(c));
        r.setAction(new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
        return r;
    }

    // ==================== 创建规则 ====================

    @Nested
    @DisplayName("创建规则测试")
    class CreateTests {

        @Test
        @DisplayName("RULEC-001: 创建规则成功并自动生成 ID")
        void testCreate_success() {
            ResponseEntity<RouterResponse<RuleDefinition>> response =
                    controller.createRule(validRule("rule1"));

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            RuleDefinition created = response.getBody().getData();
            assertNotNull(created.getId(), "创建时应自动生成 ID");
            assertEquals("rule1", created.getName());
        }

        @Test
        @DisplayName("RULEC-002: 名称缺失返回 400")
        void testCreate_blankName_400() {
            RuleDefinition r = validRule("  ");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createRule(r));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("RULEC-003: 无条件返回 400")
        void testCreate_noCondition_400() {
            RuleDefinition r = validRule("rule1");
            r.setConditions(List.of());
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createRule(r));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("RULEC-004: 无动作返回 400")
        void testCreate_noAction_400() {
            RuleDefinition r = validRule("rule1");
            r.setAction(null);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createRule(r));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("RULEC-005: 重复 ID 返回 409")
        void testCreate_duplicateId_409() {
            RuleDefinition r1 = validRule("rule1");
            controller.createRule(r1);
            RuleDefinition r2 = validRule("rule2");
            r2.setId(r1.getId());
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createRule(r2));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        @DisplayName("RULEC-006: HEADER 条件缺 field 返回 400")
        void testCreate_headerNoField_400() {
            RuleDefinition r = validRule("rule1");
            RuleDefinition.Condition c = new RuleDefinition.Condition(
                    RuleDefinition.ConditionType.HEADER, RuleDefinition.Operator.EQUALS, "vllm");
            r.setConditions(List.of(c));
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createRule(r));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("RULEC-007: CIDR_MATCH 用于非 CLIENT_IP 返回 400")
        void testCreate_cidrOnWrongType_400() {
            RuleDefinition r = validRule("rule1");
            RuleDefinition.Condition c = new RuleDefinition.Condition(
                    RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.CIDR_MATCH, "10.0.0.0/8");
            r.setConditions(List.of(c));
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createRule(r));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    // ==================== 查询与更新 ====================

    @Nested
    @DisplayName("查询与更新测试")
    class QueryUpdateTests {

        @Test
        @DisplayName("RULEC-008: 创建后列表包含该规则")
        void testList_containsCreated() {
            controller.createRule(validRule("rule1"));

            List<RuleDefinition> rules = controller.getAllRules().getBody().getData();

            assertEquals(1, rules.size());
            assertEquals("rule1", rules.get(0).getName());
        }

        @Test
        @DisplayName("RULEC-009: 列表按 priority 降序")
        void testList_sortedByPriorityDesc() {
            RuleDefinition low = validRule("low");
            low.setPriority(1);
            RuleDefinition high = validRule("high");
            high.setPriority(100);
            controller.createRule(low);
            controller.createRule(high);

            List<RuleDefinition> rules = controller.getAllRules().getBody().getData();

            assertEquals(2, rules.size());
            assertEquals("high", rules.get(0).getName(), "高优先级在前");
        }

        @Test
        @DisplayName("RULEC-010: 按 ID 获取单条规则")
        void testGetById() {
            RuleDefinition created = controller.createRule(validRule("rule1")).getBody().getData();

            RuleDefinition found = controller.getRule(created.getId()).getBody().getData();

            assertEquals("rule1", found.getName());
        }

        @Test
        @DisplayName("RULEC-011: 不存在的 ID 返回 404")
        void testGetById_missing_404() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.getRule("nonexistent"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("RULEC-012: 更新规则")
        void testUpdate() {
            RuleDefinition created = controller.createRule(validRule("rule1")).getBody().getData();
            created.setName("renamed");

            RuleDefinition updated = controller.updateRule(created.getId(), created).getBody().getData();

            assertEquals("renamed", updated.getName());
            RuleDefinition fetched = controller.getRule(created.getId()).getBody().getData();
            assertEquals("renamed", fetched.getName());
        }
    }

    // ==================== 启停与删除 ====================

    @Nested
    @DisplayName("启停与删除测试")
    class EnableDisableDeleteTests {

        @Test
        @DisplayName("RULEC-013: 停用规则后 enabled=false")
        void testDisable() {
            RuleDefinition created = controller.createRule(validRule("rule1")).getBody().getData();

            RuleDefinition disabled = controller.disableRule(created.getId()).getBody().getData();

            assertFalse(disabled.isEnabled());
        }

        @Test
        @DisplayName("RULEC-014: 启用规则后 enabled=true")
        void testEnable() {
            RuleDefinition created = controller.createRule(validRule("rule1")).getBody().getData();
            controller.disableRule(created.getId());

            RuleDefinition enabled = controller.enableRule(created.getId()).getBody().getData();

            assertTrue(enabled.isEnabled());
        }

        @Test
        @DisplayName("RULEC-015: 删除规则后列表为空")
        void testDelete() {
            RuleDefinition created = controller.createRule(validRule("rule1")).getBody().getData();

            controller.deleteRule(created.getId());

            List<RuleDefinition> rules = controller.getAllRules().getBody().getData();
            assertTrue(rules.isEmpty());
        }

        @Test
        @DisplayName("RULEC-016: 删除不存在的规则返回 404")
        void testDelete_missing_404() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.deleteRule("nonexistent"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    // ==================== 优先级批量调整 ====================

    @Nested
    @DisplayName("优先级批量调整测试")
    class PriorityTests {

        @Test
        @DisplayName("RULEC-017: 批量调整优先级生效")
        void testUpdatePriorities() {
            RuleDefinition r1 = controller.createRule(validRule("r1")).getBody().getData();
            RuleDefinition r2 = controller.createRule(validRule("r2")).getBody().getData();

            controller.updatePriorities(List.of(
                    Map.of("id", r1.getId(), "priority", 1),
                    Map.of("id", r2.getId(), "priority", 99)));

            List<RuleDefinition> rules = controller.getAllRules().getBody().getData();
            assertEquals("r2", rules.get(0).getName(), "r2 应排最前");
            assertEquals(1, rules.get(1).getPriority());
        }

        @Test
        @DisplayName("RULEC-018: 空列表返回 400")
        void testUpdatePriorities_empty_400() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.updatePriorities(List.of()));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("RULEC-019: 不存在的规则返回 404")
        void testUpdatePriorities_missing_404() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.updatePriorities(List.of(Map.of("id", "nonexistent", "priority", 5))));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }
}

package org.unreal.modelrouter.router.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RuleDefinitionPersistenceService 持久化服务测试
 * TDD 测试先行:save/load 往返、异常降级、删除
 */
@DisplayName("RuleDefinitionPersistenceService 持久化服务测试")
class RuleDefinitionPersistenceServiceTest {

    private StoreManager storeManager;
    private RuleDefinitionPersistenceService service;

    @BeforeEach
    void setUp() {
        storeManager = Mockito.mock(StoreManager.class);
        service = new RuleDefinitionPersistenceService(storeManager, new ObjectMapper());
    }

    private RuleDefinition rule(final String id, final String name) {
        RuleDefinition r = new RuleDefinition();
        r.setId(id);
        r.setName(name);
        r.setPriority(1);
        RuleDefinition.Condition c = new RuleDefinition.Condition(
                RuleDefinition.ConditionType.MODEL_NAME, RuleDefinition.Operator.EQUALS, "gpt-4");
        r.setConditions(List.of(c));
        r.setAction(new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3"));
        return r;
    }

    // ==================== 保存测试 ====================

    @Nested
    @DisplayName("保存规则测试")
    class SaveTests {

        @Test
        @DisplayName("PERSIST-001: 保存全部规则写入 StoreManager")
        void testSaveAll_writesToStore() {
            service.saveAll(List.of(rule("r1", "rule1"), rule("r2", "rule2")));

            verify(storeManager).saveConfig(eq("rule_definitions"), any(Map.class));
        }

        @Test
        @DisplayName("PERSIST-002: 保存失败降级为仅内存模式,不抛异常")
        void testSaveAll_storeFailure_degrades() {
            doThrow(new RuntimeException("store down")).when(storeManager)
                    .saveConfig(any(String.class), any(Map.class));

            assertDoesNotThrow(() -> service.saveAll(List.of(rule("r1", "rule1"))));
        }

        @Test
        @DisplayName("PERSIST-003: 保存单条规则")
        void testSave_single() {
            when(storeManager.getConfig("rule_definitions")).thenReturn(new HashMap<>());

            service.save(rule("r1", "rule1"));

            verify(storeManager).saveConfig(eq("rule_definitions"), any(Map.class));
        }
    }

    // ==================== 加载测试 ====================

    @Nested
    @DisplayName("加载规则测试")
    class LoadTests {

        @Test
        @DisplayName("PERSIST-004: 空存储返回空列表")
        void testLoadAll_emptyStore_returnsEmpty() {
            when(storeManager.getConfig("rule_definitions")).thenReturn(new HashMap<>());

            List<RuleDefinition> rules = service.loadAll();

            assertNotNull(rules);
            assertTrue(rules.isEmpty());
        }

        @Test
        @DisplayName("PERSIST-005: 加载已持久化规则(save/load 往返)")
        void testLoadAll_roundTrip() {
            // 模拟 StoreManager 保存后再读取(Jackson 序列化往返)
            Map<String, Object> saved = new HashMap<>();
            RuleDefinition r1 = rule("r1", "rule1");
            saved.put("r1", r1);
            when(storeManager.getConfig("rule_definitions")).thenReturn(saved);

            List<RuleDefinition> rules = service.loadAll();

            assertEquals(1, rules.size());
            assertEquals("r1", rules.get(0).getId());
            assertEquals("rule1", rules.get(0).getName());
        }

        @Test
        @DisplayName("PERSIST-006: 损坏 JSON 条目跳过不抛异常")
        void testLoadAll_corruptedEntry_skipped() {
            Map<String, Object> saved = new HashMap<>();
            saved.put("bad", "not-a-rule-object");
            when(storeManager.getConfig("rule_definitions")).thenReturn(saved);

            List<RuleDefinition> rules = service.loadAll();

            assertNotNull(rules);
            assertTrue(rules.isEmpty(), "损坏条目应被跳过");
        }

        @Test
        @DisplayName("PERSIST-007: 存储异常返回空列表")
        void testLoadAll_storeFailure_returnsEmpty() {
            when(storeManager.getConfig("rule_definitions"))
                    .thenThrow(new RuntimeException("store down"));

            List<RuleDefinition> rules = service.loadAll();

            assertNotNull(rules);
            assertTrue(rules.isEmpty());
        }
    }

    // ==================== 删除测试 ====================

    @Nested
    @DisplayName("删除规则测试")
    class RemoveTests {

        @Test
        @DisplayName("PERSIST-008: 删除存在的规则")
        void testRemove_existing() {
            Map<String, Object> saved = new HashMap<>();
            RuleDefinition r1 = rule("r1", "rule1");
            saved.put("r1", r1);
            when(storeManager.getConfig("rule_definitions")).thenReturn(saved);

            service.remove("r1");

            verify(storeManager).saveConfig(eq("rule_definitions"), any(Map.class));
        }

        @Test
        @DisplayName("PERSIST-009: 删除不存在的规则不触发保存")
        void testRemove_notExisting_noSave() {
            when(storeManager.getConfig("rule_definitions")).thenReturn(new HashMap<>());

            service.remove("nonexistent");

            verify(storeManager, never()).saveConfig(any(String.class), any(Map.class));
        }
    }
}

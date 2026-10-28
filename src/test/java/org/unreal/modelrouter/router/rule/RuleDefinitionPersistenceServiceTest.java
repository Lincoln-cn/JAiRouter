package org.unreal.modelrouter.router.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.unreal.modelrouter.persistence.store.MemoryStoreManager;
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
        @DisplayName("PERSIST-008: 删除存在的规则（落库内容必须已摘掉它）")
        void testRemove_existing() {
            Map<String, Object> saved = new HashMap<>();
            RuleDefinition r1 = rule("r1", "rule1");
            saved.put("r1", r1);
            when(storeManager.getConfig("rule_definitions")).thenReturn(saved);

            assertTrue(service.remove("r1"), "持久化层确有该条目时 remove 必须返回 true");

            // 只断言「saveConfig 被调用过」不够：传旧载荷同样满足。这里钉住载荷本身。
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(storeManager).saveConfig(eq("rule_definitions"), captor.capture());
            assertFalse(captor.getValue().containsKey("r1"),
                    "删除后落库的表里不应再有 r1，实际=" + captor.getValue().keySet());
        }

        @Test
        @DisplayName("PERSIST-010: 删除最后一条规则后重新加载为空（真实 StoreManager 往返）")
        void testRemove_lastRule_roundTripEmpty() {
            // 用真实实现而非 Mockito 桩：桩不复现 store 侧「空表被静默丢弃」，正是它让本仓长期绿着漏掉该缺陷。
            RuleDefinitionPersistenceService real =
                    new RuleDefinitionPersistenceService(new MemoryStoreManager(), new ObjectMapper());
            real.save(rule("only-rule", "rule-only"));
            assertEquals(1, real.loadAll().size(), "前置：已有一条");

            assertTrue(real.remove("only-rule"), "真实 StoreManager 下删除也必须返回 true");

            assertTrue(real.loadAll().isEmpty(),
                    "删掉最后一条规则后必须读回空集合；非空即说明空表被丢弃、条目会复活");
        }

        @Test
        @DisplayName("PERSIST-009: 删除不存在的规则不触发保存")
        void testRemove_notExisting_noSave() {
            when(storeManager.getConfig("rule_definitions")).thenReturn(new HashMap<>());

            // #708: 返回值是调用方区分「YAML 只读 / 持久化层没有该条目」的唯一信号，
            // 之前 remove 是 void，静默 no-op 也照样回 200。
            assertFalse(service.remove("nonexistent"),
                    "持久化层没有该条目时 remove 必须返回 false");

            verify(storeManager, never()).saveConfig(any(String.class), any(Map.class));
        }
    }
}

package org.unreal.modelrouter.router.rule.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RuleTemplateService 场景模板测试
 */
@DisplayName("RuleTemplateService 场景模板测试")
class RuleTemplateServiceTest {

    private final RuleTemplateService service = new RuleTemplateService();

    @Test
    @DisplayName("模板列表包含 7 个预置模板")
    void getAllTemplates_containsSeven() {
        List<RuleTemplate> templates = service.getAllTemplates();
        assertEquals(7, templates.size());
    }

    @Test
    @DisplayName("限流保护模板生成 RATE_LIMIT 草稿(含容量/速率参数)")
    void buildRuleDefinition_rateLimitTemplate() {
        RuleDefinition draft = service.buildRuleDefinition("rate-limit", Map.of("name", "限流规则"));

        assertNull(draft.getId());
        assertEquals(RuleDefinition.ActionType.RATE_LIMIT, draft.getAction().getType());
        assertEquals(100L, draft.getAction().getCapacity());
        assertEquals(10L, draft.getAction().getRate());
        assertEquals("token-bucket", draft.getAction().getAlgorithm());
        assertEquals("rule", draft.getAction().getScope());
    }

    @Test
    @DisplayName("buildRuleDefinition 生成草稿(无 id,深拷贝条件与动作)")
    void buildRuleDefinition_generatesDraftAndCopies() {
        RuleDefinition draft = service.buildRuleDefinition("tenant", Map.of("name", "我的租户规则"));

        assertNull(draft.getId());
        assertEquals("我的租户规则", draft.getName());
        assertEquals(90, draft.getPriority());
        assertEquals(RuleDefinition.ConditionType.HEADER, draft.getConditions().get(0).getType());
        assertEquals(RuleDefinition.ActionType.TARGET_INSTANCE, draft.getAction().getType());
        assertEquals("internal-gpu-1", draft.getAction().getInstanceId());
    }

    @Test
    @DisplayName("buildRuleDefinition 支持 priority 覆盖")
    void buildRuleDefinition_priorityOverride() {
        RuleDefinition draft = service.buildRuleDefinition("canary", Map.of("name", "灰度", "priority", "200"));
        assertEquals(200, draft.getPriority());
    }

    @Test
    @DisplayName("copyAction 复制 TARGET_TAGS 动作保留 tags(不注入单一 target)")
    void copyAction_targetTags_keepsTags() throws Exception {
        RuleDefinition.Action source = new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_TAGS, null);
        source.setTags(Map.of("gpu", "a100", "region", "cn"));

        Method copyAction = RuleTemplateService.class.getDeclaredMethod("copyAction", RuleDefinition.Action.class);
        copyAction.setAccessible(true);
        RuleDefinition.Action copy = (RuleDefinition.Action) copyAction.invoke(service, source);

        assertEquals(RuleDefinition.ActionType.TARGET_TAGS, copy.getType());
        assertNull(copy.getModelName());
        assertEquals(Map.of("gpu", "a100", "region", "cn"), copy.getTags());
    }

    @Test
    @DisplayName("buildRuleDefinition 未知模板抛异常")
    void buildRuleDefinition_unknown_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buildRuleDefinition("nonexistent", Map.of("name", "x")));
    }

    @Test
    @DisplayName("buildRuleDefinition 缺名称抛异常")
    void buildRuleDefinition_missingName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buildRuleDefinition("canary", Map.of()));
    }
}

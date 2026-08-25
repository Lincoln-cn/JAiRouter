package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;
import org.unreal.modelrouter.router.rule.template.RuleTemplate;
import org.unreal.modelrouter.router.rule.template.RuleTemplateService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RuleTemplateController 场景模板 API 测试
 */
@DisplayName("RuleTemplateController 场景模板 API 测试")
class RuleTemplateControllerTest {

    private final RuleTemplateController controller =
            new RuleTemplateController(new RuleTemplateService());

    @Test
    @DisplayName("TMPL-001: 模板列表返回 6 个模板")
    void listTemplates() {
        List<RuleTemplate> templates = controller.getAllTemplates().getBody().getData();
        assertEquals(6, templates.size());
    }

    @Test
    @DisplayName("TMPL-002: 从模板创建返回草稿(201,无 id)")
    void createFromTemplate_201() {
        RuleDefinition draft = controller.createFromTemplate(
                "canary", Map.of("name", "灰度规则")).getBody().getData();

        assertNull(draft.getId());
        assertEquals("灰度规则", draft.getName());
        assertEquals(RuleDefinition.ConditionType.CLIENT_IP, draft.getConditions().get(0).getType());
    }

    @Test
    @DisplayName("TMPL-003: 缺名称返回 400")
    void missingName_400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.createFromTemplate("canary", Map.of()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("TMPL-004: 未知模板返回 404")
    void unknownTemplate_404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getTemplateById("nonexistent"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}

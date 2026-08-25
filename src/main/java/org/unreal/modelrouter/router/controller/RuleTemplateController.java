package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;
import org.unreal.modelrouter.router.rule.template.RuleTemplate;
import org.unreal.modelrouter.router.rule.template.RuleTemplateService;

import java.util.List;
import java.util.Map;

/**
 * 路由规则场景模板控制器
 * 预置规则模板,从模板生成规则草稿(不持久化,由前端预填表单后走正常创建接口)
 */
@RestController
@RequestMapping("/api/config/rules/templates")
@CrossOrigin(origins = "*")
@Tag(name = "路由规则场景模板", description = "预置规则模板,从模板快速创建规则")
public class RuleTemplateController {

    private final RuleTemplateService templateService;

    public RuleTemplateController(final RuleTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @Operation(summary = "获取规则模板列表")
    public ResponseEntity<RouterResponse<List<RuleTemplate>>> getAllTemplates() {
        return ResponseEntity.ok(RouterResponse.success(templateService.getAllTemplates()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取规则模板详情")
    public ResponseEntity<RouterResponse<RuleTemplate>> getTemplateById(@PathVariable final String id) {
        RuleTemplate template = templateService.getTemplateById(id);
        if (template == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found: " + id);
        }
        return ResponseEntity.ok(RouterResponse.success(template));
    }

    @PostMapping("/{id}/create")
    @Operation(summary = "从模板创建规则", description = "基于模板生成规则草稿(不持久化,前端预填表单后保存)")
    public ResponseEntity<RouterResponse<RuleDefinition>> createFromTemplate(
            @PathVariable final String id,
            @RequestBody final Map<String, String> overrides) {
        if (overrides == null || overrides.get("name") == null || overrides.get("name").isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        RuleDefinition draft = templateService.buildRuleDefinition(id, overrides);
        return ResponseEntity.status(HttpStatus.CREATED).body(RouterResponse.success(draft));
    }
}

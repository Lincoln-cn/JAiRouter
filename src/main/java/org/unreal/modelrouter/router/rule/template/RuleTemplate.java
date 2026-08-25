package org.unreal.modelrouter.router.rule.template;

import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.util.List;

/**
 * 路由规则场景模板
 * 预置常见路由场景(灰度/租户隔离/模型重写等),从模板快速创建规则
 */
public class RuleTemplate {

    private String id;
    private String name;
    private String description;
    private String category;
    private int defaultPriority;
    private String usageTip;
    private List<RuleDefinition.Condition> conditions;
    private RuleDefinition.Action action;

    public RuleTemplate() {
    }

    public RuleTemplate(final String id, final String name, final String description,
                        final String category, final int defaultPriority, final String usageTip,
                        final List<RuleDefinition.Condition> conditions,
                        final RuleDefinition.Action action) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultPriority = defaultPriority;
        this.usageTip = usageTip;
        this.conditions = conditions;
        this.action = action;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(final String category) {
        this.category = category;
    }

    public int getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(final int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public String getUsageTip() {
        return usageTip;
    }

    public void setUsageTip(final String usageTip) {
        this.usageTip = usageTip;
    }

    public List<RuleDefinition.Condition> getConditions() {
        return conditions;
    }

    public void setConditions(final List<RuleDefinition.Condition> conditions) {
        this.conditions = conditions;
    }

    public RuleDefinition.Action getAction() {
        return action;
    }

    public void setAction(final RuleDefinition.Action action) {
        this.action = action;
    }
}

package org.unreal.modelrouter.router.rule.template;

import org.springframework.stereotype.Service;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 路由规则场景模板服务
 * 管理预置的规则模板(灰度发布/租户隔离/模型重写等),从模板生成规则草稿
 */
@Service
public class RuleTemplateService {

    private final List<RuleTemplate> templates;

    public RuleTemplateService() {
        this.templates = new ArrayList<>();
        initTemplates();
    }

    /**
     * 获取全部模板(按分类+名称排序)
     */
    public List<RuleTemplate> getAllTemplates() {
        return templates.stream()
                .sorted((a, b) -> {
                    int byCategory = a.getCategory().compareTo(b.getCategory());
                    return byCategory != 0 ? byCategory : a.getName().compareTo(b.getName());
                })
                .collect(Collectors.toList());
    }

    /**
     * 按 ID 获取模板
     */
    public RuleTemplate getTemplateById(final String id) {
        return templates.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从模板构建规则草稿(深拷贝条件与动作,id 留空由创建接口生成)
     *
     * @param templateId 模板 ID
     * @param overrides  覆盖参数(name 必填, priority 可选)
     */
    public RuleDefinition buildRuleDefinition(final String templateId, final Map<String, String> overrides) {
        RuleTemplate template = getTemplateById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        String name = overrides.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("规则名称不能为空");
        }

        RuleDefinition rule = new RuleDefinition();
        rule.setName(name);
        rule.setEnabled(true);
        String priority = overrides.get("priority");
        rule.setPriority(priority != null ? Integer.parseInt(priority) : template.getDefaultPriority());
        rule.setConditions(copyConditions(template.getConditions()));
        rule.setAction(copyAction(template.getAction()));
        return rule;
    }

    private List<RuleDefinition.Condition> copyConditions(final List<RuleDefinition.Condition> conditions) {
        List<RuleDefinition.Condition> copy = new ArrayList<>();
        for (RuleDefinition.Condition condition : conditions) {
            RuleDefinition.Condition c = new RuleDefinition.Condition(
                    condition.getType(), condition.getOperator(), condition.getValue());
            c.setField(condition.getField());
            c.setWeight(condition.getWeight());
            copy.add(c);
        }
        return copy;
    }

    private RuleDefinition.Action copyAction(final RuleDefinition.Action action) {
        String target = null;
        switch (action.getType()) {
            case TARGET_MODEL -> {
                target = action.getModelName();
            }
            case TARGET_INSTANCE -> {
                target = action.getInstanceId();
            }
            case TARGET_ADAPTER -> {
                target = action.getAdapterName();
            }
            case LB_STRATEGY -> {
                target = action.getLbStrategy();
            }
            case RATE_LIMIT -> { /* 限流参数经下方 setter 复制 */ }
        }
        RuleDefinition.Action copy = new RuleDefinition.Action(action.getType(), target);
        copy.setCapacity(action.getCapacity());
        copy.setRate(action.getRate());
        copy.setAlgorithm(action.getAlgorithm());
        copy.setScope(action.getScope());
        copy.setWarmUpPeriod(action.getWarmUpPeriod());
        return copy;
    }

    private void initTemplates() {
        templates.add(new RuleTemplate(
                "canary",
                "灰度发布",
                "按来源 IP 网段将部分流量路由到新模型,用于灰度验证",
                "发布",
                100,
                "将 CIDR 与目标模型名替换为你的灰度网段与模型",
                List.of(new RuleDefinition.Condition(
                        RuleDefinition.ConditionType.CLIENT_IP,
                        RuleDefinition.Operator.CIDR_MATCH, "10.0.0.0/8")),
                new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3")));

        templates.add(new RuleTemplate(
                "tenant",
                "租户/渠道隔离",
                "按请求头 x-tenant 将指定租户路由到专用实例",
                "路由",
                90,
                "将 header 值与目标实例名替换为你的租户标识与实例",
                List.of(new RuleDefinition.Condition(
                        RuleDefinition.ConditionType.HEADER,
                        RuleDefinition.Operator.EQUALS, "tenant-a")),
                new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_INSTANCE, "internal-gpu-1")));

        templates.add(new RuleTemplate(
                "model-rewrite",
                "模型名重写",
                "将指定模型名的请求重写到另一模型(如 gpt-4 → claude-3)",
                "路由",
                80,
                "将源模型名与目标模型名替换为你的值",
                List.of(new RuleDefinition.Condition(
                        RuleDefinition.ConditionType.MODEL_NAME,
                        RuleDefinition.Operator.EQUALS, "gpt-4")),
                new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3")));

        templates.add(new RuleTemplate(
                "weight-50",
                "权重分流",
                "按 IP+模型名稳定哈希,50% 请求走新模型",
                "发布",
                70,
                "调整 weight(0-100)与目标模型名;同一请求对始终命中同一结果",
                List.of(new RuleDefinition.Condition(
                        RuleDefinition.ConditionType.WEIGHT,
                        RuleDefinition.Operator.EQUALS, "50")),
                new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_MODEL, "claude-3")));

        templates.add(new RuleTemplate(
                "adapter-switch",
                "适配器切换",
                "按请求头 x-routing 切换适配器(如切到 vLLM)",
                "路由",
                60,
                "将 header 值与适配器名替换为你的值",
                List.of(new RuleDefinition.Condition(
                        RuleDefinition.ConditionType.HEADER,
                        RuleDefinition.Operator.EQUALS, "vllm")),
                new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_ADAPTER, "vllm")));

        templates.add(new RuleTemplate(
                "vip-pin",
                "VIP 实例锁定",
                "将指定来源 IP 固定路由到专用实例",
                "路由",
                50,
                "将 IP 与目标实例名替换为你的值",
                List.of(new RuleDefinition.Condition(
                        RuleDefinition.ConditionType.CLIENT_IP,
                        RuleDefinition.Operator.EQUALS, "10.0.0.8")),
                new RuleDefinition.Action(RuleDefinition.ActionType.TARGET_INSTANCE, "internal-gpu-1")));

        RuleDefinition.Action rateLimitAction = new RuleDefinition.Action(RuleDefinition.ActionType.RATE_LIMIT, null);
        rateLimitAction.setCapacity(100L);
        rateLimitAction.setRate(10L);
        rateLimitAction.setAlgorithm("token-bucket");
        rateLimitAction.setScope("rule");
        templates.add(new RuleTemplate(
                "rate-limit",
                "限流保护",
                "按模型名对请求做规则级限流,超限返回 429(保护后端服务)",
                "防护",
                40,
                "调整容量(令牌桶容量)与速率(每秒补充)适配你的 QPS 预算",
                List.of(new RuleDefinition.Condition(
                        RuleDefinition.ConditionType.MODEL_NAME,
                        RuleDefinition.Operator.EQUALS, "gpt-4")),
                rateLimitAction));
    }
}

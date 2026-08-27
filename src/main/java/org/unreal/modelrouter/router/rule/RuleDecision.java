package org.unreal.modelrouter.router.rule;

import org.unreal.modelrouter.router.rule.model.RuleDefinition;

/**
 * 规则求值决策结果
 * 命中规则后携带动作目标;无命中时返回 null
 */
public final class RuleDecision {

    private final RuleDefinition rule;

    public RuleDecision(final RuleDefinition rule) {
        this.rule = rule;
    }

    public RuleDefinition getRule() {
        return rule;
    }

    /** 动作目标:重写模型名 */
    public String getTargetModelName() {
        return rule.getAction() != null && rule.getAction().getType() == RuleDefinition.ActionType.TARGET_MODEL
                ? rule.getAction().getModelName() : null;
    }

    /** 动作目标:锁定实例 */
    public String getTargetInstanceId() {
        return rule.getAction() != null && rule.getAction().getType() == RuleDefinition.ActionType.TARGET_INSTANCE
                ? rule.getAction().getInstanceId() : null;
    }

    /** 动作目标:覆盖适配器 */
    public String getTargetAdapterName() {
        return rule.getAction() != null && rule.getAction().getType() == RuleDefinition.ActionType.TARGET_ADAPTER
                ? rule.getAction().getAdapterName() : null;
    }

    /** 动作目标:覆盖 LB 策略 */
    public String getLbStrategy() {
        return rule.getAction() != null && rule.getAction().getType() == RuleDefinition.ActionType.LB_STRATEGY
                ? rule.getAction().getLbStrategy() : null;
    }
}

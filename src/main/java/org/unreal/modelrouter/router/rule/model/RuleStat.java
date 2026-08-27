package org.unreal.modelrouter.router.rule.model;

/**
 * 规则命中统计项
 */
public final class RuleStat {

    private String ruleId;
    private String ruleName;
    private String actionType;
    private long hits;

    public RuleStat() {
    }

    public RuleStat(final String ruleId, final String ruleName, final String actionType, final long hits) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.actionType = actionType;
        this.hits = hits;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(final String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(final String ruleName) {
        this.ruleName = ruleName;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(final String actionType) {
        this.actionType = actionType;
    }

    public long getHits() {
        return hits;
    }

    public void setHits(final long hits) {
        this.hits = hits;
    }
}

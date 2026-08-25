package org.unreal.modelrouter.router.rule.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由规则定义
 * 条件(AND 组合)命中后执行动作(重写模型名/锁定实例/覆盖适配器/覆盖LB策略)
 */
public class RuleDefinition {

    /** 条件类型 */
    public enum ConditionType {
        SERVICE_TYPE,
        MODEL_NAME,
        HEADER,
        CLIENT_IP,
        WEIGHT
    }

    /** 操作符 */
    public enum Operator {
        EQUALS,
        CONTAINS,
        STARTS_WITH,
        REGEX,
        CIDR_MATCH
    }

    /** 动作类型 */
    public enum ActionType {
        TARGET_MODEL,
        TARGET_INSTANCE,
        TARGET_ADAPTER,
        LB_STRATEGY
    }

    /** 规则来源 */
    public enum Source {
        YAML,       // 配置文件默认规则
        PERSISTED   // Web 页面创建/持久化规则
    }

    /** 条件 */
    public static class Condition {
        private ConditionType type;
        private String field;       // 仅 HEADER 用(header 名)
        private Operator operator;
        private String value;
        private Integer weight;     // 可选,仅 WEIGHT 型

        public Condition() {
        }

        public Condition(final ConditionType type, final Operator operator, final String value) {
            this.type = type;
            this.operator = operator;
            this.value = value;
        }

        public ConditionType getType() {
            return type;
        }

        public void setType(final ConditionType type) {
            this.type = type;
        }

        public String getField() {
            return field;
        }

        public void setField(final String field) {
            this.field = field;
        }

        public Operator getOperator() {
            return operator;
        }

        public void setOperator(final Operator operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        public Integer getWeight() {
            return weight;
        }

        public void setWeight(final Integer weight) {
            this.weight = weight;
        }
    }

    /** 动作 */
    public static class Action {
        private ActionType type;
        private String modelName;       // TARGET_MODEL
        private String instanceId;      // TARGET_INSTANCE
        private String adapterName;     // TARGET_ADAPTER
        private String lbStrategy;      // LB_STRATEGY

        public Action() {
        }

        public Action(final ActionType type, final String target) {
            this.type = type;
            switch (type) {
                case TARGET_MODEL -> this.modelName = target;
                case TARGET_INSTANCE -> this.instanceId = target;
                case TARGET_ADAPTER -> this.adapterName = target;
                case LB_STRATEGY -> this.lbStrategy = target;
            }
        }

        public ActionType getType() {
            return type;
        }

        public void setType(final ActionType type) {
            this.type = type;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(final String modelName) {
            this.modelName = modelName;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(final String instanceId) {
            this.instanceId = instanceId;
        }

        public String getAdapterName() {
            return adapterName;
        }

        public void setAdapterName(final String adapterName) {
            this.adapterName = adapterName;
        }

        public String getLbStrategy() {
            return lbStrategy;
        }

        public void setLbStrategy(final String lbStrategy) {
            this.lbStrategy = lbStrategy;
        }
    }

    private String id;              // UUID,创建时自动生成
    private String name;            // 必填
    private String description;
    private boolean enabled = true; // 默认启用
    private int priority;           // 越大越先;降序,首条命中即终止
    private String matchMode = "ALL"; // v1 固定 AND,预留 OR
    private List<Condition> conditions = new ArrayList<>();
    private Action action;
    private Source source;          // v2.8.7: YAML/PERSISTED;旧数据 null 兼容

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
    }

    public String getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(final String matchMode) {
        this.matchMode = matchMode;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(final List<Condition> conditions) {
        this.conditions = conditions;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(final Action action) {
        this.action = action;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(final Source source) {
        this.source = source;
    }
}

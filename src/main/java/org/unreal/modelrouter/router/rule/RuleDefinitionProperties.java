package org.unreal.modelrouter.router.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由规则 YAML 配置属性
 * 从 rules.yml 的 model.rules 节加载默认规则(可为空)
 */
@Configuration
@ConfigurationProperties(prefix = "model.rules")
public class RuleDefinitionProperties {

    private List<RuleDefinition> rules = new ArrayList<>();

    public List<RuleDefinition> getRules() {
        return rules;
    }

    public void setRules(final List<RuleDefinition> rules) {
        this.rules = rules;
    }
}

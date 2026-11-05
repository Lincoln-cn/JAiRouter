package org.unreal.modelrouter.pii;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 可逆脱敏配置（#987）。
 *
 * <p>默认关闭：开启后网关会改写发给上游的内容，属于「改变请求语义」的行为，
 * 不该在没人知情的情况下生效。</p>
 */
@Component
@ConfigurationProperties(prefix = "jairouter.pii.reversible")
public class PiiProperties {

    /** 总开关。关闭时请求与响应两侧都不做任何处理。 */
    private boolean enabled = false;

    /** 占位符前缀，最终形如 {@code [PII_1]}。 */
    private String placeholderPrefix = "PII";

    /** 映射保留时长，超过后清理任务会删除；只覆盖「一次请求的响应还没回来」的窗口。 */
    private Duration ttl = Duration.ofMinutes(30);

    /** 字段级策略：只有命中的 JSON 路径才做脱敏；为空表示整个请求体文本都参与匹配。 */
    private List<String> jsonPaths = new ArrayList<>();

    /** 内置规则是否启用（身份证 / 手机号 / 银行卡 / 邮箱）。 */
    private boolean builtinRulesEnabled = true;

    /** 额外规则，与 {@link SanitizationRule} 同形（正则 + 规则名）。 */
    private List<Rule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPlaceholderPrefix() {
        return placeholderPrefix;
    }

    public void setPlaceholderPrefix(String placeholderPrefix) {
        this.placeholderPrefix = placeholderPrefix;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public List<String> getJsonPaths() {
        return jsonPaths;
    }

    public void setJsonPaths(List<String> jsonPaths) {
        this.jsonPaths = jsonPaths;
    }

    public boolean isBuiltinRulesEnabled() {
        return builtinRulesEnabled;
    }

    public void setBuiltinRulesEnabled(boolean builtinRulesEnabled) {
        this.builtinRulesEnabled = builtinRulesEnabled;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    /**
     * 一条自定义脱敏规则。
     */
    public static class Rule {

        /** 规则名，会写进 vault 的 rule_name 供审计查询 */
        private String name;

        /** Java 正则，命中部分整体替换为占位符 */
        private String pattern;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}

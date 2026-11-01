package org.unreal.modelrouter.router.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式护栏配置（{@code jairouter.guard.*}）。
 *
 * <p>默认全部关闭：护栏会改变下游看到的字节，应当由运维明确打开。</p>
 *
 * @since 3.6.0
 */
@Component
@ConfigurationProperties(prefix = "jairouter.guard")
public class GuardProperties {

    /** 总开关；关闭时链路完全不介入，零开销。 */
    private boolean enabled = false;

    /** 脱敏（REDACT）配置。 */
    private Redact redact = new Redact();

    /** 阻断（ABORT）配置。 */
    private Block block = new Block();

    /**
     * 取得脱敏配置。
     *
     * @return 脱敏配置
     */
    public Redact getRedact() {
        return redact;
    }

    /**
     * 设置脱敏配置。
     *
     * @param redact 脱敏配置
     */
    public void setRedact(final Redact redact) {
        this.redact = redact;
    }

    /**
     * 取得阻断配置。
     *
     * @return 阻断配置
     */
    public Block getBlock() {
        return block;
    }

    /**
     * 设置阻断配置。
     *
     * @param block 阻断配置
     */
    public void setBlock(final Block block) {
        this.block = block;
    }

    /**
     * 是否启用。
     *
     * @return 启用时为 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 脱敏配置：命中正则的文本被替换成占位符。
     */
    public static class Redact {

        /** 是否启用脱敏。 */
        private boolean enabled = true;

        /** 规则列表。 */
        private List<Pattern> patterns = new ArrayList<>();

        /**
         * 是否启用。
         *
         * @return 启用时为 true
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 取得规则列表。
         *
         * @return 规则列表
         */
        public List<Pattern> getPatterns() {
            return patterns;
        }

        /**
         * 设置规则列表。
         *
         * @param patterns 规则列表
         */
        public void setPatterns(final List<Pattern> patterns) {
            this.patterns = patterns;
        }
    }

    /**
     * 阻断配置：命中正则即终止当前流。
     */
    public static class Block {

        /** 是否启用阻断。 */
        private boolean enabled = true;

        /** 规则列表。 */
        private List<Pattern> patterns = new ArrayList<>();

        /**
         * 是否启用。
         *
         * @return 启用时为 true
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 取得规则列表。
         *
         * @return 规则列表
         */
        public List<Pattern> getPatterns() {
            return patterns;
        }

        /**
         * 设置规则列表。
         *
         * @param patterns 规则列表
         */
        public void setPatterns(final List<Pattern> patterns) {
            this.patterns = patterns;
        }
    }

    /**
     * 一条规则。
     */
    public static class Pattern {

        /** 规则名，进指标与审计。 */
        private String name;

        /** 正则（Java 语法）。 */
        private String regex;

        /** 脱敏时的替换文本；阻断规则忽略此字段。 */
        private String replacement = "[REDACTED]";

        /**
         * 取得规则名。
         *
         * @return 规则名
         */
        public String getName() {
            return name;
        }

        /**
         * 设置规则名。
         *
         * @param name 规则名
         */
        public void setName(final String name) {
            this.name = name;
        }

        /**
         * 取得正则。
         *
         * @return 正则表达式
         */
        public String getRegex() {
            return regex;
        }

        /**
         * 设置正则。
         *
         * @param regex 正则表达式
         */
        public void setRegex(final String regex) {
            this.regex = regex;
        }

        /**
         * 取得替换文本。
         *
         * @return 替换文本
         */
        public String getReplacement() {
            return replacement;
        }

        /**
         * 设置替换文本。
         *
         * @param replacement 替换文本
         */
        public void setReplacement(final String replacement) {
            this.replacement = replacement;
        }
    }
}

package org.unreal.modelrouter.router.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.router.guard.GuardProperties.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * 按正则把敏感文本替换成占位符的脱敏算子。
 *
 * <p>替换直接作用于 chunk 原文。chunk 通常是 JSON，因此规则正则必须只可能命中
 * <b>字符串字面量内部</b>的内容（手机号、身份证号这类都不会跨过引号与花括号），
 * 且 {@code replacement} 必须是安全的字面量（不含引号、反斜杠、换行）——
 * 否则会产出下游解析不了的 JSON。</p>
 *
 * @since 3.6.0
 */
final class RegexRedactGuard implements ChunkGuard {

    private static final Logger log = LoggerFactory.getLogger(RegexRedactGuard.class);

    private final List<Compiled> compiled;

    /**
     * 从配置构建。
     *
     * @param patterns 配置的规则列表
     */
    RegexRedactGuard(final List<Pattern> patterns) {
        final List<Compiled> list = new ArrayList<>();
        for (final Pattern config : patterns) {
            if (config == null || config.getRegex() == null || config.getRegex().isBlank()) {
                continue;
            }
            try {
                final String name = config.getName() == null || config.getName().isBlank()
                        ? "redact-" + list.size() : config.getName();
                final String replacement = config.getReplacement() == null
                        ? "[REDACTED]" : config.getReplacement();
                list.add(new Compiled(name, java.util.regex.Pattern.compile(config.getRegex()), replacement));
            } catch (final PatternSyntaxException e) {
                log.warn("流式护栏：脱敏规则 '{}' 的正则无效，已忽略：{}", config.getName(), e.getMessage());
            }
        }
        this.compiled = List.copyOf(list);
    }

    @Override
    public GuardDecision evaluate(final String chunk) {
        if (chunk == null || compiled.isEmpty()) {
            return GuardDecision.allow(chunk);
        }
        String current = chunk;
        String lastPolicy = null;
        for (final Compiled rule : compiled) {
            if (rule.pattern().matcher(current).find()) {
                current = rule.pattern().matcher(current)
                        .replaceAll(java.util.regex.Matcher.quoteReplacement(rule.replacement()));
                lastPolicy = rule.name();
            }
        }
        if (lastPolicy == null) {
            return GuardDecision.allow(chunk);
        }
        return GuardDecision.redact(lastPolicy, current, "已脱敏");
    }

    @Override
    public String name() {
        return "regex-redact";
    }

    /**
     * 编译后的规则。
     *
     * @param name        规则名
     * @param pattern     编译后的正则
     * @param replacement 替换文本
     */
    private record Compiled(String name, java.util.regex.Pattern pattern, String replacement) {
    }
}

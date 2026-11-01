package org.unreal.modelrouter.router.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.router.guard.GuardProperties.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * 命中即终止当前流的阻断算子。
 *
 * <p>用于「不该流给客户端」的内容：密钥泄漏、越权指令、被禁止的话题等。
 * 判定在 chunk 原文上做（不解析 JSON），因此既能命中正文，也能命中上游放进
 * {@code id} / {@code model} 之类字段的串 —— 后者通常意味着上游本身出了问题。</p>
 *
 * @since 3.6.0
 */
final class RegexAbortGuard implements ChunkGuard {

    private static final Logger log = LoggerFactory.getLogger(RegexAbortGuard.class);

    private final List<Compiled> compiled;

    /**
     * 从配置构建。
     *
     * @param patterns 配置的规则列表
     */
    RegexAbortGuard(final List<Pattern> patterns) {
        final List<Compiled> list = new ArrayList<>();
        for (final Pattern config : patterns) {
            if (config == null || config.getRegex() == null || config.getRegex().isBlank()) {
                continue;
            }
            try {
                final String name = config.getName() == null || config.getName().isBlank()
                        ? "block-" + list.size() : config.getName();
                list.add(new Compiled(name, java.util.regex.Pattern.compile(config.getRegex())));
            } catch (final PatternSyntaxException e) {
                log.warn("流式护栏：阻断规则 '{}' 的正则无效，已忽略：{}", config.getName(), e.getMessage());
            }
        }
        this.compiled = List.copyOf(list);
    }

    @Override
    public GuardDecision evaluate(final String chunk) {
        if (chunk == null || compiled.isEmpty()) {
            return GuardDecision.allow(chunk);
        }
        for (final Compiled rule : compiled) {
            if (rule.pattern().matcher(chunk).find()) {
                return GuardDecision.abort(rule.name(), "命中阻断规则 " + rule.name());
            }
        }
        return GuardDecision.allow(chunk);
    }

    @Override
    public String name() {
        return "regex-abort";
    }

    /**
     * 编译后的规则。
     *
     * @param name    规则名
     * @param pattern 编译后的正则
     */
    private record Compiled(String name, java.util.regex.Pattern pattern) {
    }
}

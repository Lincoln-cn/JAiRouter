package org.unreal.modelrouter.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 管理面/配置面用户提供的正则安全编译器.
 *
 * <p>覆盖脱敏 piiPatterns 等场景：拒绝空串、超长、嵌套量词、被量词包裹的交替分支、
 * 反向引用以及过大量词，合法模式经校验后缓存编译结果。运行时匹配也应走本类，
 * 避免对未校验的用户正则直接 {@link Pattern#compile}。</p>
 */
public final class SafeRegexValidator {

    public static final int MAX_PATTERN_LENGTH = 512;
    public static final int MAX_QUANTIFIER = 10_000;

    private static final ConcurrentMap<String, Pattern> CACHE = new ConcurrentHashMap<>();

    private static final Pattern NESTED_QUANTIFIER = Pattern.compile(
            "\\([^()]*(?:[+*]|\\{\\d+,\\d*\\})[^()]*\\)\\s*(?:[+*]|\\{\\d+,\\d*\\})");
    private static final Pattern NESTED_GROUP_QUANTIFIER = Pattern.compile(
            "\\([^()]*\\([^()]*[+*][^()]*\\)[^()]*\\)\\s*[+*]");
    private static final Pattern QUANTIFIED_ALTERNATION = Pattern.compile(
            "\\([^)]*\\|[^)]*\\)\\s*(?:[+*]|\\{\\d+,\\d*\\})");
    private static final Pattern BACKREFERENCE = Pattern.compile("\\\\[1-9]");
    private static final Pattern BOUNDED_QUANTIFIER = Pattern.compile(
            "\\{\\s*(\\d+)\\s*(?:,\\s*(\\d*)\\s*)?\\}");

    private SafeRegexValidator() {
    }

    /**
     * 校验用户正则是否可安全编译使用。
     *
     * @return {@code null} 表示通过；否则返回可展示给调用方的错误原因
     */
    public static String validateUserPattern(final String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "存在空模式";
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            return "长度超过 " + MAX_PATTERN_LENGTH + " 个字符";
        }
        final String redos = detectRedosRisk(pattern);
        if (redos != null) {
            return redos;
        }
        try {
            doCompile(pattern);
            return null;
        } catch (PatternSyntaxException e) {
            final String detail = e.getDescription() != null ? e.getDescription() : "syntax error";
            return "非法正则: " + detail;
        }
    }

    public static boolean isSafeUserPattern(final String pattern) {
        return validateUserPattern(pattern) == null;
    }

    /**
     * 编译已通过安全校验的用户正则（结果缓存）。
     *
     * @throws IllegalArgumentException 校验未通过
     */
    public static Pattern compileUserPattern(final String pattern) {
        final String error = validateUserPattern(pattern);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        return CACHE.computeIfAbsent(pattern, SafeRegexValidator::doCompile);
    }

    /**
     * 校验并编译；不安全或非法时返回 {@code null}（调用方跳过该规则）。
     */
    public static Pattern compileOrNull(final String pattern) {
        if (!isSafeUserPattern(pattern)) {
            return null;
        }
        return CACHE.computeIfAbsent(pattern, SafeRegexValidator::doCompile);
    }

    /**
     * 格式化原始模式用于日志：≤80 字符时原样附带，否则省略以防日志膨胀。
     */
    public static String formatPatternForLog(final String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "";
        }
        return pattern.length() <= 80 ? ", pattern=" + pattern : "";
    }

    private static Pattern doCompile(final String pattern) {
        return Pattern.compile(pattern);
    }

    private static String detectRedosRisk(final String pattern) {
        if (NESTED_QUANTIFIER.matcher(pattern).find()
                || NESTED_GROUP_QUANTIFIER.matcher(pattern).find()) {
            return "包含嵌套量词，存在 ReDoS 风险";
        }
        if (QUANTIFIED_ALTERNATION.matcher(pattern).find()) {
            return "包含被量词包裹的交替分支，存在 ReDoS 风险";
        }
        if (BACKREFERENCE.matcher(pattern).find()) {
            return "包含反向引用，已禁止";
        }
        final Matcher quantifier = BOUNDED_QUANTIFIER.matcher(pattern);
        while (quantifier.find()) {
            final int min = Integer.parseInt(quantifier.group(1));
            final String maxRaw = quantifier.group(2);
            final int max = (maxRaw == null || maxRaw.isEmpty()) ? min : Integer.parseInt(maxRaw);
            if (min > MAX_QUANTIFIER || max > MAX_QUANTIFIER) {
                return "量词过大（>" + MAX_QUANTIFIER + "），存在资源耗尽风险";
            }
        }
        return null;
    }
}

package org.unreal.modelrouter.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    // -----------------------------------------------------------------------
    //  ReDoS scanner — single left-to-right pass, O(n), no regex compiled
    //  from or run against the user-supplied pattern string.
    // -----------------------------------------------------------------------

    /**
     * Detect ReDoS risk via a character scanner.
     *
     * <p>Six rejection reasons, produced without compiling any regex against the
     * user pattern:
     * <ol>
     *   <li>Nested quantifier — a group that contains a quantifier, itself
     *       followed by a quantifier ({@code +}, {@code *}, or {@code {…}}).</li>
     *   <li>Quantified alternation — a group containing {@code |}, followed by
     *       a quantifier.</li>
     *   <li>Backreference — {@code \1} … {@code \9}.</li>
     *   <li>Oversized bounded quantifier — {@code {n}} / {@code {n,}} /
     *       {@code {n,m}} with any bound &gt; {@link #MAX_QUANTIFIER}.</li>
     * </ol>
     *
     * @return rejection message, or {@code null} if safe
     */
    private static String detectRedosRisk(final String pattern) {
        final int len = pattern.length();
        final int arraySize = len + 2; // safe upper bound for nesting depth
        final boolean[] containsQuantifier = new boolean[arraySize];
        final boolean[] containsAlternation = new boolean[arraySize];
        int depth = 0;

        for (int i = 0; i < len; i++) {
            final char c = pattern.charAt(i);

            switch (c) {

                /* --- backslash: backreference or escape --- */
                case '\\':
                    if (i + 1 < len) {
                        final char next = pattern.charAt(i + 1);
                        if (next >= '1' && next <= '9') {
                            return "包含反向引用，已禁止";
                        }
                        i++; // consume the escaped character
                    }
                    break;

                /* --- character class: consume the entire [...] inline --- */
                case '[': {
                    int ci = i + 1;
                    // optional negation
                    if (ci < len && pattern.charAt(ci) == '^') {
                        ci++;
                    }
                    // leading ] is literal (e.g. []abc])
                    if (ci < len && pattern.charAt(ci) == ']') {
                        ci++;
                    }
                    // scan for the real closing ]
                    while (ci < len) {
                        if (pattern.charAt(ci) == '\\' && ci + 1 < len) {
                            ci += 2; // skip escaped char inside class
                        } else if (pattern.charAt(ci) == ']') {
                            break;
                        } else {
                            ci++;
                        }
                    }
                    // ci points to ] or past end; for-loop increments past it
                    i = ci;
                    break;
                }

                /* --- group open --- */
                case '(':
                    if (depth < arraySize - 1) {
                        depth++;
                        containsQuantifier[depth] = false;
                        containsAlternation[depth] = false;
                    }
                    break;

                /* --- group close --- */
                case ')':
                    if (depth > 0) {
                        final boolean risky =
                                containsQuantifier[depth] || containsAlternation[depth];
                        if (risky && isFollowedByQuantifier(pattern, i + 1)) {
                            return containsAlternation[depth]
                                    ? "包含被量词包裹的交替分支，存在 ReDoS 风险"
                                    : "包含嵌套量词，存在 ReDoS 风险";
                        }
                        // propagate "contains quantifier" to parent depth
                        if (containsQuantifier[depth]) {
                            containsQuantifier[depth - 1] = true;
                        }
                        depth--;
                    }
                    break;

                /* --- alternation (only meaningful inside a group) --- */
                case '|':
                    if (depth > 0) {
                        containsAlternation[depth] = true;
                    }
                    break;

                /* --- simple quantifiers --- */
                case '+': case '*': case '?':
                    if (depth > 0) {
                        containsQuantifier[depth] = true;
                    }
                    break;

                /* --- bounded quantifier {n}, {n,}, {n,m} --- */
                case '{': {
                    final int[] endPos = new int[1];
                    final int bound = parseBoundedQuantifier(pattern, i, endPos);
                    if (bound >= 0) {
                        if (bound > MAX_QUANTIFIER) {
                            return "量词过大（>" + MAX_QUANTIFIER + "），存在资源耗尽风险";
                        }
                        if (depth > 0) {
                            containsQuantifier[depth] = true;
                        }
                        i = endPos[0]; // advance to closing }
                    }
                    // else: { is literal (valid in Java regex)
                    break;
                }

                default:
                    break;
            }
        }

        return null;
    }

    /**
     * Check whether the substring starting at {@code start} (after optional
     * whitespace) begins with a quantifier that triggers the nested-quantifier
     * or quantified-alternation rejection: {@code +}, {@code *}, or a valid
     * {@code {…}} bound.  Note: {@code ?} alone is intentionally excluded
     * from the outer trigger to match the original heuristic semantics.
     */
    private static boolean isFollowedByQuantifier(final String pattern, final int start) {
        int i = start;
        final int len = pattern.length();
        while (i < len && Character.isWhitespace(pattern.charAt(i))) {
            i++;
        }
        if (i >= len) {
            return false;
        }
        final char c = pattern.charAt(i);
        if (c == '+' || c == '*') {
            return true;
        }
        if (c == '{') {
            final int[] endPos = new int[1];
            return parseBoundedQuantifier(pattern, i, endPos) >= 0;
        }
        return false;
    }

    /**
     * Parse a bounded quantifier starting at {@code pos} (the {@code '{'}
     * character).  Only plain ASCII digits are accepted.
     *
     * @return the effective bound ({@code max(min, max)}), or -1 if the text
     *         at {@code pos} is not a valid bounded quantifier.
     */
    private static int parseBoundedQuantifier(final String pattern, final int pos,
                                              final int[] endPos) {
        final int len = pattern.length();
        int i = pos + 1;

        // skip whitespace
        while (i < len && Character.isWhitespace(pattern.charAt(i))) {
            i++;
        }

        // first number: mandatory ASCII digits
        final int numStart = i;
        while (i < len && pattern.charAt(i) >= '0' && pattern.charAt(i) <= '9') {
            i++;
        }
        if (i == numStart) {
            return -1; // no digits → not a quantifier
        }

        final int min;
        try {
            min = Integer.parseInt(pattern.substring(numStart, i));
        } catch (final NumberFormatException e) {
            return -1;
        }

        // skip whitespace
        while (i < len && Character.isWhitespace(pattern.charAt(i))) {
            i++;
        }
        if (i >= len) {
            return -1;
        }

        int max = min;
        if (pattern.charAt(i) == ',') {
            i++;
            // skip whitespace
            while (i < len && Character.isWhitespace(pattern.charAt(i))) {
                i++;
            }
            final int secondStart = i;
            while (i < len && pattern.charAt(i) >= '0' && pattern.charAt(i) <= '9') {
                i++;
            }
            if (i > secondStart) {
                try {
                    max = Integer.parseInt(pattern.substring(secondStart, i));
                } catch (final NumberFormatException e) {
                    return -1;
                }
            }
            // else: {n,} → max stays equal to min (preserves original semantics)
            // skip whitespace
            while (i < len && Character.isWhitespace(pattern.charAt(i))) {
                i++;
            }
        }

        if (i >= len || pattern.charAt(i) != '}') {
            return -1;
        }

        endPos[0] = i;
        return Math.max(min, max);
    }
}

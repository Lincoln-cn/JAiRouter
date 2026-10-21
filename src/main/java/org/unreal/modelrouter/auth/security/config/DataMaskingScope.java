package org.unreal.modelrouter.auth.security.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.util.AntPathMatcher;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;

/**
 * 响应脱敏的「生效范围」解析器（issue #560 可配置化 A 档）。
 *
 * <p>历史实现把「哪些路径脱敏」写死在 {@link ExcludedPathsConfig} 的两个静态集合里。这里的职责是
 * 把同样的判定搬到配置上，同时<b>默认行为逐字不变</b>：{@code scope.patterns} 为空时继续使用
 * {@link ExcludedPathsConfig#DATA_MASKING_EXCLUDED_PATHS} / {@code ..._PATTERNS} 作为排除面。</p>
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>{@code exclude}（默认）—— {@code patterns} 是**不脱敏**的例外；未命中例外的路径都脱敏。</li>
 *   <li>{@code include} —— 只有命中 {@code patterns} 的路径才脱敏；用于收窄到某几个入口。</li>
 * </ul>
 *
 * <p><b>安全约束</b>：{@code include} 模式且 {@code patterns} 为空时，「命中集合」为空 ⇒ 一条响应
 * 都不会脱敏。本类不静默回退到 exclude（那会让界面显示的「当前不脱敏任何响应」与服务端行为不一致），
 * 而是如实返回「全部不在范围内」并给出 {@link #warning()}，由调用方（控制器 / 前端）显式提示。</p>
 */
public final class DataMaskingScope {

    /** 排除模式：patterns 是不脱敏的例外。 */
    public static final String MODE_EXCLUDE = "exclude";

    /** 包含模式：只有命中 patterns 的路径才脱敏。 */
    public static final String MODE_INCLUDE = "include";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final String mode;
    private final List<String> patterns;
    private final boolean usingBuiltInDefaults;

    private DataMaskingScope(final String mode, final List<String> patterns, final boolean usingBuiltInDefaults) {
        this.mode = mode;
        this.patterns = List.copyOf(patterns);
        this.usingBuiltInDefaults = usingBuiltInDefaults;
    }

    /**
     * 从响应脱敏配置解析生效范围。
     *
     * @param config 响应脱敏配置，可为 {@code null}（视为默认排除模式）
     * @return 生效范围
     */
    public static DataMaskingScope from(final SanitizationConfig.ResponseSanitization config) {
        final SanitizationConfig.Scope scope = config == null ? null : config.getScope();
        final String rawMode = scope == null ? null : scope.getMode();
        final String mode = MODE_INCLUDE.equalsIgnoreCase(trimToEmpty(rawMode)) ? MODE_INCLUDE : MODE_EXCLUDE;

        final List<String> configured = scope == null ? null : scope.getPatterns();
        final List<String> cleaned = new ArrayList<>();
        if (configured != null) {
            for (final String pattern : configured) {
                final String trimmed = trimToEmpty(pattern);
                if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                    cleaned.add(trimmed);
                }
            }
        }

        if (cleaned.isEmpty() && MODE_EXCLUDE.equals(mode)) {
            final List<String> defaults = new ArrayList<>(ExcludedPathsConfig.DATA_MASKING_EXCLUDED_PATHS);
            defaults.addAll(ExcludedPathsConfig.DATA_MASKING_EXCLUDED_PATTERNS);
            return new DataMaskingScope(MODE_EXCLUDE, defaults, true);
        }
        return new DataMaskingScope(mode, cleaned, false);
    }

    /**
     * 路径是否应当脱敏。
     *
     * @param path 请求路径
     * @return {@code true} 表示该路径的响应进入脱敏；{@code false} 表示跳过
     */
    public boolean shouldSanitize(final String path) {
        if (path == null || path.isEmpty()) {
            // 拿不到路径时无法判定：排除模式按「需要脱敏」处理（fail-closed），包含模式按「不在范围」处理。
            return MODE_EXCLUDE.equals(mode);
        }
        final boolean matched = matchedPattern(path) != null;
        return MODE_INCLUDE.equals(mode) ? matched : !matched;
    }

    /**
     * 命中的那条 pattern；未命中返回 {@code null}。
     *
     * <p>匹配规则与历史实现保持一致：带通配符的按 Ant 风格匹配，不带通配符的按<b>前缀</b>匹配
     * （旧实现里 {@code "/api/"} 这类条目就是靠前缀命中的）。</p>
     *
     * @param path 请求路径
     * @return 命中的 pattern 或 {@code null}
     */
    public String matchedPattern(final String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (final String pattern : patterns) {
            if (matches(pattern, path)) {
                return pattern;
            }
        }
        return null;
    }

    private static boolean matches(final String pattern, final String path) {
        if (pattern.indexOf('*') < 0 && pattern.indexOf('?') < 0) {
            return path.startsWith(pattern);
        }
        if (PATH_MATCHER.match(pattern, path)) {
            return true;
        }
        // "/api/**" 这类模式在 AntPathMatcher 下不匹配 "/api" 本身，但旧集合里的 "/api/" 前缀语义覆盖它。
        if (pattern.endsWith("/**")) {
            final String base = pattern.substring(0, pattern.length() - 3);
            return path.equals(base);
        }
        return false;
    }

    /**
     * 生效范围的机器可读描述，供配置接口回显。
     *
     * @return 含 mode / patterns / usingBuiltInDefaults / warning 的只读视图
     */
    public Map<String, Object> describe() {
        return Map.of(
                "mode", mode,
                "patterns", patterns,
                "usingBuiltInDefaults", usingBuiltInDefaults,
                "warning", warning() == null ? "" : warning());
    }

    /**
     * 需要显式提示给人看的问题；没有问题返回 {@code null}。
     *
     * @return 提示文案或 {@code null}
     */
    public String warning() {
        if (MODE_INCLUDE.equals(mode) && patterns.isEmpty()) {
            return "scope.mode=include 但 scope.patterns 为空：当前没有任何响应会被脱敏";
        }
        return null;
    }

    public String getMode() {
        return mode;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public boolean isUsingBuiltInDefaults() {
        return usingBuiltInDefaults;
    }

    private static String trimToEmpty(final String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataMaskingScope that)) {
            return false;
        }
        return usingBuiltInDefaults == that.usingBuiltInDefaults
                && mode.equals(that.mode)
                && patterns.equals(that.patterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, patterns, usingBuiltInDefaults);
    }

    @Override
    public String toString() {
        return "DataMaskingScope{mode='" + mode + "', patterns=" + patterns
                + ", usingBuiltInDefaults=" + usingBuiltInDefaults + '}';
    }
}

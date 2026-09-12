package org.unreal.modelrouter.auth.security.quota;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 配额账本配置（{@code jairouter.quota.*}）。
 *
 * <p>默认 {@link #isEnabled() enabled=false}，即完全不启用账本：不累加、不查库、不落库，
 * 行为与 v3.0.x 完全一致（零行为变更）。</p>
 *
 * <p>保留期使用字符串而非 {@link java.time.Duration}：月级保留期（{@code 13mo}）无法用
 * {@code java.time.Duration} 表达，统一采用 {@code Nmo}/{@code Nd}/{@code Nh}/{@code Nm}/{@code Ns}
 * 形式（{@code mo}=月、{@code d}=天、{@code h}=小时、{@code m}=分钟、{@code s}=秒，
 * 无单位按天解释），解析见 {@link #retentionCutoff(QuotaWindow, LocalDateTime)}。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.quota")
public class QuotaProperties {

    /** 默认启用的四级窗口：分钟 / 小时 / 天 / 月 */
    private static final List<QuotaWindow> DEFAULT_WINDOWS =
        List.of(QuotaWindow.MINUTE, QuotaWindow.HOUR, QuotaWindow.DAY, QuotaWindow.MONTH);

    /** 默认保留期：minute=1d / hour=2d / day=35d / month=13mo */
    private static final Map<String, String> DEFAULT_RETENTION = defaultRetention();

    /** 无法解析保留期时的兜底天数 */
    private static final long FALLBACK_RETENTION_DAYS = 1L;

    /**
     * 是否启用配额账本（默认 false：零行为变更）
     */
    private boolean enabled = false;

    /**
     * 账本异常时是否 fail-open（默认 true）：true = 放行请求并标记降级，false = 拒绝请求
     */
    private boolean failOpen = true;

    /**
     * 内存账本快照落库间隔（秒，默认 60）
     */
    private long flushIntervalSeconds = 60L;

    /**
     * 启用的窗口列表（默认四级：MINUTE / HOUR / DAY / MONTH）
     */
    private List<QuotaWindow> windows = new ArrayList<>(DEFAULT_WINDOWS);

    /**
     * 各级窗口保留期，键为窗口名（minute / hour / day / month）
     */
    private Map<String, String> retention = new LinkedHashMap<>(DEFAULT_RETENTION);

    /**
     * 当前启用的窗口列表（去重、去空、保持配置顺序）。
     *
     * @return 启用窗口列表，未配置时为空列表
     */
    public List<QuotaWindow> enabledWindows() {
        if (windows == null || windows.isEmpty()) {
            return List.of();
        }
        final List<QuotaWindow> result = new ArrayList<>(windows.size());
        for (final QuotaWindow window : windows) {
            if (window != null && !result.contains(window)) {
                result.add(window);
            }
        }
        return result;
    }

    /**
     * 读取指定窗口的保留期原文。
     *
     * @param window 窗口类型，可为 {@code null}
     * @return 保留期原文（如 {@code 1d}、{@code 13mo}），未配置时按默认值返回
     */
    public String retentionSpec(final QuotaWindow window) {
        if (window == null) {
            return "";
        }
        final String name = window.name();
        final String lowerName = name.toLowerCase(Locale.ROOT);
        if (retention != null) {
            final String exact = retention.get(name);
            if (exact != null) {
                return exact;
            }
            final String lower = retention.get(lowerName);
            if (lower != null) {
                return lower;
            }
        }
        final String fallback = DEFAULT_RETENTION.get(lowerName);
        return Objects.requireNonNullElse(fallback, "");
    }

    /**
     * 计算指定窗口的保留期截止时间：早于该时间的账本行可在 {@code cleanupExpired()} 中清理。
     *
     * @param window 窗口类型，可为 {@code null}（返回一天前的截止时间）
     * @param now    当前时间
     * @return 保留期截止时间
     */
    public LocalDateTime retentionCutoff(final QuotaWindow window, final LocalDateTime now) {
        return minus(now, retentionSpec(window));
    }

    /**
     * 默认保留期常量表（键为窗口名小写）。
     *
     * @return 默认保留期
     */
    private static Map<String, String> defaultRetention() {
        final Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(QuotaWindow.MINUTE.name().toLowerCase(Locale.ROOT), "1d");
        defaults.put(QuotaWindow.HOUR.name().toLowerCase(Locale.ROOT), "2d");
        defaults.put(QuotaWindow.DAY.name().toLowerCase(Locale.ROOT), "35d");
        defaults.put(QuotaWindow.MONTH.name().toLowerCase(Locale.ROOT), "13mo");
        return defaults;
    }

    /**
     * 按保留期原文回推截止时间。支持 {@code mo}（月）/ {@code d} / {@code h} / {@code m} / {@code s}，
     * 解析失败或无单位时按天解释，数量最小为 1（避免误配 0 导致整表被清理）。
     *
     * @param now  当前时间
     * @param spec 保留期原文
     * @return 截止时间
     */
    private static LocalDateTime minus(final LocalDateTime now, final String spec) {
        final String value = spec == null ? "" : spec.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return now.minusDays(FALLBACK_RETENTION_DAYS);
        }
        if (value.endsWith("mo")) {
            return now.minusMonths(parseAmount(value.substring(0, value.length() - 2)));
        }
        final char unit = value.charAt(value.length() - 1);
        final long amount = parseAmount(value.substring(0, value.length() - 1));
        switch (unit) {
            case 'd':
                return now.minusDays(amount);
            case 'h':
                return now.minusHours(amount);
            case 'm':
                return now.minusMinutes(amount);
            case 's':
                return now.minusSeconds(amount);
            default:
                return now.minusDays(amount);
        }
    }

    /**
     * 解析保留期数量部分，非法值兜底为 1。
     *
     * @param text 数量文本
     * @return 不小于 1 的数量
     */
    private static long parseAmount(final String text) {
        try {
            return Math.max(1L, Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return 1L;
        }
    }
}

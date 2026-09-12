package org.unreal.modelrouter.auth.security.quota;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * 配额账本时间窗口。
 *
 * <p>每个窗口按“自然时间”对齐截断（{@link #windowStart(LocalDateTime)}）：
 * 分钟窗口对齐到整分、小时窗口对齐到整点、天窗口对齐到当天 0 点、月窗口对齐到当月 1 日 0 点。
 * 账本以 {@code (维度, 窗口, 窗口起点)} 三元组作为唯一键，因此窗口起点必须稳定可复现。</p>
 *
 * <p>窗口名称同时作为 {@code quota_ledger.window_type} 列的值（见 {@link #name()}）。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
public enum QuotaWindow {

    /** 分钟窗口（对齐到整分） */
    MINUTE,

    /** 小时窗口（对齐到整点） */
    HOUR,

    /** 天窗口（对齐到当天 0 点） */
    DAY,

    /** 月窗口（对齐到当月 1 日 0 点） */
    MONTH;

    /**
     * 计算该窗口在给定时刻所处的窗口起点。
     *
     * @param time 任意时刻，不能为 {@code null}
     * @return 该时刻所属窗口的起始时间
     * @throws IllegalArgumentException 当 {@code time} 为 {@code null} 时
     */
    public LocalDateTime windowStart(final LocalDateTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time 不能为 null");
        }
        switch (this) {
            case MINUTE:
                return time.truncatedTo(ChronoUnit.MINUTES);
            case HOUR:
                return time.truncatedTo(ChronoUnit.HOURS);
            case DAY:
                return time.toLocalDate().atStartOfDay();
            case MONTH:
                return time.toLocalDate().withDayOfMonth(1).atStartOfDay();
            default:
                return time.truncatedTo(ChronoUnit.MINUTES);
        }
    }

    /**
     * 宽松解析窗口名称（忽略大小写与首尾空格），用于读取数据库中的 {@code window_type} 列。
     *
     * @param value 窗口名称，可为 {@code null}
     * @return 匹配到的窗口，无法解析时返回 {@link Optional#empty()}
     */
    public static Optional<QuotaWindow> parse(final String value) {
        if (value == null) {
            return Optional.empty();
        }
        final String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (final QuotaWindow window : values()) {
            if (window.name().equals(normalized)) {
                return Optional.of(window);
            }
        }
        return Optional.empty();
    }
}

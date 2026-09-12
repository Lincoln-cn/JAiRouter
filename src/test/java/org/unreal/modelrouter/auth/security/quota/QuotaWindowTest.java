package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuotaWindow} 窗口截断边界测试（v3.1 PR-1）。
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaWindow 窗口截断测试")
class QuotaWindowTest {

    @Test
    @DisplayName("MINUTE：截断到整分（秒与纳秒归零）")
    void minute_shouldTruncateToMinute() {
        final LocalDateTime time = LocalDateTime.of(2026, 3, 14, 13, 45, 59, 999_000_000);

        assertEquals(LocalDateTime.of(2026, 3, 14, 13, 45), QuotaWindow.MINUTE.windowStart(time));
    }

    @Test
    @DisplayName("HOUR：截断到整点（分秒纳秒归零）")
    void hour_shouldTruncateToHour() {
        final LocalDateTime time = LocalDateTime.of(2026, 3, 14, 13, 45, 59, 999_000_000);

        assertEquals(LocalDateTime.of(2026, 3, 14, 13, 0), QuotaWindow.HOUR.windowStart(time));
    }

    @Test
    @DisplayName("DAY：截断到当天 0 点")
    void day_shouldTruncateToMidnight() {
        final LocalDateTime time = LocalDateTime.of(2026, 3, 14, 23, 59, 59, 999_000_000);

        assertEquals(LocalDateTime.of(2026, 3, 14, 0, 0), QuotaWindow.DAY.windowStart(time));
    }

    @Test
    @DisplayName("MONTH：截断到当月 1 日 0 点（跨月边界）")
    void month_shouldTruncateToFirstDay() {
        assertEquals(LocalDateTime.of(2026, 3, 1, 0, 0),
            QuotaWindow.MONTH.windowStart(LocalDateTime.of(2026, 3, 31, 23, 59, 59, 999_000_000)));
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0),
            QuotaWindow.MONTH.windowStart(LocalDateTime.of(2026, 4, 1, 0, 0, 0)));
        // 闰年 2 月 29 日
        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0),
            QuotaWindow.MONTH.windowStart(LocalDateTime.of(2024, 2, 29, 12, 0)));
    }

    @Test
    @DisplayName("边界时刻：已对齐的时间点截断后保持不变")
    void alreadyAlignedTime_shouldRemainUnchanged() {
        final LocalDateTime minuteAligned = LocalDateTime.of(2026, 3, 14, 13, 45, 0);

        assertEquals(minuteAligned, QuotaWindow.MINUTE.windowStart(minuteAligned));
        assertEquals(LocalDateTime.of(2026, 3, 14, 13, 0), QuotaWindow.HOUR.windowStart(minuteAligned));
        assertEquals(LocalDateTime.of(2026, 3, 14, 0, 0), QuotaWindow.DAY.windowStart(minuteAligned));
        assertEquals(LocalDateTime.of(2026, 3, 1, 0, 0), QuotaWindow.MONTH.windowStart(minuteAligned));
    }

    @Test
    @DisplayName("null 时间点抛出 IllegalArgumentException")
    void nullTime_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> QuotaWindow.HOUR.windowStart(null));
    }

    @Test
    @DisplayName("parse：忽略大小写与空格，非法值返回 empty")
    void parse_shouldBeLenient() {
        assertEquals(Optional.of(QuotaWindow.DAY), QuotaWindow.parse("day"));
        assertEquals(Optional.of(QuotaWindow.DAY), QuotaWindow.parse("  DAY "));
        assertEquals(Optional.of(QuotaWindow.MINUTE), QuotaWindow.parse("Minute"));
        assertTrue(QuotaWindow.parse("week").isEmpty());
        assertTrue(QuotaWindow.parse("").isEmpty());
        assertTrue(QuotaWindow.parse(null).isEmpty());
    }

    @Test
    @DisplayName("窗口名称即数据库 window_type 列取值")
    void name_shouldBeStable() {
        assertEquals("MINUTE", QuotaWindow.MINUTE.name());
        assertEquals("HOUR", QuotaWindow.HOUR.name());
        assertEquals("DAY", QuotaWindow.DAY.name());
        assertEquals("MONTH", QuotaWindow.MONTH.name());
        assertEquals(4, QuotaWindow.values().length);
    }
}

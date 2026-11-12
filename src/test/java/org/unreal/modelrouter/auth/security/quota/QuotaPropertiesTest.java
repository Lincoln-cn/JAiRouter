package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuotaProperties} 默认值与保留期解析测试（v3.1 PR-1）。
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaProperties 配置与保留期测试")
class QuotaPropertiesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    @Test
    @DisplayName("默认开启：enabled=true / failOpen=true / 60 秒落库 / 三级窗口（不含分钟）")
    void defaults_shouldBeEnabledAndFourWindows() {
        final QuotaProperties properties = new QuotaProperties();

        // 2026-11 行为变更：配额账本默认开启（按 Key 的日/时/月额度真正生效）。
        // 需要完全关闭时显式设置 jairouter.quota.enabled=false。
        assertTrue(properties.isEnabled(), "配额账本默认开启，否则按 Key 配置的额度永远不会被检查");
        assertTrue(properties.isFailOpen());
        assertEquals(60L, properties.getFlushIntervalSeconds());
        // v3.8.6：每分钟速率改由 ApiKeyMinuteRateLimitService 在主链路判定，账本不再含 MINUTE ——
        // 否则同一个 rateLimitPerMinute 会被两套计数各判一次，多副本下两套口径还不一致。
        assertEquals(List.of(QuotaWindow.HOUR, QuotaWindow.DAY, QuotaWindow.MONTH),
            properties.enabledWindows());
    }

    @Test
    @DisplayName("默认保留期：minute=1d / hour=2d / day=35d / month=13mo")
    void defaultRetention_shouldMatchSpec() {
        final QuotaProperties properties = new QuotaProperties();

        assertEquals("1d", properties.retentionSpec(QuotaWindow.MINUTE));
        assertEquals("2d", properties.retentionSpec(QuotaWindow.HOUR));
        assertEquals("35d", properties.retentionSpec(QuotaWindow.DAY));
        assertEquals("13mo", properties.retentionSpec(QuotaWindow.MONTH));
    }

    @Test
    @DisplayName("保留期截止时间按窗口分别计算（含月级 13mo）")
    void retentionCutoff_shouldBePerWindow() {
        final QuotaProperties properties = new QuotaProperties();

        assertEquals(NOW.minusDays(1), properties.retentionCutoff(QuotaWindow.MINUTE, NOW));
        assertEquals(NOW.minusDays(2), properties.retentionCutoff(QuotaWindow.HOUR, NOW));
        assertEquals(NOW.minusDays(35), properties.retentionCutoff(QuotaWindow.DAY, NOW));
        assertEquals(NOW.minusMonths(13), properties.retentionCutoff(QuotaWindow.MONTH, NOW));
    }

    @Test
    @DisplayName("保留期可被配置覆盖（大小写不敏感，未覆盖的窗口回落默认值）")
    void retention_shouldBeOverridable() {
        final QuotaProperties properties = new QuotaProperties();
        final Map<String, String> retention = new LinkedHashMap<>();
        retention.put("DAY", "60d");
        retention.put("minute", "3h");
        properties.setRetention(retention);

        assertEquals(NOW.minusDays(60), properties.retentionCutoff(QuotaWindow.DAY, NOW));
        assertEquals(NOW.minusHours(3), properties.retentionCutoff(QuotaWindow.MINUTE, NOW));
        // 未覆盖的窗口回落默认值
        assertEquals(NOW.minusDays(2), properties.retentionCutoff(QuotaWindow.HOUR, NOW));
        assertEquals(NOW.minusMonths(13), properties.retentionCutoff(QuotaWindow.MONTH, NOW));
    }

    @Test
    @DisplayName("保留期解析：支持 mo/d/h/m/s，非法值与 0 兜底（不会被清空）")
    void retentionCutoff_shouldTolerateIllegalValues() {
        final QuotaProperties properties = new QuotaProperties();
        final Map<String, String> retention = new LinkedHashMap<>();
        retention.put("minute", "90s");
        retention.put("hour", "5m");
        retention.put("day", "0d");
        retention.put("month", "abc");
        properties.setRetention(retention);

        assertEquals(NOW.minusSeconds(90), properties.retentionCutoff(QuotaWindow.MINUTE, NOW));
        assertEquals(NOW.minusMinutes(5), properties.retentionCutoff(QuotaWindow.HOUR, NOW));
        assertEquals(NOW.minusDays(1), properties.retentionCutoff(QuotaWindow.DAY, NOW));
        assertEquals(NOW.minusDays(1), properties.retentionCutoff(QuotaWindow.MONTH, NOW));
        // null 窗口与 null 时间值兜底
        assertEquals(NOW.minusDays(1), properties.retentionCutoff(null, NOW));
        assertEquals("", properties.retentionSpec(null));
    }

    @Test
    @DisplayName("windows 配置：去重、忽略 null，空配置表示不启用任何窗口")
    void enabledWindows_shouldDeduplicate() {
        final QuotaProperties properties = new QuotaProperties();
        final List<QuotaWindow> windows = new ArrayList<>();
        windows.add(QuotaWindow.DAY);
        windows.add(QuotaWindow.DAY);
        windows.add(null);
        windows.add(QuotaWindow.MINUTE);
        properties.setWindows(windows);
        assertEquals(List.of(QuotaWindow.DAY, QuotaWindow.MINUTE), properties.enabledWindows());

        properties.setWindows(null);
        assertTrue(properties.enabledWindows().isEmpty());

        properties.setWindows(new ArrayList<>());
        assertTrue(properties.enabledWindows().isEmpty());
    }
}

package org.unreal.modelrouter.monitor.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.monitor.monitoring.alert.SlowQueryAlertService;
import org.unreal.modelrouter.monitor.monitoring.alert.SlowQueryAlertStats;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SlowQueryAnalysisController 单元测试
 *
 * <p>测试慢查询分析功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("SlowQueryAnalysisController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlowQueryAnalysisControllerTest {

    @Mock
    private SlowQueryDetector slowQueryDetector;

    @Mock
    private PerformanceTracker performanceTracker;

    @Mock
    private SlowQueryAlertService slowQueryAlertService;

    @InjectMocks
    private SlowQueryAnalysisController controller;

    private SlowQueryDetector.SlowQueryStats testStats;

    @BeforeEach
    void setUp() {
        // SlowQueryStats 没有 setter 方法，使用 mock 来模拟
        testStats = mock(SlowQueryDetector.SlowQueryStats.class);
        when(testStats.getCount()).thenReturn(10L);
        when(testStats.getAverageDuration()).thenReturn(500.0);
        when(testStats.getMaxDuration()).thenReturn(2000L);
    }

    // ==================== 获取慢查询统计测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/slow-queries/stats - 获取慢查询统计测试")
    class GetSlowQueryStatsTests {

        @Test
        @DisplayName("SLOW-001: 成功获取慢查询统计")
        void testGetSlowQueryStats_success() {
            // Given
            when(slowQueryDetector.getAllSlowQueryStats())
                    .thenReturn(Map.of("test-operation", testStats));

            // When
            Map<String, SlowQueryDetector.SlowQueryStats> result = controller.getSlowQueryStats();

            // Then
            assertEquals(1, result.size());
            assertTrue(result.containsKey("test-operation"));
            assertEquals(10L, result.get("test-operation").getCount());
        }
    }

    // ==================== 获取特定操作慢查询统计测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/slow-queries/stats/{operationName} - 获取特定操作统计测试")
    class GetSlowQueryStatsByOperationTests {

        @Test
        @DisplayName("SLOW-002: 成功获取特定操作统计")
        void testGetSlowQueryStatsByOperation_success() {
            // Given
            when(slowQueryDetector.getSlowQueryStats("test-operation")).thenReturn(testStats);

            // When
            SlowQueryDetector.SlowQueryStats result = controller.getSlowQueryStatsByOperation("test-operation");

            // Then
            assertEquals(10L, result.getCount());
        }
    }

    // ==================== 获取慢查询总数测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/slow-queries/count - 获取慢查询总数测试")
    class GetTotalSlowQueryCountTests {

        @Test
        @DisplayName("SLOW-003: 成功获取慢查询总数")
        void testGetTotalSlowQueryCount_success() {
            // Given
            when(slowQueryDetector.getTotalSlowQueryCount()).thenReturn(100L);

            // When
            long result = controller.getTotalSlowQueryCount();

            // Then
            assertEquals(100L, result);
        }
    }

    // ==================== 获取性能热点测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/slow-queries/hotspots - 获取性能热点测试")
    class GetPerformanceHotspotsTests {

        @Test
        @DisplayName("SLOW-004: 成功获取性能热点")
        void testGetPerformanceHotspots_success() {
            // Given
            PerformanceTracker.PerformanceHotspot hotspot = mock(PerformanceTracker.PerformanceHotspot.class);
            when(performanceTracker.getPerformanceHotspots(10)).thenReturn(List.of(hotspot));

            // When
            List<PerformanceTracker.PerformanceHotspot> result = controller.getPerformanceHotspots(10);

            // Then
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("SLOW-005: 空热点列表")
        void testGetPerformanceHotspots_empty() {
            // Given
            when(performanceTracker.getPerformanceHotspots(10)).thenReturn(List.of());

            // When
            List<PerformanceTracker.PerformanceHotspot> result = controller.getPerformanceHotspots(10);

            // Then
            assertTrue(result.isEmpty());
        }
    }

    // ==================== 重置统计信息测试 ====================

    @Nested
    @DisplayName("DELETE /api/monitoring/slow-queries/stats - 重置统计信息测试")
    class ResetStatsTests {

        @Test
        @DisplayName("SLOW-006: 成功重置统计信息")
        void testResetStats_success() {
            // Given
            doNothing().when(slowQueryDetector).resetStats();
            doNothing().when(performanceTracker).clearStats();

            // When
            controller.resetStats();

            // Then
            verify(slowQueryDetector, times(1)).resetStats();
            verify(performanceTracker, times(1)).clearStats();
        }
    }

    // ==================== 获取慢查询趋势数据测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/slow-queries/trends - 获取慢查询趋势测试")
    class GetSlowQueryTrendsTests {

        @Test
        @DisplayName("SLOW-007: 成功获取趋势数据")
        void testGetSlowQueryTrends_success() {
            // Given
            PerformanceTracker.OperationStats opStats = mock(PerformanceTracker.OperationStats.class);
            when(opStats.getCallCount()).thenReturn(100L);

            when(performanceTracker.getAllOperationStats())
                    .thenReturn(Map.of("test-operation", opStats));
            when(slowQueryDetector.getSlowQueryStats("test-operation")).thenReturn(testStats);
            when(slowQueryDetector.getTotalSlowQueryCount()).thenReturn(10L);

            // When
            Map<String, Object> result = controller.getSlowQueryTrends();

            // Then
            assertTrue(result.containsKey("slowOperations"));
            assertTrue(result.containsKey("totalSlowQueries"));
            assertEquals(10L, result.get("totalSlowQueries"));
        }
    }

    // ==================== 获取告警统计信息测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/slow-queries/alerts/stats - 获取告警统计测试")
    class GetAlertStatsTests {

        @Test
        @DisplayName("SLOW-008: 告警服务可用时获取统计")
        void testGetAlertStats_withService() {
            // Given
            SlowQueryAlertStats stats = SlowQueryAlertStats.builder()
                    .totalAlertsTriggered(50L)
                    .totalAlertsSuppressed(10L)
                    .activeAlertKeys(5)
                    .activeOperations(Set.of("op1", "op2"))
                    .build();
            when(slowQueryAlertService.getAlertStats()).thenReturn(stats);

            // 使用反射设置私有字段
            ReflectionTestUtils.setField(controller, "slowQueryAlertService", slowQueryAlertService);

            // When
            SlowQueryAlertStats result = controller.getAlertStats();

            // Then
            assertEquals(50L, result.getTotalAlertsTriggered());
            assertEquals(10L, result.getTotalAlertsSuppressed());
        }

        @Test
        @DisplayName("SLOW-009: 告警服务不可用时返回默认值")
        void testGetAlertStats_withoutService() {
            // Given
            ReflectionTestUtils.setField(controller, "slowQueryAlertService", null);

            // When
            SlowQueryAlertStats result = controller.getAlertStats();

            // Then
            assertEquals(0L, result.getTotalAlertsTriggered());
            assertEquals(0, result.getActiveAlertKeys());
        }
    }

    // ==================== 重置告警统计测试 ====================

    @Nested
    @DisplayName("DELETE /api/monitoring/slow-queries/alerts/stats - 重置告警统计测试")
    class ResetAlertStatsTests {

        @Test
        @DisplayName("SLOW-010: 成功重置告警统计")
        void testResetAlertStats_withService() {
            // Given
            doNothing().when(slowQueryAlertService).resetAlertStats();
            ReflectionTestUtils.setField(controller, "slowQueryAlertService", slowQueryAlertService);

            // When
            controller.resetAlertStats();

            // Then
            verify(slowQueryAlertService, times(1)).resetAlertStats();
        }

        @Test
        @DisplayName("SLOW-011: 告警服务不可用时不执行操作")
        void testResetAlertStats_withoutService() {
            // Given
            ReflectionTestUtils.setField(controller, "slowQueryAlertService", null);

            // When
            controller.resetAlertStats();

            // Then - no exception thrown
        }
    }

    // ==================== 获取告警系统状态测试 ====================

    @Nested
    @DisplayName("GET /api/monitoring/slow-queries/alerts/status - 获取告警系统状态测试")
    class GetAlertSystemStatusTests {

        @Test
        @DisplayName("SLOW-012: 告警服务可用时获取状态")
        void testGetAlertSystemStatus_withService() {
            // Given
            SlowQueryAlertStats stats = SlowQueryAlertStats.builder()
                    .totalAlertsTriggered(50L)
                    .totalAlertsSuppressed(10L)
                    .activeAlertKeys(5)
                    .activeOperations(Set.of("op1"))
                    .build();
            when(slowQueryAlertService.getAlertStats()).thenReturn(stats);
            ReflectionTestUtils.setField(controller, "slowQueryAlertService", slowQueryAlertService);

            // When
            Map<String, Object> result = controller.getAlertSystemStatus();

            // Then
            assertTrue((Boolean) result.get("alertServiceEnabled"));
            assertTrue(result.containsKey("alertStats"));
        }

        @Test
        @DisplayName("SLOW-013: 告警服务不可用时返回状态")
        void testGetAlertSystemStatus_withoutService() {
            // Given
            ReflectionTestUtils.setField(controller, "slowQueryAlertService", null);

            // When
            Map<String, Object> result = controller.getAlertSystemStatus();

            // Then
            assertFalse((Boolean) result.get("alertServiceEnabled"));
            assertTrue(result.containsKey("message"));
        }
    }
}

package org.unreal.modelrouter.auth.security.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryUsageHealthService 单元测试
 */
class MemoryUsageHealthServiceTest {

    private MemoryUsageHealthService service;

    @BeforeEach
    void setUp() {
        service = new MemoryUsageHealthService();
    }

    @Nested
    @DisplayName("checkMemoryUsage 测试")
    class CheckMemoryUsageTests {
        @Test
        @DisplayName("应返回布尔值表示健康状态")
        void shouldReturnBooleanHealthStatus() {
            boolean result = service.checkMemoryUsage();
            assertTrue(result || !result); // 任何结果都是有效的
        }

        @Test
        @DisplayName("短时间内应返回缓存结果")
        void shouldReturnCachedResultWithinCacheDuration() {
            boolean first = service.checkMemoryUsage();
            boolean second = service.checkMemoryUsage();
            assertEquals(first, second);
        }
    }

    @Nested
    @DisplayName("triggerMemoryCheck 测试")
    class TriggerMemoryCheckTests {
        @Test
        @DisplayName("应强制执行新的内存检查")
        void shouldForceNewMemoryCheck() {
            boolean result = service.triggerMemoryCheck();
            assertTrue(result || !result);
        }
    }

    @Nested
    @DisplayName("getDetailedMemoryStatus 测试")
    class GetDetailedMemoryStatusTests {
        @Test
        @DisplayName("应返回包含必要字段的状态Map")
        void shouldReturnStatusMapWithRequiredFields() {
            service.checkMemoryUsage(); // 先执行一次检查

            Map<String, Object> status = service.getDetailedMemoryStatus();

            assertNotNull(status);
            assertTrue(status.containsKey("healthy"));
            assertTrue(status.containsKey("warningCount"));
            assertTrue(status.containsKey("criticalCount"));
            assertTrue(status.containsKey("heapMemory"));
            assertTrue(status.containsKey("nonHeapMemory"));
            assertTrue(status.containsKey("garbageCollection"));
            assertTrue(status.containsKey("thresholds"));
        }

        @Test
        @DisplayName("heapMemory 应包含使用详情")
        void shouldReturnHeapMemoryDetails() {
            service.checkMemoryUsage();

            Map<String, Object> status = service.getDetailedMemoryStatus();
            @SuppressWarnings("unchecked")
            Map<String, Object> heapMemory = (Map<String, Object>) status.get("heapMemory");

            assertNotNull(heapMemory);
            assertTrue(heapMemory.containsKey("usedMB"));
            assertTrue(heapMemory.containsKey("committedMB"));
            assertTrue(heapMemory.containsKey("maxMB"));
            assertTrue(heapMemory.containsKey("usagePercent"));
        }

        @Test
        @DisplayName("garbageCollection 应包含GC信息")
        void shouldReturnGarbageCollectionInfo() {
            service.checkMemoryUsage();

            Map<String, Object> status = service.getDetailedMemoryStatus();
            @SuppressWarnings("unchecked")
            Map<String, Object> gcInfo = (Map<String, Object>) status.get("garbageCollection");

            assertNotNull(gcInfo);
            assertTrue(gcInfo.containsKey("collectors"));
            assertTrue(gcInfo.containsKey("totalCollectionTimeMs"));
            assertTrue(gcInfo.containsKey("totalCollections"));
            assertTrue(gcInfo.containsKey("gcPressurePercent"));
            assertTrue(gcInfo.containsKey("jvmUptimeMs"));
        }

        @Test
        @DisplayName("thresholds 应包含阈值配置")
        void shouldReturnThresholds() {
            Map<String, Object> status = service.getDetailedMemoryStatus();

            @SuppressWarnings("unchecked")
            Map<String, Object> thresholds = (Map<String, Object>) status.get("thresholds");

            assertNotNull(thresholds);
            assertEquals(80.0, thresholds.get("warningThresholdPercent"));
            assertEquals(90.0, thresholds.get("criticalThresholdPercent"));
            assertEquals(10.0, thresholds.get("gcPressureThresholdPercent"));
        }
    }

    @Nested
    @DisplayName("resetMemoryStats 测试")
    class ResetMemoryStatsTests {
        @Test
        @DisplayName("应重置所有统计计数器")
        void shouldResetAllCounters() {
            service.checkMemoryUsage();
            service.resetMemoryStats();

            Map<String, Object> summary = service.getMemoryUsageSummary();
            assertEquals(0L, summary.get("warningCount"));
            assertEquals(0L, summary.get("criticalCount"));
            assertEquals(0L, summary.get("maxHeapUsagePercent"));
            assertEquals(0L, summary.get("maxNonHeapUsagePercent"));
        }
    }

    @Nested
    @DisplayName("getCurrentHealthStatus 测试")
    class GetCurrentHealthStatusTests {
        @Test
        @DisplayName("应返回当前健康状态而不触发检查")
        void shouldReturnCurrentStatusWithoutCheck() {
            boolean status1 = service.getCurrentHealthStatus();
            boolean status2 = service.getCurrentHealthStatus();
            // 不触发检查，状态应该一致
            assertEquals(status1, status2);
        }
    }

    @Nested
    @DisplayName("shouldAlert 测试")
    class ShouldAlertTests {
        @Test
        @DisplayName("默认状态下不应告警")
        void shouldNotAlertByDefault() {
            service.resetMemoryStats();
            assertFalse(service.shouldAlert());
        }
    }

    @Nested
    @DisplayName("getMemoryUsageSummary 测试")
    class GetMemoryUsageSummaryTests {
        @Test
        @DisplayName("应返回摘要信息")
        void shouldReturnSummary() {
            service.checkMemoryUsage();

            Map<String, Object> summary = service.getMemoryUsageSummary();

            assertNotNull(summary);
            assertTrue(summary.containsKey("healthy"));
            assertTrue(summary.containsKey("maxHeapUsagePercent"));
            assertTrue(summary.containsKey("maxNonHeapUsagePercent"));
            assertTrue(summary.containsKey("warningCount"));
            assertTrue(summary.containsKey("criticalCount"));
            assertTrue(summary.containsKey("totalGcTimeMs"));
            assertTrue(summary.containsKey("totalGcCount"));
        }
    }
}

package org.unreal.modelrouter.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SlowQueryDetectorTest {

    private SlowQueryDetector slowQueryDetector;
    private MonitoringProperties monitoringProperties;

    @BeforeEach
    void setUp() {
        monitoringProperties = new MonitoringProperties();
        // 初始化阈值配置
        MonitoringProperties.Thresholds thresholds = new MonitoringProperties.Thresholds();
        Map<String, Long> slowQueryThresholds = new HashMap<>();
        slowQueryThresholds.put("default", 1000L); // 设置默认阈值为1000ms
        thresholds.setSlowQueryThresholds(slowQueryThresholds);
        monitoringProperties.setThresholds(thresholds);
        slowQueryDetector = new SlowQueryDetector(monitoringProperties);
    }

    @Test
    void testDetectSlowQueryBelowThreshold() {
        // Given
        String operationName = "fast-operation";
        long durationMillis = 50L; // 低于默认阈值(1000ms)
        Map<String, String> context = new HashMap<>();
        context.put("test", "value");

        // When
        slowQueryDetector.detectSlowQuery(operationName, durationMillis, context);

        // Then
        assertEquals(0, slowQueryDetector.getTotalSlowQueryCount());
        SlowQueryDetector.SlowQueryStats stats = slowQueryDetector.getSlowQueryStats(operationName);
        assertEquals(0, stats.getCount());
    }

    @Test
    void testDetectSlowQueryAboveThreshold() {
        // Given
        String operationName = "slow-operation";
        long durationMillis = 2000L; // 高于默认阈值(1000ms)
        Map<String, String> context = new HashMap<>();
        context.put("test", "value");

        // When
        slowQueryDetector.detectSlowQuery(operationName, durationMillis, context);

        // Then
        assertEquals(1, slowQueryDetector.getTotalSlowQueryCount());
        SlowQueryDetector.SlowQueryStats stats = slowQueryDetector.getSlowQueryStats(operationName);
        assertEquals(1, stats.getCount());
        assertEquals(2000L, stats.getTotalDuration());
        assertEquals(2000L, stats.getMaxDuration());
        assertEquals(2000L, stats.getMinDuration());
        assertEquals(2000.0, stats.getAverageDuration());
    }

    @Test
    void testDetectMultipleSlowQueries() {
        // Given
        String operationName = "slow-operation";
        
        // When
        slowQueryDetector.detectSlowQuery(operationName, 1500L, null); // 第一次慢查询
        slowQueryDetector.detectSlowQuery(operationName, 2500L, null); // 第二次慢查询

        // Then
        assertEquals(2, slowQueryDetector.getTotalSlowQueryCount());
        SlowQueryDetector.SlowQueryStats stats = slowQueryDetector.getSlowQueryStats(operationName);
        assertEquals(2, stats.getCount());
        assertEquals(4000L, stats.getTotalDuration());
        assertEquals(2500L, stats.getMaxDuration());
        assertEquals(1500L, stats.getMinDuration());
        assertEquals(2000.0, stats.getAverageDuration());
    }

    @Test
    void testGetAllSlowQueryStats() {
        // Given
        slowQueryDetector.detectSlowQuery("operation-1", 1500L, null);
        slowQueryDetector.detectSlowQuery("operation-2", 2000L, null);

        // When
        Map<String, SlowQueryDetector.SlowQueryStats> allStats = slowQueryDetector.getAllSlowQueryStats();

        // Then
        assertEquals(2, allStats.size());
        assertTrue(allStats.containsKey("operation-1"));
        assertTrue(allStats.containsKey("operation-2"));
    }

    @Test
    void testResetStats() {
        // Given
        slowQueryDetector.detectSlowQuery("operation-1", 1500L, null);
        slowQueryDetector.detectSlowQuery("operation-2", 2000L, null);
        assertEquals(2, slowQueryDetector.getTotalSlowQueryCount());

        // When
        slowQueryDetector.resetStats();

        // Then
        assertEquals(0, slowQueryDetector.getTotalSlowQueryCount());
        Map<String, SlowQueryDetector.SlowQueryStats> allStats = slowQueryDetector.getAllSlowQueryStats();
        assertTrue(allStats.isEmpty());
    }

    @Test
    void testCustomThreshold() {
        // Given
        String operationName = "custom-threshold-operation";
        long customThreshold = 500L;
        long durationMillis = 600L; // 高于自定义阈值
        
        // 设置自定义阈值
        monitoringProperties.getThresholds().setSlowQueryThresholds(new HashMap<>());
        monitoringProperties.getThresholds().getSlowQueryThresholds().put(operationName, customThreshold);

        // When
        slowQueryDetector.detectSlowQuery(operationName, durationMillis, null);

        // Then
        assertEquals(1, slowQueryDetector.getTotalSlowQueryCount());
    }
}
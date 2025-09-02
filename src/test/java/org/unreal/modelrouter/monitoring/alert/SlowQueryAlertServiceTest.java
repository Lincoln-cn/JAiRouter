package org.unreal.modelrouter.monitoring.alert;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.SlowQueryDetector;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 慢查询告警服务测试类
 */
class SlowQueryAlertServiceTest {
    
    @Mock
    private MonitoringProperties monitoringProperties;
    
    @Mock
    private StructuredLogger structuredLogger;
    
    @Mock
    private SlowQueryDetector slowQueryDetector;
    
    @Mock
    private TracingContext tracingContext;
    
    private MeterRegistry meterRegistry;
    private SlowQueryAlertService alertService;
    private SlowQueryAlertProperties alertProperties;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 创建真实的MeterRegistry
        meterRegistry = new SimpleMeterRegistry();
        
        // 设置告警配置属性
        alertProperties = new SlowQueryAlertProperties();
        alertProperties.setEnabled(true);
        
        // 设置全局配置
        SlowQueryAlertProperties.GlobalConfig globalConfig = new SlowQueryAlertProperties.GlobalConfig();
        globalConfig.setEnabled(true);
        globalConfig.setMinIntervalMs(1000L); // 1秒，便于测试
        globalConfig.setMinOccurrences(1L); // 1次，便于测试
        globalConfig.setEnabledSeverities(Set.of("critical", "warning", "info"));
        alertProperties.setGlobal(globalConfig);
        
        // 创建告警服务
        alertService = new SlowQueryAlertService(
                monitoringProperties,
                alertProperties,
                structuredLogger,
                meterRegistry
        );
        
        // 设置Mock行为
        when(tracingContext.getTraceId()).thenReturn("test-trace-id");
        when(tracingContext.getSpanId()).thenReturn("test-span-id");
        
        // 模拟慢查询统计
        SlowQueryDetector.SlowQueryStats stats = mock(SlowQueryDetector.SlowQueryStats.class);
        when(stats.getCount()).thenReturn(5L);
        when(stats.getAverageDuration()).thenReturn(2000.0);
        when(stats.getMaxDuration()).thenReturn(3000L);
        when(slowQueryDetector.getSlowQueryStats(anyString())).thenReturn(stats);
    }
    
    @Test
    void testCheckAndAlert_ShouldTriggerAlert_WhenSlowQueryDetected() {
        // Given
        String operationName = "test-operation";
        long durationMillis = 5000L; // 5秒
        long threshold = 1000L; // 1秒阈值
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("test", "value");
        
        // When
        alertService.checkAndAlert(operationName, durationMillis, threshold, tracingContext, additionalInfo);
        
        // Then
        // 验证结构化日志被调用
        verify(structuredLogger, times(2)).logBusinessEvent(
                anyString(), 
                any(Map.class), 
                eq(tracingContext)
        );
        
        // 验证指标被记录
        assertNotNull(meterRegistry.find("slow_query_total").counter());
        assertNotNull(meterRegistry.find("slow_query_duration").timer());
        assertNotNull(meterRegistry.find("slow_query_alert_triggered").counter());
        
        // 验证告警统计
        SlowQueryAlertStats stats = alertService.getAlertStats();
        assertEquals(1L, stats.getTotalAlertsTriggered());
        assertEquals(0L, stats.getTotalAlertsSuppressed());
        assertEquals(1, stats.getActiveAlertKeys());
    }
    
    @Test
    void testCheckAndAlert_ShouldNotTriggerAlert_WhenBelowThreshold() {
        // Given
        String operationName = "fast-operation";
        long durationMillis = 500L; // 0.5秒
        long threshold = 1000L; // 1秒阈值
        
        // When
        alertService.checkAndAlert(operationName, durationMillis, threshold, tracingContext, null);
        
        // Then
        // 验证没有触发告警
        verify(structuredLogger, never()).logBusinessEvent(
                eq("slow_query_alert_triggered"), 
                any(Map.class), 
                any(TracingContext.class)
        );
        
        // 验证告警统计
        SlowQueryAlertStats stats = alertService.getAlertStats();
        assertEquals(0L, stats.getTotalAlertsTriggered());
    }
    
    @Test
    void testCheckAndAlert_ShouldSuppressAlert_WhenWithinMinInterval() throws InterruptedException {
        // Given
        String operationName = "frequent-slow-operation";
        long durationMillis = 5000L;
        long threshold = 1000L;
        
        // When - 第一次告警
        alertService.checkAndAlert(operationName, durationMillis, threshold, tracingContext, null);
        
        // 立即再次触发（在最小间隔内）
        alertService.checkAndAlert(operationName, durationMillis, threshold, tracingContext, null);
        
        // Then
        SlowQueryAlertStats stats = alertService.getAlertStats();
        assertEquals(1L, stats.getTotalAlertsTriggered()); // 只应该触发一次
        assertEquals(1L, stats.getTotalAlertsSuppressed()); // 第二次被抑制
    }
    
    @Test
    void testCheckAndAlert_ShouldTriggerAlert_AfterMinInterval() throws InterruptedException {
        // 修改配置，设置更短的最小间隔
        SlowQueryAlertProperties.GlobalConfig globalConfig = alertProperties.getGlobal();
        globalConfig.setMinIntervalMs(100L); // 100毫秒
        
        // Given
        String operationName = "interval-test-operation";
        long durationMillis = 5000L;
        long threshold = 1000L;
        
        // When - 第一次告警
        alertService.checkAndAlert(operationName, durationMillis, threshold, tracingContext, null);
        
        // 等待超过最小间隔
        Thread.sleep(150L);
        
        // 再次触发
        alertService.checkAndAlert(operationName, durationMillis, threshold, tracingContext, null);
        
        // Then
        SlowQueryAlertStats stats = alertService.getAlertStats();
        assertEquals(2L, stats.getTotalAlertsTriggered()); // 应该触发两次
        assertEquals(0L, stats.getTotalAlertsSuppressed()); // 没有被抑制
    }
    
    @Test
    void testGetSeverityLevel() throws Exception {
        // 使用反射访问私有方法进行测试
        java.lang.reflect.Method getSeverityLevel = SlowQueryAlertService.class
                .getDeclaredMethod("getSeverityLevel", long.class, long.class);
        getSeverityLevel.setAccessible(true);
        
        // Test critical level (>= 5x threshold)
        String severity1 = (String) getSeverityLevel.invoke(alertService, 5000L, 1000L);
        assertEquals("critical", severity1);
        
        // Test warning level (>= 3x threshold)
        String severity2 = (String) getSeverityLevel.invoke(alertService, 3000L, 1000L);
        assertEquals("warning", severity2);
        
        // Test info level (< 3x threshold)
        String severity3 = (String) getSeverityLevel.invoke(alertService, 2000L, 1000L);
        assertEquals("info", severity3);
    }
    
    @Test
    void testResetAlertStats() {
        // Given - 先触发一些告警
        alertService.checkAndAlert("test-op", 5000L, 1000L, tracingContext, null);
        
        // When
        alertService.resetAlertStats();
        
        // Then
        SlowQueryAlertStats stats = alertService.getAlertStats();
        assertEquals(0L, stats.getTotalAlertsTriggered());
        assertEquals(0L, stats.getTotalAlertsSuppressed());
        assertEquals(0, stats.getActiveAlertKeys());
    }
    
    @Test
    void testAlertStatsCalculations() {
        // Given - 触发一些告警和抑制
        alertService.checkAndAlert("op1", 5000L, 1000L, tracingContext, null);
        alertService.checkAndAlert("op1", 5000L, 1000L, tracingContext, null); // 被抑制
        alertService.checkAndAlert("op2", 5000L, 1000L, tracingContext, null);
        
        // When
        SlowQueryAlertStats stats = alertService.getAlertStats();
        
        // Then
        assertEquals(2L, stats.getTotalAlertsTriggered());
        assertEquals(1L, stats.getTotalAlertsSuppressed());
        assertEquals(2, stats.getActiveAlertKeys());
        assertEquals(Set.of("op1", "op2"), stats.getActiveOperations());
        
        // 验证计算方法
        assertEquals(2.0/3.0, stats.getAlertTriggerRate(), 0.001);
        assertEquals(1.0/3.0, stats.getAlertSuppressionRate(), 0.001);
        assertEquals(1.0, stats.getAverageAlertsPerOperation(), 0.001);
    }
}
package org.unreal.modelrouter.monitoring.error;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * ErrorMetricsCollector 单元测试
 */
class ErrorMetricsCollectorTest {

    private ErrorMetricsCollector metricsCollector;
    private MeterRegistry meterRegistry;
    private ErrorTrackerProperties properties;
    
    @Mock
    private ErrorTracker errorTracker;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        meterRegistry = new SimpleMeterRegistry();
        properties = new ErrorTrackerProperties();
        
        metricsCollector = new ErrorMetricsCollector(meterRegistry, errorTracker, properties);
    }

    @Test
    void testRecordError() {
        // Given
        String errorType = "RuntimeException";
        String operation = "test-operation";
        Duration duration = Duration.ofMillis(100);

        // When
        metricsCollector.recordError(errorType, operation, duration);

        // Then
        // 验证总错误计数器被创建并递增
        assertNotNull(meterRegistry.find("jairouter.errors.total").counter());
        assertEquals(1.0, meterRegistry.find("jairouter.errors.total").counter().count());
        
        // 验证按错误类型分组的计数器被创建并递增
        assertNotNull(meterRegistry.find("jairouter.errors.by_type").tag("error_type", errorType).counter());
        assertEquals(1.0, meterRegistry.find("jairouter.errors.by_type").tag("error_type", errorType).counter().count());
        
        // 验证按操作分组的计数器被创建并递增
        assertNotNull(meterRegistry.find("jairouter.errors.by_operation").tag("operation", operation).counter());
        assertEquals(1.0, meterRegistry.find("jairouter.errors.by_operation").tag("operation", operation).counter().count());
        
        // 验证错误处理耗时计时器被创建并记录
        assertNotNull(meterRegistry.find("jairouter.errors.duration").timer());
        assertEquals(1, meterRegistry.find("jairouter.errors.duration").timer().count());
    }

    @Test
    void testRecordErrorWithoutDuration() {
        // Given
        String errorType = "IllegalArgumentException";
        String operation = "validation";

        // When
        metricsCollector.recordError(errorType, operation);

        // Then
        // 验证错误被记录但没有耗时信息
        assertEquals(1.0, meterRegistry.find("jairouter.errors.total").counter().count());
        assertEquals(1.0, meterRegistry.find("jairouter.errors.by_type").tag("error_type", errorType).counter().count());
        assertEquals(1.0, meterRegistry.find("jairouter.errors.by_operation").tag("operation", operation).counter().count());
    }

    @Test
    void testRecordMultipleErrors() {
        // Given
        String errorType1 = "RuntimeException";
        String errorType2 = "IllegalArgumentException";
        String operation = "test-operation";

        // When
        metricsCollector.recordError(errorType1, operation);
        metricsCollector.recordError(errorType2, operation);
        metricsCollector.recordError(errorType1, operation);

        // Then
        // 验证总错误计数
        assertEquals(3.0, meterRegistry.find("jairouter.errors.total").counter().count());
        
        // 验证按错误类型分组的计数
        assertEquals(2.0, meterRegistry.find("jairouter.errors.by_type").tag("error_type", errorType1).counter().count());
        assertEquals(1.0, meterRegistry.find("jairouter.errors.by_type").tag("error_type", errorType2).counter().count());
        
        // 验证按操作分组的计数
        assertEquals(3.0, meterRegistry.find("jairouter.errors.by_operation").tag("operation", operation).counter().count());
    }

    @Test
    void testMetricsDisabled() {
        // Given
        properties.getMetrics().setEnabled(false);
        metricsCollector = new ErrorMetricsCollector(meterRegistry, errorTracker, properties);

        // When
        metricsCollector.recordError("TestException", "test-operation");

        // Then
        // 验证新的指标未被记录（但之前的指标仍在）
        // 检查是否有新的错误指标
        long totalCount = meterRegistry.find("jairouter.errors.total").counter() != null ? 
                         (long) meterRegistry.find("jairouter.errors.total").counter().count() : 0;
        assertEquals(0, totalCount, "在禁用指标的情况下，不应记录新的错误");
    }

    @Test
    void testGroupByErrorTypeDisabled() {
        // Given
        properties.getMetrics().setGroupByErrorType(false);
        metricsCollector = new ErrorMetricsCollector(meterRegistry, errorTracker, properties);

        // When
        metricsCollector.recordError("TestException", "test-operation");

        // Then
        // 验证总错误计数器存在
        assertNotNull(meterRegistry.find("jairouter.errors.total").counter());
        
        // 验证按错误类型分组的计数器不存在
        assertNull(meterRegistry.find("jairouter.errors.by_type").counter());
    }

    @Test
    void testGroupByOperationDisabled() {
        // Given
        properties.getMetrics().setGroupByOperation(false);
        metricsCollector = new ErrorMetricsCollector(meterRegistry, errorTracker, properties);

        // When
        metricsCollector.recordError("TestException", "test-operation");

        // Then
        // 验证总错误计数器存在
        assertNotNull(meterRegistry.find("jairouter.errors.total").counter());
        
        // 验证按操作分组的计数器不存在
        assertNull(meterRegistry.find("jairouter.errors.by_operation").counter());
    }

    @Test
    void testGetErrorMetricsStats() {
        // Given
        Map<String, Long> errorTypeStats = Map.of("RuntimeException", 5L, "IllegalArgumentException", 3L);
        Map<String, Long> errorLocationStats = Map.of("operation1", 4L, "operation2", 4L);
        
        when(errorTracker.getErrorTypeStatistics()).thenReturn(errorTypeStats);
        when(errorTracker.getErrorLocationStatistics()).thenReturn(errorLocationStats);

        // 记录一些错误以创建指标
        metricsCollector.recordError("TestException", "test-operation", Duration.ofMillis(50));

        // When
        ErrorMetricsCollector.ErrorMetricsStats stats = metricsCollector.getErrorMetricsStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.getTotalErrorCounters() > 0);
        assertTrue(stats.getTotalErrorTimers() > 0);
        assertEquals(errorTypeStats, stats.getErrorTypeStats());
        assertEquals(errorLocationStats, stats.getErrorLocationStats());
    }

    @Test
    void testCustomCounterPrefix() {
        // Given
        properties.getMetrics().setCounterPrefix("custom.errors");
        metricsCollector = new ErrorMetricsCollector(meterRegistry, errorTracker, properties);

        // When
        metricsCollector.recordError("TestException", "test-operation");

        // Then
        // 验证自定义前缀的计数器被创建
        assertNotNull(meterRegistry.find("custom.errors.total").counter());
        assertNotNull(meterRegistry.find("custom.errors.by_type").counter());
        assertNotNull(meterRegistry.find("custom.errors.by_operation").counter());
    }
}
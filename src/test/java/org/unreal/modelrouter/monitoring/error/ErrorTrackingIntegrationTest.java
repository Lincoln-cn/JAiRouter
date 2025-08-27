package org.unreal.modelrouter.monitoring.error;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 错误追踪系统集成测试
 */
@ExtendWith(MockitoExtension.class)
class ErrorTrackingIntegrationTest {

    @Mock
    private StructuredLogger structuredLogger;
    
    @Mock
    private TracingContext tracingContext;

    private ErrorTracker errorTracker;
    private StackTraceSanitizer stackTraceSanitizer;
    private ErrorMetricsCollector errorMetricsCollector;
    private MeterRegistry meterRegistry;
    private ErrorTrackerProperties properties;

    @BeforeEach
    void setUp() {
        // 初始化配置
        properties = new ErrorTrackerProperties();
        
        // 初始化指标注册表
        meterRegistry = new SimpleMeterRegistry();
        
        // 创建脱敏器
        stackTraceSanitizer = new StackTraceSanitizer(properties.getSanitization());
        
        // 创建错误追踪器
        errorTracker = new ErrorTracker(structuredLogger);
        
        // 创建指标收集器
        errorMetricsCollector = new ErrorMetricsCollector(meterRegistry, errorTracker, properties);
        
        // 通过反射设置可选依赖
        setPrivateField(errorTracker, "stackTraceSanitizer", stackTraceSanitizer);
        setPrivateField(errorTracker, "errorMetricsCollector", errorMetricsCollector);
    }

    @Test
    void testCompleteErrorTrackingFlow() {
        // Given
        RuntimeException exception = new RuntimeException("Database connection failed: password=secret123");
        String operation = "database-query";
        Map<String, Object> additionalInfo = Map.of(
            "table", "users",
            "query", "SELECT * FROM users"
        );

        // Mock追踪上下文
        try (var mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            when(tracingContext.isActive()).thenReturn(true);
            when(tracingContext.getTraceId()).thenReturn("test-trace-id");

            // When
            errorTracker.trackError(exception, operation, additionalInfo);

            // Then
            // 验证结构化日志被调用
            verify(structuredLogger, times(1)).logError(eq(exception), eq(tracingContext), anyMap());
            verify(structuredLogger, times(1)).logBusinessEvent(eq("error_aggregation"), anyMap(), eq(tracingContext));

            // 验证错误统计
            Map<String, Long> errorTypeStats = errorTracker.getErrorTypeStatistics();
            assertEquals(1L, errorTypeStats.get("java.lang.RuntimeException"));

            Map<String, Long> errorLocationStats = errorTracker.getErrorLocationStatistics();
            assertEquals(1L, errorLocationStats.get(operation));

            // 验证错误聚合
            Map<String, ErrorTracker.ErrorAggregation> aggregations = errorTracker.getErrorAggregations();
            assertEquals(1, aggregations.size());
            assertTrue(aggregations.containsKey("java.lang.RuntimeException:" + operation));

            // 验证指标被记录
            assertNotNull(meterRegistry.find("jairouter.errors.total").counter());
            assertEquals(1.0, meterRegistry.find("jairouter.errors.total").counter().count());
            
            assertNotNull(meterRegistry.find("jairouter.errors.by_type").tag("error_type", "RuntimeException").counter());
            assertEquals(1.0, meterRegistry.find("jairouter.errors.by_type").tag("error_type", "RuntimeException").counter().count());
        }
    }

    @Test
    void testErrorTrackingWithSensitiveInformation() {
        // Given
        RuntimeException exception = new RuntimeException("Authentication failed: token=Bearer abc123xyz password=secret");
        String operation = "authentication";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        // 验证脱敏功能正常工作
        verify(structuredLogger).logError(eq(exception), any(), argThat(errorInfo -> {
            // 检查是否包含脱敏后的信息
            return errorInfo.containsKey("sanitizedMessage") && 
                   errorInfo.get("sanitizedMessage").toString().contains("***");
        }));
    }

    @Test
    void testErrorTrackingWithStackTrace() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        StackTraceElement[] stackTrace = {
            new StackTraceElement("org.unreal.modelrouter.security.AuthService", "authenticate", "AuthService.java", 123),
            new StackTraceElement("org.unreal.modelrouter.controller.TestController", "test", "TestController.java", 456),
            new StackTraceElement("sun.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 62)
        };
        exception.setStackTrace(stackTrace);
        String operation = "stack-trace-test";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        // 验证脱敏后的堆栈跟踪被正确处理
        verify(structuredLogger).logError(eq(exception), any(), argThat(errorInfo -> {
            return errorInfo.containsKey("sanitizedStackTrace") &&
                   errorInfo.get("sanitizedStackTrace").toString().contains("RuntimeException");
        }));
    }

    @Test
    void testMultipleErrorsStatistics() {
        // Given
        RuntimeException runtimeException = new RuntimeException("Runtime error");
        IllegalArgumentException illegalArgException = new IllegalArgumentException("Illegal argument");
        String operation1 = "operation1";
        String operation2 = "operation2";

        // When
        errorTracker.trackError(runtimeException, operation1);
        errorTracker.trackError(illegalArgException, operation1);
        errorTracker.trackError(runtimeException, operation2);

        // Then
        // 验证错误类型统计
        Map<String, Long> errorTypeStats = errorTracker.getErrorTypeStatistics();
        assertEquals(2L, errorTypeStats.get("java.lang.RuntimeException"));
        assertEquals(1L, errorTypeStats.get("java.lang.IllegalArgumentException"));

        // 验证错误位置统计
        Map<String, Long> errorLocationStats = errorTracker.getErrorLocationStatistics();
        assertEquals(2L, errorLocationStats.get(operation1));
        assertEquals(1L, errorLocationStats.get(operation2));

        // 验证指标记录
        assertEquals(3.0, meterRegistry.find("jairouter.errors.total").counter().count());
    }

    @Test
    void testErrorTrackingWithoutTracingContext() {
        // Given
        RuntimeException exception = new RuntimeException("Test without tracing");
        String operation = "no-tracing-test";

        // Mock no tracing context
        try (var mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(null);

            // When
            errorTracker.trackError(exception, operation);

            // Then
            // 验证即使没有追踪上下文也能正常工作
            verify(structuredLogger, times(1)).logError(eq(exception), isNull(), anyMap());
            verify(structuredLogger, times(1)).logBusinessEvent(eq("error_aggregation"), anyMap(), isNull());

            // 验证统计信息正常更新
            assertEquals(1L, errorTracker.getErrorTypeStatistics().get("java.lang.RuntimeException"));
        }
    }

    @Test
    void testErrorMetricsCollectorIntegration() {
        // Given
        RuntimeException exception = new RuntimeException("Metrics test");
        String operation = "metrics-test";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        // 验证指标被正确记录
        ErrorMetricsCollector.ErrorMetricsStats stats = errorMetricsCollector.getErrorMetricsStats();
        assertNotNull(stats);
        assertTrue(stats.getTotalErrorCounters() > 0);
        assertTrue(stats.getTotalErrorTimers() > 0);
        assertNotNull(stats.getErrorTypeStats());
        assertNotNull(stats.getErrorLocationStats());
    }

    @Test
    void testDisabledSanitization() {
        // Given
        properties.getSanitization().setEnabled(false);
        stackTraceSanitizer = new StackTraceSanitizer(properties.getSanitization());
        setPrivateField(errorTracker, "stackTraceSanitizer", stackTraceSanitizer);

        RuntimeException exception = new RuntimeException("Test with password=secret");
        String operation = "disabled-sanitization-test";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        // 验证脱敏被禁用时，敏感信息仍然存在
        verify(structuredLogger).logError(eq(exception), any(), argThat(errorInfo -> {
            Object sanitizedMessage = errorInfo.get("sanitizedMessage");
            return sanitizedMessage != null && sanitizedMessage.toString().contains("password=secret");
        }));
    }

    /**
     * 使用反射设置私有字段
     */
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set private field: " + fieldName, e);
        }
    }
}
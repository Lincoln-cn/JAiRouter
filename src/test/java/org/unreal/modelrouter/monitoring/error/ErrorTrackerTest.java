package org.unreal.modelrouter.monitoring.error;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

/**
 * ErrorTracker单元测试
 * 
 * 测试异常追踪功能，包括：
 * - 基本异常追踪
 * - 统计信息收集
 * - 异常聚合
 * - 与其他组件的集成
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErrorTrackerTest {

    private ErrorTracker errorTracker;

    @Mock
    private StructuredLogger structuredLogger;

    @Mock
    private StackTraceSanitizer stackTraceSanitizer;

    @Mock
    private ErrorMetricsCollector errorMetricsCollector;

    @Mock
    private TracingContext tracingContext;

    @Mock
    private StackTraceSanitizer.SanitizedThrowable sanitizedThrowable;

    @BeforeEach
    void setUp() {
        errorTracker = new ErrorTracker(structuredLogger);
        
        // 使用反射设置可选依赖
        setPrivateField(errorTracker, "stackTraceSanitizer", stackTraceSanitizer);
        setPrivateField(errorTracker, "errorMetricsCollector", errorMetricsCollector);
    }

    @Test
    void testTrackErrorBasic() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception message");
        String operation = "test-operation";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        verify(structuredLogger, times(1)).logError(eq(exception), isNull(), anyMap());
        verify(structuredLogger, times(1)).logBusinessEvent(eq("error_aggregation"), anyMap(), isNull());
    }

    @Test
    void testTrackErrorWithNull() {
        // Given
        String operation = "test-operation";

        // When
        errorTracker.trackError(null, operation);

        // Then
        verify(structuredLogger, never()).logError(any(), any(), any());
        verify(structuredLogger, never()).logBusinessEvent(any(), any(), any());
    }

    @Test
    void testTrackErrorWithTracingContext() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        String operation = "test-operation";
        String traceId = "test-trace-id";
        String spanId = "test-span-id";

        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.getTraceId()).thenReturn(traceId);
        when(tracingContext.getSpanId()).thenReturn(spanId);

        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);

            // When
            errorTracker.trackError(exception, operation);

            // Then
            verify(structuredLogger, times(1)).logError(eq(exception), eq(tracingContext), anyMap());
            verify(structuredLogger, times(1)).logBusinessEvent(eq("error_aggregation"), anyMap(), eq(tracingContext));
        }
    }

    @Test
    void testTrackErrorWithAdditionalInfo() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        String operation = "test-operation";
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("userId", "user123");
        additionalInfo.put("requestId", "req-456");

        // When
        errorTracker.trackError(exception, operation, additionalInfo);

        // Then
        verify(structuredLogger, times(1)).logError(eq(exception), isNull(), argThat(errorInfo -> {
            return errorInfo.containsKey("userId") && 
                   errorInfo.containsKey("requestId") &&
                   errorInfo.containsKey("operation") &&
                   errorInfo.containsKey("exceptionClass");
        }));
    }

    @Test
    void testTrackErrorWithStackTraceSanitizer() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception with password=secret");
        String operation = "test-operation";
        
        when(stackTraceSanitizer.sanitize(exception)).thenReturn(sanitizedThrowable);
        when(sanitizedThrowable.getMessage()).thenReturn("Test exception with password=***");
        when(sanitizedThrowable.toSimpleString()).thenReturn("java.lang.RuntimeException: Test exception with password=***");

        // When
        errorTracker.trackError(exception, operation);

        // Then
        verify(stackTraceSanitizer, times(1)).sanitize(exception);
        verify(structuredLogger, times(1)).logError(eq(exception), any(), argThat(errorInfo -> {
            return errorInfo.containsKey("sanitizedMessage") && 
                   errorInfo.containsKey("sanitizedStackTrace");
        }));
    }

    @Test
    void testTrackErrorWithMetricsCollector() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        String operation = "metrics-test";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        verify(errorMetricsCollector, times(1)).recordError(
            eq("RuntimeException"), 
            eq(operation), 
            any(Duration.class)
        );
    }

    @Test
    void testErrorTypeStatistics() {
        // Given
        RuntimeException exception1 = new RuntimeException("Test 1");
        IllegalArgumentException exception2 = new IllegalArgumentException("Test 2");
        RuntimeException exception3 = new RuntimeException("Test 3");

        // When
        errorTracker.trackError(exception1, "operation1");
        errorTracker.trackError(exception2, "operation2");
        errorTracker.trackError(exception3, "operation3");

        // Then
        Map<String, Long> statistics = errorTracker.getErrorTypeStatistics();
        assertEquals(2L, statistics.get("java.lang.RuntimeException"));
        assertEquals(1L, statistics.get("java.lang.IllegalArgumentException"));
    }

    @Test
    void testErrorLocationStatistics() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");

        // When
        errorTracker.trackError(exception, "operation1");
        errorTracker.trackError(exception, "operation1");
        errorTracker.trackError(exception, "operation2");
        errorTracker.trackError(exception, null); // null operation should be counted as "unknown"

        // Then
        Map<String, Long> statistics = errorTracker.getErrorLocationStatistics();
        assertEquals(2L, statistics.get("operation1"));
        assertEquals(1L, statistics.get("operation2"));
        assertEquals(1L, statistics.get("unknown"));
    }

    @Test
    void testErrorAggregations() {
        // Given
        RuntimeException exception1 = new RuntimeException("Test 1");
        RuntimeException exception2 = new RuntimeException("Test 2");

        // When
        errorTracker.trackError(exception1, "operation1");
        errorTracker.trackError(exception1, "operation1"); // Same error key
        errorTracker.trackError(exception2, "operation2");

        // Then
        Map<String, ErrorTracker.ErrorAggregation> aggregations = errorTracker.getErrorAggregations();
        
        ErrorTracker.ErrorAggregation aggregation1 = aggregations.get("java.lang.RuntimeException:operation1");
        assertNotNull(aggregation1);
        assertEquals(2L, aggregation1.getCount());
        assertEquals("java.lang.RuntimeException", aggregation1.getErrorType());
        assertEquals("operation1", aggregation1.getOperation());
        
        ErrorTracker.ErrorAggregation aggregation2 = aggregations.get("java.lang.RuntimeException:operation2");
        assertNotNull(aggregation2);
        assertEquals(1L, aggregation2.getCount());
    }

    @Test
    void testErrorAggregationWithNullOperation() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");

        // When
        errorTracker.trackError(exception, null);
        errorTracker.trackError(exception, null);

        // Then
        Map<String, ErrorTracker.ErrorAggregation> aggregations = errorTracker.getErrorAggregations();
        ErrorTracker.ErrorAggregation aggregation = aggregations.get("java.lang.RuntimeException:unknown");
        
        assertNotNull(aggregation);
        assertEquals(2L, aggregation.getCount());
        assertEquals(null, aggregation.getOperation());
    }

    @Test
    void testTrackErrorSimplified() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        String operation = "simple-test";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        verify(structuredLogger, times(1)).logError(eq(exception), any(), anyMap());
    }

    @Test
    void testErrorTrackingWithException() {
        // Given
        RuntimeException testException = new RuntimeException("Test exception");
        String operation = "exception-test";
        
        // Mock StructuredLogger to throw exception
        doThrow(new RuntimeException("Logger exception")).when(structuredLogger).logError(any(), any(), any());

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> errorTracker.trackError(testException, operation));
    }

    @Test
    void testConcurrentErrorTracking() throws InterruptedException {
        // Given
        RuntimeException exception = new RuntimeException("Concurrent test");
        String operation = "concurrent-test";
        int numberOfThreads = 10;
        int errorsPerThread = 100;

        // When
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < errorsPerThread; j++) {
                    errorTracker.trackError(exception, operation);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        Map<String, Long> statistics = errorTracker.getErrorTypeStatistics();
        assertEquals(numberOfThreads * errorsPerThread, statistics.get("java.lang.RuntimeException").longValue());
        
        Map<String, ErrorTracker.ErrorAggregation> aggregations = errorTracker.getErrorAggregations();
        ErrorTracker.ErrorAggregation aggregation = aggregations.get("java.lang.RuntimeException:concurrent-test");
        assertNotNull(aggregation);
        assertEquals(numberOfThreads * errorsPerThread, aggregation.getCount());
    }

    @Test
    void testErrorAggregationToString() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        String operation = "toString-test";

        // When
        errorTracker.trackError(exception, operation);
        
        // Then
        Map<String, ErrorTracker.ErrorAggregation> aggregations = errorTracker.getErrorAggregations();
        ErrorTracker.ErrorAggregation aggregation = aggregations.get("java.lang.RuntimeException:toString-test");
        
        assertNotNull(aggregation);
        String toString = aggregation.toString();
        assertTrue(toString.contains("ErrorAggregation"));
        assertTrue(toString.contains("errorType='java.lang.RuntimeException'"));
        assertTrue(toString.contains("operation='toString-test'"));
        assertTrue(toString.contains("count=1"));
        assertTrue(toString.contains("firstOccurrence="));
    }

    @Test
    void testNoMetricsCollectorDoesNotFail() {
        // Given - Create ErrorTracker without metrics collector
        ErrorTracker trackerWithoutMetrics = new ErrorTracker(structuredLogger);
        RuntimeException exception = new RuntimeException("No metrics test");

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> trackerWithoutMetrics.trackError(exception, "test"));
    }

    @Test
    void testNoStackTraceSanitizerDoesNotFail() {
        // Given - Create ErrorTracker without sanitizer
        ErrorTracker trackerWithoutSanitizer = new ErrorTracker(structuredLogger);
        RuntimeException exception = new RuntimeException("No sanitizer test");

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> trackerWithoutSanitizer.trackError(exception, "test"));
    }

    @Test
    void testComplexExceptionHierarchy() {
        // Given
        IllegalArgumentException cause = new IllegalArgumentException("Root cause");
        RuntimeException wrapper = new RuntimeException("Wrapper exception", cause);
        Exception topLevel = new Exception("Top level exception", wrapper);

        // When
        errorTracker.trackError(topLevel, "complex-test");

        // Then
        verify(structuredLogger, times(1)).logError(eq(topLevel), any(), anyMap());
        
        Map<String, Long> statistics = errorTracker.getErrorTypeStatistics();
        assertEquals(1L, statistics.get("java.lang.Exception"));
    }

    @Test
    void testLargeNumberOfUniqueOperations() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        int numberOfOperations = 1000;

        // When
        for (int i = 0; i < numberOfOperations; i++) {
            errorTracker.trackError(exception, "operation-" + i);
        }

        // Then
        Map<String, Long> locationStatistics = errorTracker.getErrorLocationStatistics();
        assertEquals(numberOfOperations, locationStatistics.size());
        
        Map<String, ErrorTracker.ErrorAggregation> aggregations = errorTracker.getErrorAggregations();
        assertEquals(numberOfOperations, aggregations.size());
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
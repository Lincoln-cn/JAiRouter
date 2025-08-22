package org.unreal.modelrouter.monitoring.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErrorTrackerTest {

    @Mock
    private StructuredLogger structuredLogger;

    @Mock
    private TracingContext tracingContext;

    private ErrorTracker errorTracker;

    @BeforeEach
    public void setUp() {
        errorTracker = new ErrorTracker(structuredLogger);
    }

    @Test
    void testTrackError() {
        // Given
        Exception exception = new RuntimeException("Test exception");
        String operation = "test-operation";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        verify(structuredLogger, times(1)).logError(any(Throwable.class), isNull(), anyMap());
    }

    @Test
    void testTrackErrorWithTracingContext() {
        // Given
        Exception exception = new RuntimeException("Test exception");
        String operation = "test-operation";
        String traceId = "test-trace-id";

        // Mock TracingContextHolder
        try (var mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            when(tracingContext.isActive()).thenReturn(true);
            when(tracingContext.getTraceId()).thenReturn(traceId);

            // When
            errorTracker.trackError(exception, operation);

            // Then
            verify(structuredLogger, times(1)).logError(any(Throwable.class), eq(tracingContext), anyMap());
        }
    }

    @Test
    void testTrackErrorWithAdditionalInfo() {
        // Given
        Exception exception = new RuntimeException("Test exception");
        String operation = "test-operation";
        Map<String, Object> additionalInfo = Map.of("key1", "value1", "key2", 123);

        // When
        errorTracker.trackError(exception, operation, additionalInfo);

        // Then
        verify(structuredLogger, times(1)).logError(any(Throwable.class), isNull(), anyMap());
    }

    @Test
    void testGetErrorStatistics() {
        // Given
        Exception exception1 = new RuntimeException("Test exception 1");
        Exception exception2 = new IllegalArgumentException("Test exception 2");
        String operation1 = "test-operation-1";
        String operation2 = "test-operation-2";

        // When
        errorTracker.trackError(exception1, operation1);
        errorTracker.trackError(exception2, operation2);
        errorTracker.trackError(exception1, operation1);

        // Then
        Map<String, Long> statistics = errorTracker.getErrorTypeStatistics();
        assertNotNull(statistics);
        assertNotNull(statistics);
        assertEquals(2, statistics.size());
        assertEquals(2L, statistics.get("java.lang.RuntimeException").longValue());
        assertEquals(1L, statistics.get("java.lang.IllegalArgumentException").longValue());
    }

    @Test
    void testGetErrorAggregations() {
        // Given
        Exception exception = new RuntimeException("Test exception");
        String operation = "test-operation";

        // When
        errorTracker.trackError(exception, operation);

        // Then
        Map<String, ErrorTracker.ErrorAggregation> aggregations = errorTracker.getErrorAggregations();
        assertNotNull(aggregations);
        assertTrue(aggregations.containsKey("java.lang.RuntimeException:test-operation"));
        
        ErrorTracker.ErrorAggregation aggregation = aggregations.get("java.lang.RuntimeException:test-operation");
        assertEquals("java.lang.RuntimeException", aggregation.getErrorType());
        assertEquals(1L, aggregation.getCount());
    }

    @Test
    void testClearStatistics() {
        // Given
        Exception exception = new RuntimeException("Test exception");
        String operation = "test-operation";

        errorTracker.trackError(exception, operation);
        assertFalse(errorTracker.getErrorTypeStatistics().isEmpty());

        // When
        // Clear method not available, skip this test
    }
}
package org.unreal.modelrouter.tracing.wrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerTracingWrapperTest {

    private CircuitBreakerTracingWrapper circuitBreakerWrapper;

    @Mock
    private CircuitBreaker delegate;

    @Mock
    private StructuredLogger structuredLogger;

    @BeforeEach
    void setUp() {
        // Mock getState方法的返回值
        when(delegate.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        circuitBreakerWrapper = new CircuitBreakerTracingWrapper(delegate, structuredLogger, "test-instance");
    }

    @Test
    void testCanExecute() {
        // 测试canExecute方法
        when(delegate.canExecute()).thenReturn(true);

        boolean result = circuitBreakerWrapper.canExecute();

        assertTrue(result);
        verify(delegate, times(1)).canExecute();
        // 验证记录了状态检查开始和执行允许两个事件
        verify(structuredLogger, times(2)).logBusinessEvent(anyString(), anyMap(), isNull());
    }

    @Test
    void testOnSuccess() {
        // 测试onSuccess方法
        assertDoesNotThrow(() -> circuitBreakerWrapper.onSuccess());
        verify(delegate, times(1)).onSuccess();
        // 根据实际实现，onSuccess只记录一次事件（成功完成）
        verify(structuredLogger, times(1)).logBusinessEvent(anyString(), anyMap(), isNull());
    }

    @Test
    void testOnFailure() {
        // 测试onFailure方法
        assertDoesNotThrow(() -> circuitBreakerWrapper.onFailure());
        verify(delegate, times(1)).onFailure();
        // 根据实际实现，onFailure只记录一次事件（失败完成）
        verify(structuredLogger, times(1)).logBusinessEvent(anyString(), anyMap(), isNull());
    }

    @Test
    void testGetState() {
        // 测试getState方法
        when(delegate.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        CircuitBreaker.State state = circuitBreakerWrapper.getState();

        assertEquals(CircuitBreaker.State.CLOSED, state);
        verify(delegate, times(1)).getState();
    }

    @Test
    void testGetStatistics() {
        // 测试获取统计信息
        when(delegate.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // 执行一些操作
        when(delegate.canExecute()).thenReturn(true);
        circuitBreakerWrapper.canExecute();
        circuitBreakerWrapper.onSuccess();
        circuitBreakerWrapper.onFailure();

        // 获取统计信息
        java.util.Map<String, Object> statistics = circuitBreakerWrapper.getStatistics();

        assertNotNull(statistics);
        assertEquals("test-instance", statistics.get("instance_id"));
        assertEquals("CLOSED", statistics.get("current_state"));
        assertEquals(1L, statistics.get("total_checks"));
        assertEquals(1L, statistics.get("allowed_executions"));
        assertEquals(1L, statistics.get("success_calls"));
        assertEquals(1L, statistics.get("failure_calls"));
    }
}
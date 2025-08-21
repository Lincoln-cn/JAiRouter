package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsErrorHandler 单元测试
 */
class MetricsErrorHandlerTest {

    private MeterRegistry meterRegistry;
    private MetricsErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        errorHandler = new MetricsErrorHandler(meterRegistry);
    }

    @Test
    void testHandleMetricsError() {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 处理错误
        errorHandler.handleMetricsError(component, operation, error);

        // 验证错误统计
        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(1, stats.getActiveErrorComponents());
        assertEquals(1, stats.getTotalErrors());
        assertEquals(0, stats.getDegradedComponents());
    }

    @Test
    void testDegradationActivation() {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 触发多次错误以激活降级
        for (int i = 0; i < 15; i++) {
            errorHandler.handleMetricsError(component, operation, error);
        }

        // 验证降级状态
        assertTrue(errorHandler.isDegraded(component, operation));
        
        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(1, stats.getDegradedComponents());
    }

    @Test
    void testSafeExecute() {
        String component = "test-component";
        String operation = "test-operation";
        AtomicInteger counter = new AtomicInteger(0);

        // 正常执行
        errorHandler.safeExecute(component, operation, counter::incrementAndGet);
        assertEquals(1, counter.get());

        // 异常执行
        errorHandler.safeExecute(component, operation, () -> {
            throw new RuntimeException("Test error");
        });

        // 验证错误被处理
        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(1, stats.getTotalErrors());
    }

    @Test
    void testSafeExecuteWithReturnValue() {
        String component = "test-component";
        String operation = "test-operation";

        // 正常执行
        String result = errorHandler.safeExecute(component, operation, () -> "success", "default");
        assertEquals("success", result);

        // 异常执行
        String defaultResult = errorHandler.safeExecute(component, operation, () -> {
            throw new RuntimeException("Test error");
        }, "default");
        assertEquals("default", defaultResult);
    }

    @Test
    void testResetErrorState() {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 触发错误
        for (int i = 0; i < 15; i++) {
            errorHandler.handleMetricsError(component, operation, error);
        }

        assertTrue(errorHandler.isDegraded(component, operation));

        // 重置错误状态
        errorHandler.resetErrorState(component, operation);

        assertFalse(errorHandler.isDegraded(component, operation));
        
        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(0, stats.getDegradedComponents());
    }

    @Test
    void testCleanupExpiredErrors() throws InterruptedException {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 处理错误
        errorHandler.handleMetricsError(component, operation, error);
        
        MetricsErrorHandler.MetricsErrorStats statsBefore = errorHandler.getErrorStats();
        assertEquals(1, statsBefore.getActiveErrorComponents());

        // 等待一段时间后清理
        Thread.sleep(100);
        errorHandler.cleanupExpiredErrors();

        // 由于清理间隔很长，错误记录应该还在
        MetricsErrorHandler.MetricsErrorStats statsAfter = errorHandler.getErrorStats();
        assertEquals(1, statsAfter.getActiveErrorComponents());
    }

    @Test
    void testConcurrentErrorHandling() throws InterruptedException {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");
        
        int threadCount = 10;
        int errorsPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 并发处理错误
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < errorsPerThread; j++) {
                        errorHandler.handleMetricsError(component, operation, error);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // 验证错误统计
        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(threadCount * errorsPerThread, stats.getTotalErrors());
        assertTrue(errorHandler.isDegraded(component, operation));
    }

    @Test
    void testMultipleComponentErrors() {
        Exception error = new RuntimeException("Test error");

        // 为不同组件处理错误
        errorHandler.handleMetricsError("component1", "operation1", error);
        errorHandler.handleMetricsError("component2", "operation2", error);
        errorHandler.handleMetricsError("component3", "operation3", error);

        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(3, stats.getActiveErrorComponents());
        assertEquals(3, stats.getTotalErrors());
        assertEquals(0, stats.getDegradedComponents());
    }

    @Test
    void testErrorRateThreshold() {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 处理错误但不超过阈值
        for (int i = 0; i < 9; i++) {
            errorHandler.handleMetricsError(component, operation, error);
        }

        assertFalse(errorHandler.isDegraded(component, operation));

        // 超过阈值
        errorHandler.handleMetricsError(component, operation, error);
        assertTrue(errorHandler.isDegraded(component, operation));
    }
}
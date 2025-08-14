package org.unreal.moduler.monitoring.error;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.error.MetricsErrorHandler;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsErrorHandler 单元测试
 */
class MetricsErrorHandlerTest {

    private MetricsErrorHandler errorHandler;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        errorHandler = new MetricsErrorHandler(meterRegistry);
    }

    @Test
    void testHandleMetricsError() {
        // 测试错误处理
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        errorHandler.handleMetricsError(component, operation, error);

        // 验证错误统计
        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(1, stats.getActiveErrorComponents());
        assertEquals(1, stats.getTotalErrors());
    }

    @Test
    void testDegradationActivation() {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 触发多次错误以激活降级
        for (int i = 0; i < 12; i++) {
            errorHandler.handleMetricsError(component, operation, error);
        }

        // 验证降级状态
        assertTrue(errorHandler.isDegraded(component, operation));
    }

    @Test
    void testSafeExecute() {
        String component = "test-component";
        String operation = "test-operation";
        AtomicInteger counter = new AtomicInteger(0);

        // 测试正常执行
        errorHandler.safeExecute(component, operation, counter::incrementAndGet);
        assertEquals(1, counter.get());

        // 测试异常执行
        errorHandler.safeExecute(component, operation, () -> {
            throw new RuntimeException("Test error");
        });

        // 验证错误被处理
        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertTrue(stats.getTotalErrors() > 0);
    }

    @Test
    void testSafeExecuteWithReturnValue() {
        String component = "test-component";
        String operation = "test-operation";

        // 测试正常执行
        String result = errorHandler.safeExecute(component, operation, () -> "success", "default");
        assertEquals("success", result);

        // 测试异常执行
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
        for (int i = 0; i < 12; i++) {
            errorHandler.handleMetricsError(component, operation, error);
        }

        assertTrue(errorHandler.isDegraded(component, operation));

        // 重置错误状态
        errorHandler.resetErrorState(component, operation);
        assertFalse(errorHandler.isDegraded(component, operation));
    }

    @Test
    void testCleanupExpiredErrors() {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 触发错误
        errorHandler.handleMetricsError(component, operation, error);

        MetricsErrorHandler.MetricsErrorStats statsBefore = errorHandler.getErrorStats();
        assertTrue(statsBefore.getActiveErrorComponents() > 0);

        // 清理过期错误
        errorHandler.cleanupExpiredErrors();

        // 注意：由于错误刚刚发生，不会被清理，这里主要测试方法不抛异常
        assertDoesNotThrow(() -> errorHandler.cleanupExpiredErrors());
    }

    @Test
    void testErrorStatsAccuracy() {
        String component1 = "component1";
        String component2 = "component2";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 为不同组件触发错误
        errorHandler.handleMetricsError(component1, operation, error);
        errorHandler.handleMetricsError(component2, operation, error);
        errorHandler.handleMetricsError(component1, operation, error);

        MetricsErrorHandler.MetricsErrorStats stats = errorHandler.getErrorStats();
        assertEquals(2, stats.getActiveErrorComponents()); // 两个不同的组件
        assertEquals(3, stats.getTotalErrors()); // 总共三次错误
    }

    @Test
    void testDegradationRecovery() throws InterruptedException {
        String component = "test-component";
        String operation = "test-operation";
        Exception error = new RuntimeException("Test error");

        // 触发降级
        for (int i = 0; i < 12; i++) {
            errorHandler.handleMetricsError(component, operation, error);
        }

        assertTrue(errorHandler.isDegraded(component, operation));

        // 等待一小段时间（实际场景中需要等待降级持续时间）
        Thread.sleep(100);

        // 在实际实现中，降级会在一定时间后自动恢复
        // 这里我们测试手动重置
        errorHandler.resetErrorState(component, operation);
        assertFalse(errorHandler.isDegraded(component, operation));
    }
}
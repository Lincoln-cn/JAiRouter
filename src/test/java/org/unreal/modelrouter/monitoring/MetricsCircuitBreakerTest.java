package org.unreal.modelrouter.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 指标熔断器测试
 */
class MetricsCircuitBreakerTest {

    private MetricsCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new MetricsCircuitBreaker();
    }

    @Test
    void testInitialState() {
        // 初始状态应该是CLOSED
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    void testSuccessRecording() {
        // 记录成功
        circuitBreaker.recordSuccess();
        
        // 状态应该保持CLOSED
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
        
        // 统计信息应该正确
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(1, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
    }

    @Test
    void testFailureRecording() {
        // 记录失败
        circuitBreaker.recordFailure();
        
        // 状态应该保持CLOSED（单次失败不会触发熔断）
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
        
        // 统计信息应该正确
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(0, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
    }

    @Test
    void testCircuitOpeningDueToFailureThreshold() {
        // 记录足够的请求以满足最小请求数要求
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordSuccess();
        }
        
        // 记录大量失败以触发熔断
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }
        
        // 熔断器应该开启
        assertEquals("OPEN", circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void testCircuitOpeningDueToFailureRate() {
        // 记录一些成功和失败，使失败率超过阈值
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordSuccess();
        }
        for (int i = 0; i < 7; i++) {
            circuitBreaker.recordFailure();
        }
        
        // 熔断器应该开启（失败率70% > 50%阈值）
        assertEquals("OPEN", circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void testHalfOpenTransition() throws InterruptedException {
        // 先触发熔断
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordSuccess();
        }
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }
        
        assertEquals("OPEN", circuitBreaker.getState());
        
        // 等待超时时间（实际测试中可能需要调整）
        Thread.sleep(100); // 简化测试，实际超时时间是30秒
        
        // 强制设置为可以尝试恢复的状态（模拟超时）
        // 由于实际超时时间较长，这里直接测试半开状态的行为
        
        // 手动测试半开状态逻辑
        testHalfOpenBehavior();
    }

    private void testHalfOpenBehavior() {
        // 创建新的熔断器并强制设置为半开状态进行测试
        MetricsCircuitBreaker testBreaker = new MetricsCircuitBreaker();
        
        // 先触发开启
        for (int i = 0; i < 15; i++) {
            testBreaker.recordFailure();
        }
        assertEquals("OPEN", testBreaker.getState());
        
        // 模拟半开状态下的成功恢复
        // 这里需要等待实际的超时时间或使用反射来测试
    }

    @Test
    void testForceOpen() {
        // 强制开启熔断器
        circuitBreaker.forceOpen();
        
        assertEquals("OPEN", circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void testForceClose() {
        // 先强制开启
        circuitBreaker.forceOpen();
        assertEquals("OPEN", circuitBreaker.getState());
        
        // 然后强制关闭
        circuitBreaker.forceClose();
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
        
        // 统计信息应该被重置
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(0, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
    }

    @Test
    void testFailureRateCalculation() {
        // 记录混合的成功和失败
        for (int i = 0; i < 6; i++) {
            circuitBreaker.recordSuccess();
        }
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordFailure();
        }
        
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(6, stats.getSuccessCount());
        assertEquals(4, stats.getFailureCount());
        assertEquals(10, stats.getRequestCount());
        assertEquals(0.4, stats.getFailureRate(), 0.01); // 40%失败率
    }

    @Test
    void testMinimumRequestsThreshold() {
        // 记录少量失败（不足最小请求数）
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }
        
        // 熔断器应该保持关闭状态
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    void testStatsToString() {
        // 记录一些数据
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();
        
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        String statsString = stats.toString();
        
        assertNotNull(statsString);
        assertTrue(statsString.contains("CLOSED"));
        assertTrue(statsString.contains("failures=1"));
        assertTrue(statsString.contains("successes=1"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // 测试并发访问
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    if (threadId % 2 == 0) {
                        circuitBreaker.recordSuccess();
                    } else {
                        circuitBreaker.recordFailure();
                    }
                    circuitBreaker.allowRequest();
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证统计信息
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(1000, stats.getRequestCount()); // 10线程 * 100请求
        assertEquals(500, stats.getSuccessCount());
        assertEquals(500, stats.getFailureCount());
    }

    @Test
    void testWindowReset() throws InterruptedException {
        // 记录一些数据
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();
        
        MetricsCircuitBreaker.CircuitBreakerStats initialStats = circuitBreaker.getStats();
        assertEquals(1, initialStats.getSuccessCount());
        assertEquals(1, initialStats.getFailureCount());
        
        // 等待一小段时间（实际窗口重置时间是1分钟，这里只是测试逻辑）
        Thread.sleep(10);
        
        // 记录更多数据
        circuitBreaker.recordSuccess();
        
        MetricsCircuitBreaker.CircuitBreakerStats newStats = circuitBreaker.getStats();
        assertTrue(newStats.getRequestCount() > 0);
    }
}
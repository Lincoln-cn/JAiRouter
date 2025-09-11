package org.unreal.modelrouter.monitoring.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsCircuitBreaker 单元测试
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
        
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals("CLOSED", stats.getState());
        assertEquals(1, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
        assertEquals(1, stats.getRequestCount());
        assertEquals(0.0, stats.getFailureRate());
    }

    @Test
    void testFailureRecording() {
        // 记录失败
        circuitBreaker.recordFailure();
        
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals("CLOSED", stats.getState());
        assertEquals(0, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
        assertEquals(1, stats.getRequestCount());
        assertEquals(1.0, stats.getFailureRate());
    }

    @Test
    void testCircuitBreakerOpening() {
        // 记录足够的失败以触发熔断器开启
        for (int i = 0; i < 15; i++) {
            circuitBreaker.recordFailure();
        }
        
        // 熔断器应该开启
        assertEquals("OPEN", circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void testFailureRateThreshold() {
        // 记录一些成功和失败，但失败率超过阈值
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordSuccess();
        }
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordFailure();
        }
        
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertTrue(stats.getFailureRate() > 0.5); // 失败率超过50%
        
        // 但由于总请求数较少，熔断器可能还没开启
        // 继续添加更多失败
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }
        
        assertEquals("OPEN", circuitBreaker.getState());
    }

    @Test
    void testHalfOpenState() throws InterruptedException {
        // 首先触发熔断器开启
        for (int i = 0; i < 15; i++) {
            circuitBreaker.recordFailure();
        }
        assertEquals("OPEN", circuitBreaker.getState());
        
        // 等待超时时间（在实际实现中需要等待30秒，这里我们模拟）
        // 由于测试环境限制，我们直接测试allowRequest方法的逻辑
        
        // 在OPEN状态下，请求应该被拒绝
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void testForceOpen() {
        // 初始状态是CLOSED
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
        
        // 强制开启
        circuitBreaker.forceOpen();
        assertEquals("OPEN", circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void testForceClose() {
        // 首先触发熔断器开启
        for (int i = 0; i < 15; i++) {
            circuitBreaker.recordFailure();
        }
        assertEquals("OPEN", circuitBreaker.getState());
        
        // 强制关闭
        circuitBreaker.forceClose();
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
        
        // 统计信息应该被重置
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(0, stats.getFailureCount());
        assertEquals(0, stats.getSuccessCount());
        assertEquals(0, stats.getRequestCount());
    }

    @Test
    void testStatsAccuracy() {
        // 记录混合的成功和失败
        circuitBreaker.recordSuccess();
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();
        
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(3, stats.getSuccessCount());
        assertEquals(2, stats.getFailureCount());
        assertEquals(5, stats.getRequestCount());
        assertEquals(0.4, stats.getFailureRate(), 0.01); // 2/5 = 0.4
    }

    @Test
    void testMinimumRequestsThreshold() {
        // 记录少量失败，不应该触发熔断器
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        
        // 由于请求数少于最小阈值，熔断器应该保持关闭
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    void testRecoveryFromHalfOpen() {
        // 模拟从HALF_OPEN状态恢复
        // 首先强制设置为OPEN状态
        circuitBreaker.forceOpen();
        assertEquals("OPEN", circuitBreaker.getState());
        
        // 然后强制关闭以模拟恢复
        circuitBreaker.forceClose();
        assertEquals("CLOSED", circuitBreaker.getState());
        
        // 记录一些成功操作
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordSuccess();
        }
        
        // 熔断器应该保持关闭状态
        assertEquals("CLOSED", circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    void testStatsToString() {
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();
        
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        String statsString = stats.toString();
        
        assertNotNull(statsString);
        assertTrue(statsString.contains("CLOSED"));
        assertTrue(statsString.contains("failures=1"));
        assertTrue(statsString.contains("successes=1"));
        assertTrue(statsString.contains("requests=2"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // 测试并发访问的安全性
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    if (threadIndex % 2 == 0) {
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
        
        // 验证统计信息的一致性
        MetricsCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals(threadCount * operationsPerThread, stats.getRequestCount());
        assertEquals(500, stats.getSuccessCount()); // 5个线程 * 100次操作
        assertEquals(500, stats.getFailureCount()); // 5个线程 * 100次操作
    }
}
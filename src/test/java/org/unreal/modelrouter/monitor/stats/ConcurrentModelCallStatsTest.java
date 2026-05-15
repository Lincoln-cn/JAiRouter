package org.unreal.modelrouter.monitor.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConcurrentModelCallStats 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.2
 */
class ConcurrentModelCallStatsTest {

    private ConcurrentModelCallStats stats;

    @BeforeEach
    void setUp() {
        stats = new ConcurrentModelCallStats("test-model", "chat");
    }

    @Test
    @DisplayName("构造器应正确设置模型名称和服务类型")
    void constructor_shouldSetModelNameAndServiceType() {
        assertEquals("test-model", stats.getModelName());
        assertEquals("chat", stats.getServiceType());
        assertTrue(stats.getStatsStartTime() > 0);
    }

    @Test
    @DisplayName("更新成功统计应正确计数")
    void updateStats_success_shouldIncrementCorrectly() {
        stats.updateStats(true, 100);

        assertEquals(1, stats.getTotalCalls());
        assertEquals(1, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
        assertEquals(100.0, stats.getAvgResponseTime(), 0.1);
        assertEquals(100, stats.getMinResponseTime());
        assertEquals(100, stats.getMaxResponseTime());
    }

    @Test
    @DisplayName("更新失败统计应正确计数")
    void updateStats_failure_shouldIncrementCorrectly() {
        stats.updateStats(false, 200);

        assertEquals(1, stats.getTotalCalls());
        assertEquals(0, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
    }

    @Test
    @DisplayName("多次更新应正确累计")
    void updateStats_multipleUpdates_shouldAccumulate() {
        stats.updateStats(true, 100);
        stats.updateStats(true, 200);
        stats.updateStats(false, 300);

        assertEquals(3, stats.getTotalCalls());
        assertEquals(2, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
        assertEquals(100, stats.getMinResponseTime());
        assertEquals(300, stats.getMaxResponseTime());
    }

    @Test
    @DisplayName("成功率计算应正确")
    void calculateSuccessRate_shouldCalculateCorrectly() {
        // 无调用时返回1.0
        assertEquals(1.0, stats.calculateSuccessRate(), 0.001);

        stats.updateStats(true, 100);
        assertEquals(1.0, stats.calculateSuccessRate(), 0.001);

        stats.updateStats(false, 100);
        assertEquals(0.5, stats.calculateSuccessRate(), 0.001);

        stats.updateStats(false, 100);
        assertEquals(0.333, stats.calculateSuccessRate(), 0.01);
    }

    @Test
    @DisplayName("失败率计算应正确")
    void calculateFailureRate_shouldCalculateCorrectly() {
        // 无调用时返回0.0
        assertEquals(0.0, stats.calculateFailureRate(), 0.001);

        stats.updateStats(false, 100);
        stats.updateStats(false, 100);
        stats.updateStats(true, 100);

        assertEquals(0.666, stats.calculateFailureRate(), 0.01);
    }

    @Test
    @DisplayName("记录熔断事件应正确计数")
    void recordCircuitBreaker_shouldIncrementCorrectly() {
        stats.recordCircuitBreaker();
        stats.recordCircuitBreaker();

        assertEquals(2, stats.getTotalCalls());
        assertEquals(2, stats.getCircuitBreakerCount());
        assertTrue(stats.getLastCallTime() > 0);
    }

    @Test
    @DisplayName("记录限流事件应正确计数")
    void recordRateLimit_shouldIncrementCorrectly() {
        stats.recordRateLimit();
        stats.recordRateLimit();
        stats.recordRateLimit();

        assertEquals(3, stats.getTotalCalls());
        assertEquals(3, stats.getRateLimitCount());
    }

    @Test
    @DisplayName("记录错误码应正确分布")
    void recordErrorCode_shouldDistributeCorrectly() {
        stats.recordErrorCode("400");
        stats.recordErrorCode("400");
        stats.recordErrorCode("500");
        stats.recordErrorCode("503");

        Map<String, Long> distribution = stats.getErrorCodeDistribution();
        assertEquals(2L, distribution.get("400"));
        assertEquals(1L, distribution.get("500"));
        assertEquals(1L, distribution.get("503"));
    }

    @Test
    @DisplayName("健康状态判断应正确")
    void determineHealthStatus_shouldDetermineCorrectly() {
        // 默认健康
        assertEquals("HEALTHY", stats.determineHealthStatus());

        // 95% 成功率 -> DEGRADED
        for (int i = 0; i < 95; i++) {
            stats.updateStats(true, 100);
        }
        for (int i = 0; i < 5; i++) {
            stats.updateStats(false, 100);
        }
        assertEquals("DEGRADED", stats.determineHealthStatus());

        // 重置后测试 UNHEALTHY
        stats.reset();
        for (int i = 0; i < 90; i++) {
            stats.updateStats(true, 100);
        }
        for (int i = 0; i < 10; i++) {
            stats.updateStats(false, 100);
        }
        assertEquals("UNHEALTHY", stats.determineHealthStatus());
    }

    @Test
    @DisplayName("重置应清空所有统计")
    void reset_shouldClearAllStats() {
        stats.updateStats(true, 100);
        stats.recordCircuitBreaker();
        stats.recordErrorCode("500");

        stats.reset();

        assertEquals(0, stats.getTotalCalls());
        assertEquals(0, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
        assertEquals(0, stats.getCircuitBreakerCount());
        assertEquals(0, stats.getRateLimitCount());
        assertTrue(stats.getErrorCodeDistribution().isEmpty());
    }

    @Test
    @DisplayName("多线程并发更新应正确计数")
    void concurrentUpdate_shouldCountCorrectly() throws InterruptedException {
        int threadCount = 10;
        int updatesPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final boolean success = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < updatesPerThread; j++) {
                        stats.updateStats(success, 100);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * updatesPerThread, stats.getTotalCalls());
        assertEquals(5 * updatesPerThread, stats.getSuccessCount());
        assertEquals(5 * updatesPerThread, stats.getFailureCount());
    }

    @Test
    @DisplayName("多线程并发记录熔断应正确计数")
    void concurrentCircuitBreaker_shouldCountCorrectly() throws InterruptedException {
        int threadCount = 5;
        int recordsPerThread = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        stats.recordCircuitBreaker();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * recordsPerThread, stats.getCircuitBreakerCount());
    }

    @Test
    @DisplayName("多线程并发记录错误码应正确计数")
    void concurrentErrorCode_shouldCountCorrectly() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String errorCode = String.valueOf(400 + i % 3);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        stats.recordErrorCode(errorCode);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Map<String, Long> distribution = stats.getErrorCodeDistribution();
        assertEquals(threadCount * recordsPerThread,
                distribution.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    @DisplayName("QPS计算应返回合理值")
    void getCurrentQps_shouldReturnReasonableValue() {
        // 记录多次请求
        for (int i = 0; i < 60; i++) {
            stats.updateStats(true, 100);
        }

        double qps = stats.getCurrentQps();
        assertTrue(qps >= 0.5, "QPS should be at least 0.5, got: " + qps);
    }

    @Test
    @DisplayName("活跃状态判断应正确")
    void isActive_shouldDetermineCorrectly() {
        // 新创建时没有调用，不活跃
        assertFalse(stats.isActive());

        // 有调用后活跃
        stats.updateStats(true, 100);
        assertTrue(stats.isActive());
    }

    @Test
    @DisplayName("熔断率和限流率计算应正确")
    void calculateRates_shouldCalculateCorrectly() {
        stats.updateStats(true, 100);
        stats.updateStats(true, 100);
        stats.recordCircuitBreaker();
        stats.recordRateLimit();

        assertEquals(0.25, stats.calculateCircuitBreakerRate(), 0.01);
        assertEquals(0.25, stats.calculateRateLimitRate(), 0.01);
    }

    @Test
    @DisplayName("高吞吐量场景应无性能问题")
    void highThroughput_shouldPerformWell() {
        long startTime = System.currentTimeMillis();
        int updateCount = 100000;

        for (int i = 0; i < updateCount; i++) {
            stats.updateStats(true, 100);
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("更新 " + updateCount + " 次耗时: " + duration + "ms");

        assertTrue(duration < 2000, "更新100000次应在2秒内完成");
        assertEquals(updateCount, stats.getTotalCalls());
    }
}

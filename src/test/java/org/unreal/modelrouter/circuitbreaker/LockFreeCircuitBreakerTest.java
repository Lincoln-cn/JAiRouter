package org.unreal.modelrouter.router.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LockFreeCircuitBreaker 单元测试 - v2.0.0
 * 
 * 测试内容：
 * 1. 基本状态转换测试
 * 2. 并发性能测试
 * 3. 状态持久化和恢复测试
 * 4. 新增方法测试（getStateDetail, getMetrics, persistState, restoreState）
 */
@DisplayName("LockFreeCircuitBreaker v2.0.0 测试")
class LockFreeCircuitBreakerTest {

    private LockFreeCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // 失败阈值 3，超时 5 秒，成功阈值 2
        circuitBreaker = new LockFreeCircuitBreaker("test-instance-1", 3, 5000, 2);
    }

    @Test
    @DisplayName("测试 1: 初始状态应为 CLOSED")
    void testInitialState() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
        assertEquals(0, circuitBreaker.getSuccessCount());
    }

    @Test
    @DisplayName("测试 2: CLOSED 状态下可以执行")
    void testCanExecuteInClosedState() {
        assertTrue(circuitBreaker.canExecute());
    }

    @Test
    @DisplayName("测试 3: 失败达到阈值后熔断器打开")
    void testCircuitBreakerOpensAfterThreshold() {
        // 连续失败 3 次
        for (int i = 0; i < 3; i++) {
            circuitBreaker.onFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.canExecute());
    }

    @Test
    @DisplayName("测试 4: OPEN 状态超时后转为 HALF_OPEN")
    void testOpenToHalfOpenAfterTimeout() throws InterruptedException {
        // 创建短超时的熔断器
        LockFreeCircuitBreaker shortTimeoutCB = new LockFreeCircuitBreaker("test-2", 2, 100, 1);

        // 失败 2 次打开熔断器
        shortTimeoutCB.onFailure();
        shortTimeoutCB.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, shortTimeoutCB.getState());

        // 等待超时
        Thread.sleep(150);

        // 应该可以执行并转为 HALF_OPEN
        assertTrue(shortTimeoutCB.canExecute());
        assertEquals(CircuitBreaker.State.HALF_OPEN, shortTimeoutCB.getState());
    }

    @Test
    @DisplayName("测试 5: HALF_OPEN 状态下成功达到阈值后关闭")
    void testHalfOpenToClosedAfterSuccess() throws InterruptedException {
        LockFreeCircuitBreaker shortTimeoutCB = new LockFreeCircuitBreaker("test-3", 2, 100, 2);

        // 打开熔断器
        shortTimeoutCB.onFailure();
        shortTimeoutCB.onFailure();

        // 等待超时
        Thread.sleep(150);

        // 转为 HALF_OPEN
        shortTimeoutCB.canExecute();

        // 成功 2 次
        shortTimeoutCB.onSuccess();
        shortTimeoutCB.onSuccess();

        assertEquals(CircuitBreaker.State.CLOSED, shortTimeoutCB.getState());
    }

    @Test
    @DisplayName("测试 6: HALF_OPEN 状态下失败重新打开")
    void testHalfOpenToOpenOnFailure() throws InterruptedException {
        LockFreeCircuitBreaker shortTimeoutCB = new LockFreeCircuitBreaker("test-4", 2, 100, 2);

        // 打开熔断器
        shortTimeoutCB.onFailure();
        shortTimeoutCB.onFailure();

        // 等待超时
        Thread.sleep(150);

        // 转为 HALF_OPEN
        shortTimeoutCB.canExecute();
        assertEquals(CircuitBreaker.State.HALF_OPEN, shortTimeoutCB.getState());

        // 失败
        shortTimeoutCB.onFailure();

        assertEquals(CircuitBreaker.State.OPEN, shortTimeoutCB.getState());
    }

    @Test
    @DisplayName("测试 7: 重置熔断器")
    void testReset() {
        // 打开熔断器
        circuitBreaker.onFailure();
        circuitBreaker.onFailure();
        circuitBreaker.onFailure();

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 重置
        circuitBreaker.reset();

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
        assertEquals(0, circuitBreaker.getSuccessCount());
    }

    // ==================== v2.0.0 新增功能测试 ====================

    @Test
    @DisplayName("测试 8: getStateDetail 返回完整状态信息")
    void testGetStateDetail() {
        // 制造一些失败
        circuitBreaker.onFailure();
        circuitBreaker.onFailure();

        Map<String, Object> detail = circuitBreaker.getStateDetail();

        assertEquals("test-instance-1", detail.get("instanceId"));
        assertEquals("CLOSED", detail.get("state"));
        assertEquals(2, detail.get("failureCount"));
        assertEquals(0, detail.get("successCount"));
        assertEquals(3, detail.get("failureThreshold"));
        assertEquals(2, detail.get("successThreshold"));
        assertEquals(5000L, detail.get("timeout"));
        assertTrue((Long) detail.get("elapsedTime") >= 0);
    }

    @Test
    @DisplayName("测试 9: getMetrics 返回性能指标")
    void testGetMetrics() {
        // 打开熔断器，这样计数器不会被重置
        circuitBreaker.onFailure();
        circuitBreaker.onFailure();
        circuitBreaker.onFailure(); // 打开熔断器

        Map<String, Object> metrics = circuitBreaker.getMetrics();

        assertEquals("test-instance-1", metrics.get("instanceId"));
        assertEquals("OPEN", metrics.get("currentState"));
        assertEquals(3, metrics.get("totalFailures"));
        assertNotNull(metrics.get("failureRate"));
    }

    @Test
    @DisplayName("测试 10: persistState 和 restoreState 正常工作")
    void testPersistAndRestoreState() {
        // 打开熔断器以保留计数器值
        circuitBreaker.onFailure();
        circuitBreaker.onFailure();
        circuitBreaker.onFailure(); // 打开熔断器

        // 持久化状态
        Map<String, Object> stateData = circuitBreaker.persistState();

        assertNotNull(stateData);
        assertEquals("test-instance-1", stateData.get("instanceId"));
        assertEquals("OPEN", stateData.get("state"));
        assertEquals(3, stateData.get("failureCount"));
        assertEquals(0, stateData.get("successCount"));

        // 创建新的熔断器并恢复状态
        LockFreeCircuitBreaker newCircuitBreaker = new LockFreeCircuitBreaker("test-instance-1", 3, 5000, 2);
        newCircuitBreaker.restoreState(stateData);

        Map<String, Object> restoredDetail = newCircuitBreaker.getStateDetail();
        assertEquals("OPEN", restoredDetail.get("state"));
        assertEquals(3, restoredDetail.get("failureCount"));
    }

    @Test
    @DisplayName("测试 11: restoreState 拒绝不匹配的实例 ID")
    void testRestoreStateWithMismatchedInstanceId() {
        circuitBreaker.onFailure();
        Map<String, Object> stateData = circuitBreaker.persistState();

        // 尝试恢复到不同 ID 的熔断器
        LockFreeCircuitBreaker wrongCircuitBreaker = new LockFreeCircuitBreaker("wrong-instance-id", 3, 5000, 2);
        wrongCircuitBreaker.restoreState(stateData);

        // 应该没有恢复成功
        Map<String, Object> detail = wrongCircuitBreaker.getStateDetail();
        assertEquals(0, detail.get("failureCount"));
    }

    // ==================== 并发性能测试 ====================

    @Test
    @DisplayName("测试 12: 高并发下的状态转换正确性")
    void testConcurrentStateTransitions() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 创建短超时熔断器
        LockFreeCircuitBreaker concurrentCB = new LockFreeCircuitBreaker("test-concurrent", 5, 100, 3);

        // 启动多个线程并发调用
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        if (concurrentCB.canExecute()) {
                            if (threadId % 2 == 0) {
                                concurrentCB.onFailure();
                            } else {
                                concurrentCB.onSuccess();
                            }
                        }
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 验证没有异常
        assertEquals(0, errorCount.get(), "并发执行过程中不应出现异常");

        // 验证最终状态是有效的
        CircuitBreaker.State finalState = concurrentCB.getState();
        assertTrue(finalState == CircuitBreaker.State.CLOSED ||
                   finalState == CircuitBreaker.State.OPEN ||
                   finalState == CircuitBreaker.State.HALF_OPEN,
                   "最终状态应该是有效的状态");
    }

    @Test
    @DisplayName("测试 13: 并发性能对比测试")
    void testConcurrentPerformance() throws InterruptedException {
        int threadCount = 20;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        // 并发执行操作
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        circuitBreaker.canExecute();
                        if (j % 10 == 0) {
                            circuitBreaker.onFailure();
                        } else {
                            circuitBreaker.onSuccess();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 验证性能：2000 次操作应在合理时间内完成
        assertTrue(duration < 5000, "并发操作应在 5 秒内完成，实际耗时：" + duration + "ms");

        System.out.println("并发性能测试完成：");
        System.out.println("  线程数：" + threadCount);
        System.out.println("  每线程操作数：" + operationsPerThread);
        System.out.println("  总操作数：" + (threadCount * operationsPerThread));
        System.out.println("  总耗时：" + duration + "ms");
        System.out.println("  吞吐量：" + (threadCount * operationsPerThread * 1000 / duration) + " ops/s");
    }
}

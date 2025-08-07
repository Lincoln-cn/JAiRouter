package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.circuitbreaker.DefaultCircuitBreaker;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private DefaultCircuitBreaker circuitBreaker;
    private static final String INSTANCE_ID = "test-instance";
    private static final int FAILURE_THRESHOLD = 3;
    private static final long TIMEOUT = 1000; // 1秒
    private static final int SUCCESS_THRESHOLD = 2;

    @BeforeEach
    void setUp() {
        circuitBreaker = new DefaultCircuitBreaker(
                INSTANCE_ID,
                FAILURE_THRESHOLD,
                TIMEOUT,
                SUCCESS_THRESHOLD
        );
    }

    @Test
    void testInitialState() {
        // 测试初始状态
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.canExecute());
    }

    @Test
    void testClosedToOpenTransition() {
        // 记录失败直到达到阈值
        for (int i = 0; i < FAILURE_THRESHOLD - 1; i++) {
            circuitBreaker.onFailure();
            assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
            assertTrue(circuitBreaker.canExecute());
        }

        // 达到阈值后应该变为OPEN状态
        circuitBreaker.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.canExecute());
    }

    @Test
    void testOpenToHalfOpenTransition() throws InterruptedException {
        // 先让熔断器进入OPEN状态
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuitBreaker.onFailure();
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.canExecute());

        // 等待超时时间过去
        Thread.sleep(TIMEOUT + 100);

        // 现在应该可以执行（进入HALF_OPEN状态）
        assertTrue(circuitBreaker.canExecute());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void testHalfOpenToClosedTransition() throws InterruptedException {
        // 先让熔断器进入HALF_OPEN状态
        openAndTimeoutCircuitBreaker();

        // 成功次数达到阈值应该变为CLOSED状态
        for (int i = 0; i < SUCCESS_THRESHOLD; i++) {
            circuitBreaker.onSuccess();
        }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.canExecute());
        // 失败计数应该重置
        // 注意：由于failureCount是私有字段，我们无法直接验证，但可以通过行为间接验证
    }

    @Test
    void testHalfOpenToOpenTransition() throws InterruptedException {
        // 先让熔断器进入HALF_OPEN状态
        openAndTimeoutCircuitBreaker();

        // 一次失败应该让它回到OPEN状态
        circuitBreaker.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.canExecute());
    }

    @Test
    void testSuccessInClosedState() {
        // 在CLOSED状态下成功不应该改变状态
        circuitBreaker.onSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.canExecute());

        // 即使多次成功也应该保持CLOSED状态
        for (int i = 0; i < 10; i++) {
            circuitBreaker.onSuccess();
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testFailureInOpenState() {
        // 先让熔断器进入OPEN状态
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuitBreaker.onFailure();
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 在OPEN状态下失败不应该改变状态
        circuitBreaker.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.canExecute());
    }

    @Test
    void testResetFailureCountAfterClosedState() throws InterruptedException {
        // 触发OPEN状态
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuitBreaker.onFailure();
        }
        assertEquals(FAILURE_THRESHOLD, getFailureCountThroughReflection());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 等待超时并进入HALF_OPEN
        Thread.sleep(TIMEOUT + 100);
        assertTrue(circuitBreaker.canExecute());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        // 成功并回到CLOSED
        for (int i = 0; i < SUCCESS_THRESHOLD; i++) {
            circuitBreaker.onSuccess();
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // 再次失败，检查计数是否重置
        circuitBreaker.onFailure();
        assertEquals(1, getFailureCountThroughReflection());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // 测试并发访问场景
        Runnable failureTask = () -> {
            for (int i = 0; i < FAILURE_THRESHOLD / 2; i++) {
                circuitBreaker.onFailure();
            }
        };

        // 启动多个线程同时触发失败
        Thread thread1 = new Thread(failureTask);
        Thread thread2 = new Thread(failureTask);

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // 检查状态（可能已经OPEN，取决于执行顺序）
        CircuitBreaker.State state = circuitBreaker.getState();
        assertTrue(state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.OPEN);
    }

    private void openAndTimeoutCircuitBreaker() throws InterruptedException {
        // 让熔断器进入OPEN状态
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuitBreaker.onFailure();
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 等待超时
        Thread.sleep(TIMEOUT + 100);

        // 确认可以执行（进入HALF_OPEN状态）
        assertTrue(circuitBreaker.canExecute());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    // 通过反射获取failureCount用于测试验证
    private int getFailureCountThroughReflection() {
        try {
            java.lang.reflect.Field field = DefaultCircuitBreaker.class.getDeclaredField("failureCount");
            field.setAccessible(true);
            return field.getInt(circuitBreaker);
        } catch (Exception e) {
            fail("Failed to access failureCount field through reflection");
            return -1;
        }
    }
}

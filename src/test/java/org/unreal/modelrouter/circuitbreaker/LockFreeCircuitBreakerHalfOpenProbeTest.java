package org.unreal.modelrouter.circuitbreaker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.LockFreeCircuitBreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #870：半开态只放行一个探测请求。
 *
 * <p>背景：熔断器打开到超时后，旧实现把所有并发请求一起放行；故障实例会在恢复窗口被重新打满，
 * 而 {@code onSuccess} 里「达到阈值后 CAS 失败就清零」的写法又可能把关闭条件反复清掉，
 * 表现出来的就是「熔断看起来打开了，故障期间却几乎没起到保护作用」。
 */
class LockFreeCircuitBreakerHalfOpenProbeTest {

    private static final int FAILURE_THRESHOLD = 2;
    private static final long TIMEOUT_MS = 100L;
    private static final int SUCCESS_THRESHOLD = 2;

    private LockFreeCircuitBreaker openedBreaker(final String instanceId) throws InterruptedException {
        final LockFreeCircuitBreaker breaker =
                new LockFreeCircuitBreaker(instanceId, FAILURE_THRESHOLD, TIMEOUT_MS, SUCCESS_THRESHOLD);
        breaker.onFailure();
        breaker.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState(), "前置状态：熔断器应已打开");
        Thread.sleep(TIMEOUT_MS + 50);
        return breaker;
    }

    @Test
    @DisplayName("870-001 半开态并发只放行一个探测请求")
    void onlyOneProbeIsAdmittedInHalfOpenState() throws InterruptedException {
        final LockFreeCircuitBreaker breaker = openedBreaker("probe-1");

        assertTrue(breaker.canExecute(), "第一个请求应被放行并转成半开态");
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState(),
                "第一个探测请求应把状态转成 HALF_OPEN");

        assertFalse(breaker.canExecute(), "探测还在进行时，第二个并发请求不应被放行");
        assertFalse(breaker.canExecute(), "同一轮探测里再多请求也不应被放行");
    }

    @Test
    @DisplayName("870-002 探测成功后释放探测位，允许下一次探测")
    void probeSlotIsReleasedAfterSuccess() throws InterruptedException {
        final LockFreeCircuitBreaker breaker = openedBreaker("probe-2");

        assertTrue(breaker.canExecute(), "第一个探测请求应被放行");
        breaker.onSuccess();

        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState(),
                "成功阈值是 2，一次成功后仍应停留在半开态");
        assertTrue(breaker.canExecute(), "上一次探测已回报成功，应允许下一次探测");

        breaker.onSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState(), "累计成功达到阈值后应关闭");
    }

    @Test
    @DisplayName("870-003 探测失败重新打开，后续请求不再放行")
    void probeFailureReopensTheBreaker() throws InterruptedException {
        final LockFreeCircuitBreaker breaker = openedBreaker("probe-3");

        assertTrue(breaker.canExecute(), "第一个探测请求应被放行");
        breaker.onFailure();

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState(), "探测失败应重新打开");
        assertFalse(breaker.canExecute(), "刚重新打开时不应放行新请求");
    }

    @Test
    @DisplayName("870-004 探测方没有回报时不会永久拒绝")
    void stalledProbeEventuallyAllowsAnotherAttempt() throws InterruptedException {
        final LockFreeCircuitBreaker breaker = openedBreaker("probe-4");

        assertTrue(breaker.canExecute(), "第一个探测请求应被放行");
        assertFalse(breaker.canExecute(), "探测期间不应继续放行");

        // 模拟探测方异常退出、既没回调 onSuccess 也没回调 onFailure
        Thread.sleep(TIMEOUT_MS + 50);
        assertTrue(breaker.canExecute(), "探测超过 timeout 仍未回报时，应允许下一轮探测（避免永久拒绝）");
    }
}

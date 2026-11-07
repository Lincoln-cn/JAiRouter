package org.unreal.modelrouter.circuitbreaker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.LockFreeCircuitBreaker;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #1010：熔断器记录「最近一次真实失败」详情（reason / exceptionClass / at）与打开时刻，
 * 供 503 响应体、管理端点与复位响应回显真实失败原因。
 *
 * <p>此前只记 lastFailureTime，503 的汇总文案（「全部实例处于熔断状态」）把真实失败完全遮蔽。</p>
 *
 * @author JAiRouter Team
 * @since v3.8.0
 */
@DisplayName("熔断器失败详情记录（issue #1010）")
class CircuitBreakerFailureDetailTest {

    @Test
    @DisplayName("FD-100 onFailure(reason, exceptionClass) 后 getStateDetail() 暴露失败详情四键")
    void failureDetailIsRecorded() {
        LockFreeCircuitBreaker cb = new LockFreeCircuitBreaker("fd-100", 3, 5000, 2);

        cb.onFailure("CONNECT_FAILED", "ConnectException");

        Map<String, Object> detail = cb.getStateDetail();
        assertEquals("CONNECT_FAILED", detail.get("lastFailureReason"));
        assertEquals("ConnectException", detail.get("lastFailureExceptionClass"));
        assertNotNull(detail.get("lastFailureAt"), "必须记录失败时刻");
        assertTrue(((Number) detail.get("lastFailureAt")).longValue() > 0);
    }

    @Test
    @DisplayName("FD-101 达到阈值转 OPEN 时 openedAt > 0（未打开时为 0）")
    void openedAtSetWhenTransitionToOpen() {
        LockFreeCircuitBreaker cb = new LockFreeCircuitBreaker("fd-101", 2, 5000, 2);
        assertEquals(0L, ((Number) cb.getStateDetail().get("openedAt")).longValue());

        cb.onFailure("TIMEOUT", "TimeoutException");
        cb.onFailure("TIMEOUT", "TimeoutException");

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertTrue(((Number) cb.getStateDetail().get("openedAt")).longValue() > 0,
                "转 OPEN 必须记录打开时刻");
    }

    @Test
    @DisplayName("FD-102 reset() 清空失败详情与 openedAt（复位后回不到「为什么熔断过」）")
    void resetClearsFailureDetail() {
        LockFreeCircuitBreaker cb = new LockFreeCircuitBreaker("fd-102", 1, 5000, 2);
        cb.onFailure("CONNECT_FAILED", "ConnectException");
        assertNotNull(cb.getStateDetail().get("lastFailureReason"));

        cb.reset();

        Map<String, Object> detail = cb.getStateDetail();
        assertNull(detail.get("lastFailureReason"));
        assertNull(detail.get("lastFailureExceptionClass"));
        assertNull(detail.get("lastFailureAt"));
        assertEquals(0L, ((Number) detail.get("openedAt")).longValue());
    }

    @Test
    @DisplayName("FD-103 无参 onFailure() 保持旧语义（详情 reason/exceptionClass 为 null，但时刻有值）")
    void legacyOnFailureStillRecordsTimestamp() {
        LockFreeCircuitBreaker cb = new LockFreeCircuitBreaker("fd-103", 3, 5000, 2);

        cb.onFailure();

        Map<String, Object> detail = cb.getStateDetail();
        assertNull(detail.get("lastFailureReason"));
        assertNull(detail.get("lastFailureExceptionClass"));
        assertNotNull(detail.get("lastFailureAt"), "无参失败仍应记录时刻");
    }

    @Test
    @DisplayName("FD-104 成功不清除失败详情（恢复后仍可回看「为什么熔断过」）")
    void successDoesNotClearFailureDetail() {
        LockFreeCircuitBreaker cb = new LockFreeCircuitBreaker("fd-104", 3, 5000, 1);
        cb.onFailure("CONNECT_FAILED", "ConnectException");

        cb.onSuccess();

        assertEquals("CONNECT_FAILED", cb.getStateDetail().get("lastFailureReason"));
    }

    @Test
    @DisplayName("FD-105 接口 default onFailure(reason, ec) 委托 onFailure()（未覆写的实现不破）")
    void defaultMethodDelegatesToLegacyOnFailure() {
        final boolean[] legacyCalled = {false};
        CircuitBreaker legacy = new CircuitBreaker() {
            @Override
            public boolean canExecute() {
                return true;
            }

            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure() {
                legacyCalled[0] = true;
            }

            @Override
            public State getState() {
                return State.CLOSED;
            }
        };

        legacy.onFailure("CONNECT_FAILED", "ConnectException");

        assertTrue(legacyCalled[0], "default 重载必须委托 onFailure()，未覆写的实现不能丢失败计数");
    }
}

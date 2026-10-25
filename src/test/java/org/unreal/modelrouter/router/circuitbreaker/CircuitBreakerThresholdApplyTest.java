package org.unreal.modelrouter.router.circuitbreaker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.persistence.jpa.repository.CircuitBreakerMetricsRepository;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 自适应阈值的落地效果（v3.5.4，#653）。
 *
 * <p>回归的是「阈值只出现在日志里」这个缺陷：调整之后实例真的按新阈值熔断；
 * 阈值没变时不能把已有的失败计数抹掉（旧实现每 5 分钟 reset 一次）。</p>
 */
class CircuitBreakerThresholdApplyTest {

    private static final String INSTANCE = "inst-adaptive";

    private CircuitBreakerManager newManager() {
        final CircuitBreakerManager manager = new CircuitBreakerManager();
        manager.initialize(new ModelRouterProperties());
        return manager;
    }

    private int intDetail(final CircuitBreakerManager manager, final String field) {
        final Map<String, Object> detail = manager.getCircuitBreakerDetail(INSTANCE);
        return ((Number) detail.get(field)).intValue();
    }

    @Test
    @DisplayName("CBT-001: 调整后按新阈值熔断（3 次失败即打开，而不是默认的 5 次）")
    void adjustedThresholdTakesEffect() {
        final CircuitBreakerManager manager = newManager();
        final CircuitBreaker before = manager.getCircuitBreaker(INSTANCE, null);
        assertEquals(CircuitBreaker.State.CLOSED, before.getState());

        assertTrue(manager.updateFailureThreshold(INSTANCE, null, 3));

        final CircuitBreaker after = manager.getCircuitBreaker(INSTANCE, null);
        assertEquals(3, intDetail(manager, "failureThreshold"));
        assertEquals(CircuitBreaker.State.CLOSED, after.getState());

        after.onFailure();
        after.onFailure();
        assertEquals(CircuitBreaker.State.CLOSED, after.getState(), "两次失败还不该打开");

        after.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, after.getState(), "第三次失败应当打开（新阈值 3）");
    }

    @Test
    @DisplayName("CBT-002: 阈值未变化时不重建熔断器，已有失败计数保留")
    void equalThresholdDoesNotResetState() {
        final CircuitBreakerManager manager = newManager();
        final CircuitBreaker breaker = manager.getCircuitBreaker(INSTANCE, null);
        breaker.onFailure();
        breaker.onFailure();

        final AdaptiveThresholdManager adaptive = new AdaptiveThresholdManager(
                mock(CircuitBreakerMetricsRepository.class), manager);

        assertTrue(adaptive.updateCircuitBreakerThreshold(INSTANCE, 5), "与全局阈值一致时应视为已满足");
        assertEquals(2, intDetail(manager, "failureCount"), "失败计数不该被清空");
    }

    @Test
    @DisplayName("CBT-003: 非法阈值被拒绝，且不产生覆盖")
    void rejectsNonPositiveThreshold() {
        final CircuitBreakerManager manager = newManager();
        assertFalse(manager.updateFailureThreshold(INSTANCE, null, 0));
        assertFalse(manager.updateFailureThreshold(INSTANCE, null, -1));
        manager.getCircuitBreaker(INSTANCE, null);
        assertEquals(5, intDetail(manager, "failureThreshold"));
    }

    @Test
    @DisplayName("CBT-004: clearAllCircuitBreakers 同时清掉自适应覆盖")
    void clearAllDropsOverrides() {
        final CircuitBreakerManager manager = newManager();
        manager.updateFailureThreshold(INSTANCE, null, 2);
        manager.clearAllCircuitBreakers();

        manager.getCircuitBreaker(INSTANCE, null);
        assertEquals(5, intDetail(manager, "failureThreshold"));
    }
}

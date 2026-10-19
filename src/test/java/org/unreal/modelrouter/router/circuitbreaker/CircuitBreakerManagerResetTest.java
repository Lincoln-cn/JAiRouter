package org.unreal.modelrouter.router.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CircuitBreakerManager 两种重置语义的单元测试。
 *
 * <p>区分：{@code resetCircuitBreaker} 把实例从注册表移除（配置变更后重建），
 * {@code resetCircuitBreakerInPlace} 保留实例并把状态置 CLOSED（运维手动重置）。</p>
 *
 * @author JAiRouter Team
 * @since 3.3.6
 */
@DisplayName("CircuitBreakerManager 重置语义")
class CircuitBreakerManagerResetTest {

    private CircuitBreakerManager manager;

    @BeforeEach
    void setUp() {
        manager = new CircuitBreakerManager();
    }

    @Test
    @DisplayName("就地重置：返回旧状态、保留实例、计数清零、阈值不变")
    void resetInPlaceKeepsEntry() {
        CircuitBreaker breaker = manager.getCircuitBreaker("uuid-1", "https://api.example.com");
        for (int i = 0; i < 5; i++) {
            breaker.onFailure();
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        CircuitBreaker.State previous = manager.resetCircuitBreakerInPlace("uuid-1");

        assertEquals(CircuitBreaker.State.OPEN, previous);
        assertEquals(1, manager.getCircuitBreakerCount(), "手动重置后实例必须仍留在监控视图中");
        Map<String, Object> detail = manager.getCircuitBreakerDetail("uuid-1");
        assertNotNull(detail);
        assertEquals("CLOSED", detail.get("state"));
        assertEquals(0, detail.get("failureCount"));
        assertEquals(5, detail.get("failureThreshold"), "手动重置只清状态，不重置阈值");
    }

    @Test
    @DisplayName("就地重置：实例不存在时返回 null，不抛异常")
    void resetInPlaceUnknownReturnsNull() {
        assertNull(manager.resetCircuitBreakerInPlace("not-registered"));
        assertEquals(0, manager.getCircuitBreakerCount());
    }

    @Test
    @DisplayName("移除式重置：返回旧状态并把实例从注册表删除（供配置变更后重建）")
    void removeResetDropsEntry() {
        CircuitBreaker breaker = manager.getCircuitBreaker("uuid-2", "https://api.example.com");
        for (int i = 0; i < 5; i++) {
            breaker.onFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, manager.resetCircuitBreaker("uuid-2", null));
        assertEquals(0, manager.getCircuitBreakerCount());
        assertNull(manager.getCircuitBreakerDetail("uuid-2"));
    }
}

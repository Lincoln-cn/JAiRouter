package org.unreal.modelrouter.persistence.store.persistence.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.store.persistence.StatePersistenceService;
import org.unreal.modelrouter.persistence.store.persistence.StatePersistenceService.StateType;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.LockFreeCircuitBreaker;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CircuitBreakerStatePersistenceAdapter} 的单元测试（issue #483 后续）。
 *
 * <p><b>为什么需要这个类</b>：主代码里曾有一个测试夹具端点
 * {@code POST /api/state-persistence/test/save-circuit-breaker/{id}}，它**手工伪造**
 * 一份 {@code state=OPEN, failureCount=5, successCount=0} 的 map 直接写进持久层 ——
 * 形状与真实数据一致，因此会被启动恢复链读回并真的把熔断器置为 OPEN。
 * 该夹具已从主代码删除（测试夹具不该出现在业务包里），其原本要验证的
 * "熔断器状态 → 持久层 → 恢复"这条链改由本类覆盖，且用的是**真实生产者**
 * （{@link LockFreeCircuitBreaker#persistState()}）而不是伪造值。</p>
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li>适配器写进持久层的是<b>真实状态</b>（state / failureCount / successCount / lastFailureTime）；</li>
 *   <li>非 {@link LockFreeCircuitBreaker} 的实例被拒绝且<b>不写</b>；</li>
 *   <li>恢复会真的改变运行态（这也是"不能在生产造数"的原因）。</li>
 * </ol>
 */
@DisplayName("CircuitBreakerStatePersistenceAdapter 状态持久化/恢复")
@ExtendWith(MockitoExtension.class)
class CircuitBreakerStatePersistenceAdapterTest {

    private static final String INSTANCE_ID = "inst-cb-1";

    @Mock
    private StatePersistenceService persistenceService;

    @InjectMocks
    private CircuitBreakerStatePersistenceAdapter adapter;

    /** 连续 3 次失败 ⇒ 按 failureThreshold=3 打开熔断器 */
    private LockFreeCircuitBreaker openBreaker() {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker(INSTANCE_ID, 3, 5000, 2);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();
        return breaker;
    }

    @Test
    @DisplayName("保存写入的是真实状态（不是伪造的固定值）")
    void savesRealStateNotFabricatedValues() {
        when(persistenceService.save(eq(StateType.CIRCUIT_BREAKER), anyString(), any()))
                .thenReturn(Mono.just(true));
        when(persistenceService.getTierName()).thenReturn("h2");

        StepVerifier.create(adapter.saveCircuitBreakerState(openBreaker()))
                .expectNext(true)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(persistenceService).save(eq(StateType.CIRCUIT_BREAKER), eq(INSTANCE_ID), stateCaptor.capture());

        Map<String, Object> saved = stateCaptor.getValue();
        assertEquals(INSTANCE_ID, saved.get("instanceId"));
        assertEquals("OPEN", saved.get("state"));
        assertEquals(3, saved.get("failureCount"), "失败次数必须来自真实熔断器（伪造值曾是固定 5）");
        assertEquals(0, saved.get("successCount"));
        assertTrue(saved.containsKey("lastFailureTime"), "恢复链需要 lastFailureTime，伪造的 map 里原本没有");
        assertTrue(saved.containsKey("timestamp"));
    }

    @Test
    @DisplayName("非 LockFreeCircuitBreaker 不写持久层")
    void rejectsNonLockFreeBreaker() {
        CircuitBreaker other = mock(CircuitBreaker.class);

        StepVerifier.create(adapter.saveCircuitBreakerState(other))
                .expectNext(false)
                .verifyComplete();

        verify(persistenceService, never()).save(any(), anyString(), any());
    }

    @Test
    @DisplayName("恢复会把持久层里的 OPEN 状态真的应用到熔断器")
    void restoreAppliesPersistedStateToBreaker() {
        Map<String, Object> persisted = openBreaker().persistState();
        when(persistenceService.load(StateType.CIRCUIT_BREAKER, INSTANCE_ID))
                .thenReturn(Mono.just(persisted));

        LockFreeCircuitBreaker target = new LockFreeCircuitBreaker(INSTANCE_ID, 3, 5000, 2);
        assertEquals(CircuitBreaker.State.CLOSED, target.getState(), "恢复前必须是 CLOSED，否则断言无意义");

        StepVerifier.create(adapter.restoreCircuitBreakerState(target))
                .expectNext(true)
                .verifyComplete();

        assertEquals(CircuitBreaker.State.OPEN, target.getState());
        assertEquals(3, target.getFailureCount());
        assertFalse(target.canExecute(), "OPEN 状态下不应放行请求");
    }

    @Test
    @DisplayName("持久层没有该实例状态时不改运行态")
    void restoreWithoutPersistedStateLeavesBreakerUntouched() {
        when(persistenceService.load(StateType.CIRCUIT_BREAKER, INSTANCE_ID))
                .thenReturn(Mono.just(Map.of()));

        LockFreeCircuitBreaker target = new LockFreeCircuitBreaker(INSTANCE_ID, 3, 5000, 2);

        StepVerifier.create(adapter.restoreCircuitBreakerState(target))
                .expectNext(false)
                .verifyComplete();

        assertEquals(CircuitBreaker.State.CLOSED, target.getState());
        assertEquals(0, target.getFailureCount());
    }
}

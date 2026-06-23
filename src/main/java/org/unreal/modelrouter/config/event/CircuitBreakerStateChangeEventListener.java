package org.unreal.modelrouter.config.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.service.CircuitBreakerConfigService;
import org.unreal.modelrouter.persistence.store.persistence.adapter.CircuitBreakerStatePersistenceAdapter;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;

/**
 * 熔断器状态变化事件监听器
 *
 * 异步处理状态变化事件，记录历史并持久化状态
 *
 * v2.6.13: 新增
 * v2.9.x: 添加状态持久化触发
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerStateChangeEventListener {

    private final CircuitBreakerConfigService circuitBreakerConfigService;
    private final CircuitBreakerStatePersistenceAdapter cbPersistenceAdapter;
    private final CircuitBreakerManager circuitBreakerManager;

    /**
     * 监听熔断器状态变化事件
     */
    @Async
    @EventListener
    public void onStateChange(final CircuitBreakerStateChangeEvent event) {
        log.debug("Received circuit breaker state change event: instance={}, {} -> {}",
                event.getInstanceId(), event.getPreviousState(), event.getCurrentState());

        try {
            // 1. 记录状态变更历史
            circuitBreakerConfigService.recordStateChange(
                    event.getInstanceId(),
                    event.getInstanceName(),
                    event.getServiceType(),
                    event.getPreviousState(),
                    event.getCurrentState(),
                    event.getTriggerReason(),
                    event.getFailureCount(),
                    event.getSuccessCount());

            // 2. 触发状态持久化
            CircuitBreaker cb = circuitBreakerManager.getCircuitBreaker(event.getInstanceId(), null);
            if (cb != null) {
                cbPersistenceAdapter.saveCircuitBreakerState(cb)
                        .doOnSuccess(saved -> log.debug("Circuit breaker state persisted: instance={}, saved={}",
                                event.getInstanceId(), saved))
                        .doOnError(e -> log.error("Failed to persist circuit breaker state: instance={}",
                                event.getInstanceId(), e))
                        .subscribe();
            }
        } catch (Exception e) {
            log.error("Failed to record circuit breaker state change: instance={}, error={}",
                    event.getInstanceId(), e.getMessage(), e);
        }
    }
}

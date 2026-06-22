package org.unreal.modelrouter.config.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.service.CircuitBreakerConfigService;

/**
 * 熔断器状态变化事件监听器
 * 
 * 异步处理状态变化事件，记录历史
 * 
 * v2.6.13: 新增
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerStateChangeEventListener {

    private final CircuitBreakerConfigService circuitBreakerConfigService;

    /**
     * 监听熔断器状态变化事件
     */
    @Async
    @EventListener
    public void onStateChange(final CircuitBreakerStateChangeEvent event) {
        log.debug("Received circuit breaker state change event: instance={}, {} -> {}",
                event.getInstanceId(), event.getPreviousState(), event.getCurrentState());

        try {
            circuitBreakerConfigService.recordStateChange(
                    event.getInstanceId(),
                    event.getInstanceName(),
                    event.getServiceType(),
                    event.getPreviousState(),
                    event.getCurrentState(),
                    event.getTriggerReason(),
                    event.getFailureCount(),
                    event.getSuccessCount());
        } catch (Exception e) {
            log.error("Failed to record circuit breaker state change: instance={}, error={}",
                    event.getInstanceId(), e.getMessage(), e);
        }
    }
}

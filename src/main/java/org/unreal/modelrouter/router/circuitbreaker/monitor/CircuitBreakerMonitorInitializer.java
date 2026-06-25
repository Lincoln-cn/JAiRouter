package org.unreal.modelrouter.router.circuitbreaker.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;

/**
 * 熔断器监控初始化器
 * 在应用启动完成后注入监控服务，避免循环依赖
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class CircuitBreakerMonitorInitializer {

    private final CircuitBreakerManager circuitBreakerManager;
    private final CircuitBreakerMonitorService monitorService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        circuitBreakerManager.setMonitorService(monitorService);
        log.info("CircuitBreakerMonitorService injected into CircuitBreakerManager");
    }
}

package org.unreal.modelrouter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;

import jakarta.annotation.PostConstruct;

/**
 * 熔断器配置
 * 
 * 通过 setter 注入 ApplicationEventPublisher 避免循环依赖
 * 
 * v2.6.13: 新增
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerConfiguration {

    private final CircuitBreakerManager circuitBreakerManager;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        // 注入事件发布器
        circuitBreakerManager.setEventPublisher(eventPublisher);
        log.info("CircuitBreakerManager initialized with ApplicationEventPublisher");
    }
}

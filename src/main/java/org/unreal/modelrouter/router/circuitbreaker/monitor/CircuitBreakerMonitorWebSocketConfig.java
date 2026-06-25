package org.unreal.modelrouter.router.circuitbreaker.monitor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import java.util.Map;

/**
 * 熔断器监控 WebSocket 配置
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerMonitorWebSocketConfig {

    private final CircuitBreakerMonitorWebSocketHandler circuitBreakerMonitorWebSocketHandler;

    @Bean
    public HandlerMapping circuitBreakerMonitorWebSocketMapping() {
        Map<String, Object> map = Map.of(
                "/ws/circuit-breaker-monitor", circuitBreakerMonitorWebSocketHandler
        );

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);
        return mapping;
    }
}

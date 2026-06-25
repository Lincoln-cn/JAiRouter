package org.unreal.modelrouter.router.loadbalancer.monitor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * 路由监控 WebSocket 配置
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Configuration
@RequiredArgsConstructor
public class RoutingMonitorWebSocketConfig {

    private final RoutingMonitorWebSocketHandler routingMonitorWebSocketHandler;

    @Bean
    public HandlerMapping routingMonitorWebSocketMapping() {
        Map<String, Object> map = Map.of(
                "/ws/routing-monitor", routingMonitorWebSocketHandler
        );

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1); // 高优先级
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}

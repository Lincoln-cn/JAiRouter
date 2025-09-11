package org.unreal.modelrouter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.unreal.modelrouter.websocket.AdminWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket配置类
 * 配置Web管理界面的WebSocket连接
 */
@Slf4j
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(AdminWebSocketHandler adminWebSocketHandler) {
        log.info("配置WebSocket处理器映射");
        
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/admin/ws", adminWebSocketHandler);
        
        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setUrlMap(map);
        handlerMapping.setOrder(1);
        
        return handlerMapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
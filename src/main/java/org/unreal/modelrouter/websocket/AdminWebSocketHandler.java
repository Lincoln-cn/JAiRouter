package org.unreal.modelrouter.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web管理界面WebSocket处理器
 * 处理实时数据推送和双向通信
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    private static final SecureRandom secureRandom = new SecureRandom();
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("WebSocket连接建立: sessionId={}", sessionId);
        
        // 保存会话
        activeSessions.put(sessionId, session);
        
        // 处理接收到的消息
        Mono<Void> input = session.receive()
                .doOnNext(message -> handleIncomingMessage(session, message))
                .doOnError(error -> log.error("WebSocket接收消息错误: sessionId={}", sessionId, error))
                .then();

        // 发送实时数据
        Mono<Void> output = session.send(
                createMonitoringDataStream()
                        .map(data -> session.textMessage(toJson(data)))
                        .doOnError(error -> log.error("WebSocket发送数据错误: sessionId={}", sessionId, error))
        );

        // 处理连接关闭
        return Mono.zip(input, output)
                .doFinally(signalType -> {
                    activeSessions.remove(sessionId);
                    log.info("WebSocket连接关闭: sessionId={}, signal={}", sessionId, signalType);
                })
                .then();
    }

    /**
     * 处理接收到的消息
     */
    private void handleIncomingMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            log.debug("收到WebSocket消息: sessionId={}, payload={}", session.getId(), payload);
            
            // 这里可以根据消息类型进行不同的处理
            // 例如：客户端请求特定数据、配置更新等
            
        } catch (Exception e) {
            log.error("处理WebSocket消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 创建监控数据流
     */
    private Flux<Map<String, Object>> createMonitoringDataStream() {
        return Flux.interval(Duration.ofSeconds(5))
                .map(tick -> generateMonitoringData())
                .onErrorContinue((error, item) -> 
                    log.error("生成监控数据失败: tick={}", item, error));
    }

    /**
     * 生成模拟监控数据
     */
    private Map<String, Object> generateMonitoringData() {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("type", "monitoring");
        
        // 系统指标
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cpuUsage", secureRandom.nextDouble() * 100);
        metrics.put("memoryUsage", secureRandom.nextDouble() * 100);
        metrics.put("requestsPerSecond", secureRandom.nextDouble() * 50);
        metrics.put("responseTime", secureRandom.nextDouble() * 200 + 50);
        metrics.put("errorRate", secureRandom.nextDouble() * 0.05);
        
        data.put("metrics", metrics);
        
        // 服务状态
        Map<String, Object> services = new HashMap<>();
        services.put("totalServices", 5);
        services.put("activeServices", 4 + (secureRandom.nextDouble() > 0.8 ? 1 : 0));
        services.put("failedServices", secureRandom.nextDouble() > 0.9 ? 1 : 0);
        
        data.put("services", services);
        
        return data;
    }

    /**
     * 广播消息给所有连接的客户端
     */
    public void broadcast(Map<String, Object> message) {
        String json = toJson(message);
        activeSessions.values().forEach(session -> {
            if (session.isOpen()) {
                session.send(Mono.just(session.textMessage(json)))
                        .subscribe(
                            null,
                            error -> log.error("广播消息失败: sessionId={}", session.getId(), error)
                        );
            }
        });
    }

    /**
     * 发送消息给特定会话
     */
    public void sendToSession(String sessionId, Map<String, Object> message) {
        WebSocketSession session = activeSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            String json = toJson(message);
            session.send(Mono.just(session.textMessage(json)))
                    .subscribe(
                        null,
                        error -> log.error("发送消息失败: sessionId={}", sessionId, error)
                    );
        }
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeSessions.size();
    }

    /**
     * 将对象转换为JSON字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            return "{}";
        }
    }
}
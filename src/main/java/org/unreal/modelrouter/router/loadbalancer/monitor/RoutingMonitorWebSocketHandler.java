package org.unreal.modelrouter.router.loadbalancer.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.unreal.modelrouter.common.cluster.ClusterEventBus;
import org.unreal.modelrouter.common.util.InstanceIdentity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;

/**
 * 路由监控 WebSocket 处理器
 * 实时推送路由选择事件到前端
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoutingMonitorWebSocketHandler implements WebSocketHandler {

    /** 跨副本广播通道名（#164） */
    private static final String EVENT_CHANNEL = "routing-monitor";

    private final RoutingMonitorService monitorService;
    private final ObjectMapper objectMapper;
    private final ClusterEventBus clusterEventBus;

    /**
     * 事件广播器 - 有界多播缓冲（256），溢出由 emit 失败路径丢弃，避免无界占内存
     */
    private final Sinks.Many<String> eventSink = Sinks.many().multicast()
            .onBackpressureBuffer(256, false);

    /**
     * 注册事件回调到 EventRecorder
     */
    public void registerEventCallback() {
        monitorService.getEventRecorder().setEventCallback(event -> {
            try {
                String json = objectMapper.writeValueAsString(event);
                Sinks.EmitResult result = eventSink.tryEmitNext(json);
                if (result.isFailure()) {
                    log.debug("Failed to emit routing event: {}", result);
                }
                // 同时广播给其它副本：本地 Sink 只覆盖连在本副本上的客户端（#164）。
                // 单副本部署下这是无操作（LocalClusterEventBus）。
                clusterEventBus.publish(EVENT_CHANNEL, json);
            } catch (Exception e) {
                log.warn("Failed to serialize routing event: {}", e.getMessage());
            }
        });
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket client connected: {}", session.getId());

        // 注册事件回调（首次连接时）
        registerEventCallback();

        // 发送初始状态
        Mono<String> initialState = Mono.fromCallable(() -> {
            try {
                InitialState state = new InitialState(
                        "connected",
                        monitorService.getStatusSummary(),
                        monitorService.getStatsAggregator().getAllStats()
                );
                return objectMapper.writeValueAsString(state);
            } catch (Exception e) {
                return "{\"type\":\"error\",\"message\":\"Failed to serialize initial state\"}";
            }
        });

        // 心跳流 - 每30秒发送一次
        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(seq -> "{\"type\":\"heartbeat\",\"seq\":" + seq + "}");

        // 事件流 = 本副本事件 + 其它副本广播来的事件。
        // 广播侧已按来源副本做过回环过滤，本副本发出的事件不会在这里重复出现。
        // 两类事件都补出来源副本标识，前端/运维才能判断某条事件来自哪个副本（#181）。
        final Flux<String> localEvents = eventSink.asFlux()
                .map(json -> ClusterEventBus.withSourceInstance(
                        json, InstanceIdentity.id(), objectMapper));
        final Flux<String> remoteEvents = clusterEventBus.subscribe(EVENT_CHANNEL)
                .map(event -> ClusterEventBus.withSourceInstance(
                        event.payload(), event.sourceInstance(), objectMapper));
        Flux<String> eventStream = Flux.merge(localEvents, remoteEvents)
                .onBackpressureLatest();

        // 合并初始状态、心跳和事件流
        Flux<String> output = Flux.concat(
                initialState.flux(),
                Flux.merge(heartbeat, eventStream)
        );

        return session.send(
                output.map(session::textMessage)
        ).and(
                session.receive()
                        .doOnNext(message -> {
                            String payload = message.getPayloadAsText();
                            log.debug("Received from client: {}", payload);
                            handleClientMessage(payload);
                        })
                        .doOnError(e -> log.warn("WebSocket error: {}", e.getMessage()))
                        .doOnTerminate(() -> log.info("WebSocket client disconnected: {}", session.getId()))
        );
    }

    /**
     * 处理客户端消息
     */
    private void handleClientMessage(String payload) {
        try {
            ClientMessage message = objectMapper.readValue(payload, ClientMessage.class);

            switch (message.type()) {
                case "pause" -> {
                    if (message.serviceType() != null) {
                        monitorService.pauseService(message.serviceType());
                    } else {
                        monitorService.pause();
                    }
                }
                case "resume" -> {
                    if (message.serviceType() != null) {
                        monitorService.resumeService(message.serviceType());
                    } else {
                        monitorService.resume();
                    }
                }
                case "updateConfig" -> {
                    if (message.sampleRate() != null) {
                        monitorService.updateSampleRate(message.sampleRate());
                    }
                    if (message.historySize() != null) {
                        monitorService.updateHistorySize(message.historySize());
                    }
                }
                default -> log.debug("Unknown message type: {}", message.type());
            }
        } catch (Exception e) {
            log.warn("Failed to parse client message: {}", e.getMessage());
        }
    }

    // ==================== DTO ====================

    public record InitialState(
            String type,
            RoutingMonitorService.MonitorStatusSummary status,
            Map<String, RoutingStatsAggregator.ServiceRoutingStats> stats
    ) {}

    public record ClientMessage(
            String type,
            String serviceType,
            Double sampleRate,
            Integer historySize
    ) {}
}

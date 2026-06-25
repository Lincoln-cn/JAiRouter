package org.unreal.modelrouter.router.circuitbreaker.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * 熔断器监控 WebSocket 处理器
 * 实时推送熔断器事件到前端
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerMonitorWebSocketHandler implements WebSocketHandler {

    private final CircuitBreakerMonitorService monitorService;
    private final ObjectMapper objectMapper;

    private final Sinks.Many<String> eventSink = Sinks.many().multicast().onBackpressureBuffer();

    public void registerEventCallback() {
        monitorService.getEventRecorder().setEventCallback(event -> {
            try {
                String json = objectMapper.writeValueAsString(event);
                Sinks.EmitResult result = eventSink.tryEmitNext(json);
                if (result.isFailure()) {
                    log.debug("Failed to emit circuit breaker event: {}", result);
                }
            } catch (Exception e) {
                log.warn("Failed to serialize circuit breaker event: {}", e.getMessage());
            }
        });
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket client connected to circuit breaker monitor: {}", session.getId());

        registerEventCallback();

        Mono<String> initialState = Mono.fromCallable(() -> {
            try {
                InitialState state = new InitialState(
                        "connected",
                        monitorService.getStatusSummary()
                );
                return objectMapper.writeValueAsString(state);
            } catch (Exception e) {
                return "{\"type\":\"error\",\"message\":\"Failed to serialize initial state\"}";
            }
        });

        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(seq -> "{\"type\":\"heartbeat\",\"seq\":" + seq + "}");

        Flux<String> eventStream = eventSink.asFlux()
                .onBackpressureLatest();

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

    private void handleClientMessage(String payload) {
        try {
            ClientMessage message = objectMapper.readValue(payload, ClientMessage.class);

            switch (message.type()) {
                case "pause" -> monitorService.pause();
                case "resume" -> monitorService.resume();
                default -> log.debug("Unknown message type: {}", message.type());
            }
        } catch (Exception e) {
            log.warn("Failed to parse client message: {}", e.getMessage());
        }
    }

    public record InitialState(
            String type,
            CircuitBreakerMonitorService.MonitorStatusSummary status
    ) {}

    public record ClientMessage(String type) {}
}

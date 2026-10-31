package org.unreal.modelrouter.router.circuitbreaker.monitor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.unreal.modelrouter.common.cluster.ClusterEventBus;

/**
 * #916：熔断监控 WebSocket 的 pause / resume 只允许管理员。
 */
class CircuitBreakerMonitorWebSocketHandlerAuthorizationTest {

    private CircuitBreakerMonitorService monitorService;
    private CircuitBreakerMonitorWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        monitorService = mock(CircuitBreakerMonitorService.class);
        handler = new CircuitBreakerMonitorWebSocketHandler(monitorService, new ObjectMapper(),
                mock(ClusterEventBus.class));
    }

    @Test
    @DisplayName("916-201 非管理员发 pause 时不得暂停熔断监控")
    void lowPrivilegeCallerCannotPause() {
        handler.handleClientMessage(mock(WebSocketSession.class), false, "{\"type\":\"pause\"}");

        verify(monitorService, never()).pause();
    }

    @Test
    @DisplayName("916-202 非管理员发 resume 时不得恢复熔断监控")
    void lowPrivilegeCallerCannotResume() {
        handler.handleClientMessage(mock(WebSocketSession.class), false, "{\"type\":\"resume\"}");

        verify(monitorService, never()).resume();
    }

    @Test
    @DisplayName("916-203 管理员可以暂停与恢复")
    void adminCanControl() {
        handler.handleClientMessage(mock(WebSocketSession.class), true, "{\"type\":\"pause\"}");
        handler.handleClientMessage(mock(WebSocketSession.class), true, "{\"type\":\"resume\"}");

        verify(monitorService).pause();
        verify(monitorService).resume();
    }
}

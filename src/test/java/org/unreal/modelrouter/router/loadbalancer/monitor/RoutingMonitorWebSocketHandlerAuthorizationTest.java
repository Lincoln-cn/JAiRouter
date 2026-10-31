package org.unreal.modelrouter.router.loadbalancer.monitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * #916：路由监控 WebSocket 的 pause / updateConfig 只允许管理员。
 *
 * <p>修复前：任何通过 {@code /ws/**} 认证的调用方（包括只带 {@code ROLE_CHAT} 的 API Key）
 * 都能暂停采样、改采样率；修复后：控制入口先收到 {@code handle()} 在握手时算好的管理员判定，
 * 非管理员只收事件、不生效控制消息。
 */
class RoutingMonitorWebSocketHandlerAuthorizationTest {

    private RoutingMonitorService monitorService;
    private RoutingMonitorWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        monitorService = mock(RoutingMonitorService.class);
        handler = new RoutingMonitorWebSocketHandler(monitorService, new ObjectMapper(),
                mock(ClusterEventBus.class));
    }

    @Test
    @DisplayName("916-101 非管理员发 pause 时不得暂停监控")
    void lowPrivilegeCallerCannotPause() {
        handler.handleClientMessage(mock(WebSocketSession.class), false, "{\"type\":\"pause\"}");

        verify(monitorService, never()).pause();
        verify(monitorService, never()).pauseService(any());
    }

    @Test
    @DisplayName("916-102 非管理员也改不了采样率与历史窗口")
    void lowPrivilegeCallerCannotUpdateConfig() {
        handler.handleClientMessage(mock(WebSocketSession.class), false,
                "{\"type\":\"updateConfig\",\"sampleRate\":0.01,\"historySize\":5}");

        verify(monitorService, never()).updateSampleRate(anyDouble());
        verify(monitorService, never()).updateHistorySize(anyInt());
    }

    @Test
    @DisplayName("916-103 管理员可以暂停监控")
    void adminCanPause() {
        handler.handleClientMessage(mock(WebSocketSession.class), true, "{\"type\":\"pause\"}");

        verify(monitorService).pause();
    }

    @Test
    @DisplayName("916-104 管理员可以改采样率与历史窗口")
    void adminCanUpdateConfig() {
        handler.handleClientMessage(mock(WebSocketSession.class), true,
                "{\"type\":\"updateConfig\",\"sampleRate\":0.25,\"historySize\":500}");

        verify(monitorService).updateSampleRate(0.25d);
        verify(monitorService).updateHistorySize(500);
    }

    @Test
    @DisplayName("916-105 未知消息类型不触发任何控制动作")
    void unknownMessageTypeIsIgnored() {
        handler.handleClientMessage(mock(WebSocketSession.class), false, "{\"type\":\"ping\"}");

        verify(monitorService, never()).pause();
        verify(monitorService, never()).resume();
        verify(monitorService, never()).updateSampleRate(anyDouble());
    }
}

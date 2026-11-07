package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.router.model.NoAvailableInstanceException;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #1010：熔断 503（{@link NoAvailableInstanceException}）的客户端可见形状 ——
 * lastFailure 摘要放 {@code RouterResponse.data}，message 仍只给「请求处理失败: …」外部文案。
 *
 * <p>把真实异常喂给真实处理器（照 {@code DownstreamUnavailableResponseTest} 的形态）。</p>
 *
 * @author JAiRouter Team
 * @since v3.8.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("熔断 503 的 lastFailure 渲染（issue #1010）")
class NoAvailableInstanceDataRenderTest {

    @Mock
    private ErrorTracker errorTracker;

    private ReactiveGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactiveGlobalExceptionHandler(errorTracker);
    }

    @Test
    @DisplayName("NAI-200 503 响应体 data 带 lastFailure（reason/at/exceptionClass）")
    void lastFailureRenderedInData() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/config/instance/chat").build());
        final Map<String, Object> lastFailure = Map.of(
                "instanceId", "inst-a",
                "instanceName", "chat-a",
                "reason", "CONNECT_FAILED",
                "at", 1730000000000L,
                "exceptionClass", "ConnectException");
        final NoAvailableInstanceException exception = new NoAvailableInstanceException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No available instances (all in circuit breaker state) for model 'qwen3:4b'",
                lastFailure);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("CONNECT_FAILED"), "data 必须含真实失败原因，实际: " + body);
        assertTrue(body.contains("ConnectException"), "data 必须含异常类名，实际: " + body);
        assertTrue(body.contains("1730000000000"), "data 必须含失败时刻，实际: " + body);
    }

    @Test
    @DisplayName("NAI-201 lastFailure 为 null 时形状不变（data 空），且不崩")
    void nullLastFailureKeepsShape() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/config/instance/chat").build());
        final NoAvailableInstanceException exception = new NoAvailableInstanceException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No available instances (all in circuit breaker state) for model 'qwen3:4b'",
                null);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("503"), "错误码应为 503，实际: " + body);
        assertFalse(body.contains("nullnull"), "data 为 null 时不得拼出垃圾，实际: " + body);
    }

    private static String bodyOf(final ServerWebExchange exchange) {
        return ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block();
    }
}

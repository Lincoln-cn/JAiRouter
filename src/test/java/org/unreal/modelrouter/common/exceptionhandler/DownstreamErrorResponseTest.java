package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #717：下游错误的客户端可见形状。
 *
 * <p>此前非流式路径把下游错误包成无 reason 的 {@code ResponseStatusException}，客户端只剩状态行；
 * 本用例把带上游语义的 {@link DownstreamServiceException} 喂给真实处理器，
 * 断言两种协议面（{@code /v1/**} 与其余路径）都能看到上游消息、上游错误码与 {@code Retry-After}。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("下游错误的客户端可见形状（#717）")
class DownstreamErrorResponseTest {

    @Mock
    private ErrorTracker errorTracker;

    private ReactiveGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactiveGlobalExceptionHandler(errorTracker);
    }

    @Test
    @DisplayName("/v1 面：状态码与上游错误语义透出，原 RouterResponse 外壳不泄漏")
    void v1FaceKeepsUpstreamSemantics() {
        final ServerWebExchange exchange = exchangeFor("/v1/chat/completions");
        final DownstreamServiceException exception = new DownstreamServiceException(
                "下游服务错误: context_length_exceeded", HttpStatus.BAD_REQUEST,
                "context_length_exceeded", null);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("context_length_exceeded"), "客户端必须看到上游语义，实际: " + body);
        assertTrue(body.contains("invalid_request_error") || body.contains("error"),
                "仍应是协议形状的错误体，实际: " + body);
    }

    @Test
    @DisplayName("/api 面：错误码取上游 code，消息含上游语义")
    void apiFaceKeepsUpstreamSemantics() {
        final ServerWebExchange exchange = exchangeFor("/api/v1/chat/completions");
        final DownstreamServiceException exception = new DownstreamServiceException(
                "下游服务错误: context_length_exceeded", HttpStatus.BAD_REQUEST,
                "context_length_exceeded", null);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("context_length_exceeded"), "实际: " + body);
        assertTrue(body.contains("\"errorCode\":\"context_length_exceeded\""),
                "错误码应取上游 code 而不是 400，实际: " + body);
    }

    @Test
    @DisplayName("Retry-After 秒数原样下发")
    void retryAfterHeaderIsForwarded() {
        final ServerWebExchange exchange = exchangeFor("/api/v1/chat/completions");
        final DownstreamServiceException exception = new DownstreamServiceException(
                "下游服务错误: rate limit reached", HttpStatus.TOO_MANY_REQUESTS,
                "rate_limit_exceeded", 12L);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("12", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    // ==================== 辅助 ====================

    private ServerWebExchange exchangeFor(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private String bodyOf(final ServerWebExchange exchange) {
        return ((org.springframework.mock.http.server.reactive.MockServerHttpResponse)
                exchange.getResponse()).getBodyAsString().block();
    }
}

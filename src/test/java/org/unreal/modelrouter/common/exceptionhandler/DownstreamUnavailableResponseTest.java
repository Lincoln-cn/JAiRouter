package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #682：下游连接失败与超时的客户端可见形状。
 *
 * <p>此前 {@code WebClientRequestException} / 超时异常不匹配任何专项分支，落到兜底
 * 「系统异常 500」，并且把 {@code ex.getMessage()}（含下游地址）写进 {@code /v1} 错误体。
 * 本用例把真实异常喂给真实处理器，断言状态码是 502/504、且正文不含内部实现细节。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("下游连接失败与超时的响应形状（#682）")
class DownstreamUnavailableResponseTest {

    private static final String DOWNSTREAM_URL = "http://10.255.255.1:81/v1/chat/completions";

    @Mock
    private ErrorTracker errorTracker;

    private ReactiveGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactiveGlobalExceptionHandler(errorTracker);
    }

    @Test
    @DisplayName("DS-682-001 连接被拒回 502，且不回显下游地址与异常原文")
    void connectionFailureMapsToBadGateway() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());
        final WebClientRequestException exception = new WebClientRequestException(
                new ConnectException("Connection refused: " + DOWNSTREAM_URL),
                HttpMethod.POST,
                URI.create(DOWNSTREAM_URL),
                HttpHeaders.EMPTY);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode(),
                "连不上下游是网关侧可重试故障，客户端应按 502 处理");
        final String body = bodyOf(exchange);
        assertTrue(body.contains("502"), "错误码应为 502，实际: " + body);
        assertTrue(body.contains("api_error"), "5xx 在协议面应映射为 api_error，实际: " + body);
        assertFalse(body.contains("10.255.255.1"), "错误体不得泄漏下游地址，实际: " + body);
        assertFalse(body.contains("Connection refused"), "错误体不得回显异常原文，实际: " + body);
    }

    @Test
    @DisplayName("DS-682-002 传输层读超时回 504")
    void readTimeoutMapsToGatewayTimeout() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());

        StepVerifier.create(handler.handle(exchange, io.netty.handler.timeout.ReadTimeoutException.INSTANCE))
                .verifyComplete();

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode(),
                "等不到上游应回 504，而不是 500");
        final String body = bodyOf(exchange);
        assertTrue(body.contains("504"), "错误码应为 504，实际: " + body);
        assertTrue(body.contains("api_error"), "5xx 在协议面应映射为 api_error，实际: " + body);
    }

    @Test
    @DisplayName("DS-682-003 整体超时（Timed out）同样回 504 且不带超时时长原文")
    void overallTimeoutMapsToGatewayTimeout() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());
        final TimeoutException exception = new TimeoutException("Did not observe any item within 60000ms");

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertFalse(body.contains("60000ms"), "错误体不得回显内部超时配置，实际: " + body);
    }

    @Test
    @DisplayName("DS-682-004 控制台面同样按 502 返回，保持 RouterResponse 形状")
    void consolePathKeepsRouterResponseShape() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/chat/completions").build());
        final WebClientRequestException exception = new WebClientRequestException(
                new ConnectException("Connection refused"),
                HttpMethod.POST,
                URI.create(DOWNSTREAM_URL),
                HttpHeaders.EMPTY);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("errorCode"), "控制台面应保留 RouterResponse 的 errorCode 字段，实际: " + body);
    }

    private static String bodyOf(final ServerWebExchange exchange) {
        return ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block();
    }
}

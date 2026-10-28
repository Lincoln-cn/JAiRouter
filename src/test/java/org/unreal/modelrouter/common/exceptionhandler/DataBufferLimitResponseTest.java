package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #700：下游响应体超过网关内存上限时的客户端可见形状。
 *
 * <p>此前 {@code DataBufferLimitException} 落到全局处理器的「系统异常 500」兜底，
 * 调用方看到的是服务端故障、且错误文案里没有可操作信息。本用例把真实异常喂给真实处理器，
 * 断言状态码是 502、错误码是 {@code 502}、文案点名可调参数。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("下游响应体超内存上限的响应形状（#700）")
class DataBufferLimitResponseTest {

    @Mock
    private ErrorTracker errorTracker;

    private ReactiveGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactiveGlobalExceptionHandler(errorTracker);
    }

    @Test
    @DisplayName("DATA-001 超上限回 502 且文案点名可调参数")
    void dataBufferLimitMapsToBadGateway() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());
        final DataBufferLimitException exception =
                new DataBufferLimitException("Exceeded limit on max bytes to buffer : 262144");

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode(),
                "容量限制不是下游故障，也不该让调用方按 500 处理");
        final String body = bodyOf(exchange);
        assertTrue(body.contains("502"), "错误码应为 502，实际: " + body);
        assertTrue(body.contains("max-in-memory-size-bytes"),
                "文案必须点名可调参数，运维才知道怎么改，实际: " + body);
    }

    private static String bodyOf(final ServerWebExchange exchange) {
        return ((org.springframework.mock.http.server.reactive.MockServerHttpResponse) exchange.getResponse())
                .getBodyAsString().block();
    }
}

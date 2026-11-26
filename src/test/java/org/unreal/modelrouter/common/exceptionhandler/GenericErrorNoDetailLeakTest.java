package org.unreal.modelrouter.common.exceptionhandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.common.exception.ClientFacingException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;

import reactor.test.StepVerifier;

/**
 * #856：兜底 500 只给固定文案，异常原文只进日志。
 *
 * <p>此前两个兜底分支都把 {@code ex.getMessage()} 拼进响应体（「系统异常: …」），
 * 内部类名、SQL 片段、文件路径都可能被带出去。本用例把带敏感文本的异常喂给真实处理器，
 * 断言客户端可见体不含原文、不含类名，而日志侧仍然记录异常。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("兜底 500 不回显异常原文（#856）")
class GenericErrorNoDetailLeakTest {

    private static final String SECRET = "jdbc:h2:file:./data/jairouter;user=sa;password=SuperSecret123";

    @Mock
    private ErrorTracker errorTracker;

    private ReactiveGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactiveGlobalExceptionHandler(errorTracker);
    }

    @Test
    @DisplayName("856-001 /v1 面兜底 500 不回显异常原文与类名")
    void genericFailureOnProtocolFaceHidesDetails() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());
        final IllegalStateException exception = new IllegalStateException("底层失败: " + SECRET);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("系统异常"), "文案应能看出是服务端兜底，实际: " + body);
        assertFalse(body.contains("SuperSecret123"), "错误体不得回显异常原文，实际: " + body);
        assertFalse(body.contains("jdbc:h2"), "错误体不得回显连接串，实际: " + body);
        assertFalse(body.contains("IllegalStateException"), "错误体不得回显异常类名，实际: " + body);
    }

    @Test
    @DisplayName("856-002 非 /v1 面兜底 500 同样只给固定文案")
    void genericFailureOnConsoleFaceHidesDetails() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/models").build());
        final RuntimeException exception = new RuntimeException("查询失败: " + SECRET);

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("系统异常"), "文案应能看出是服务端兜底，实际: " + body);
        assertFalse(body.contains("SuperSecret123"), "错误体不得回显异常原文，实际: " + body);
        assertFalse(body.contains("RuntimeException"), "错误体不得回显异常类名，实际: " + body);
    }

    @Test
    @DisplayName("856-003 同步版处理器（ServerExceptionHandler）同样不回显原文")
    void syncHandlerHidesDetails() {
        final ServerExceptionHandler syncHandler = new ServerExceptionHandler(errorTracker);
        final RuntimeException exception = new RuntimeException("查询失败: " + SECRET);

        final var response = syncHandler.handleException(exception);

        assertEquals("500", response.getErrorCode());
        assertTrue(response.getMessage().contains("系统异常"), "实际: " + response.getMessage());
        assertFalse(response.getMessage().contains("SuperSecret123"),
                "兜底文案不得回显异常原文，实际: " + response.getMessage());
    }

    // ==================== #1197：参数类异常不再回显原文 ====================

    @Test
    @DisplayName("1197-001 请求体解析异常：字段名与期望类型不外显")
    void webInputExceptionHidesParseDetail() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/models").build());
        final ServerWebInputException exception = new ServerWebInputException(
                "JSON parse error: Cannot deserialize value of type `int` from String \"abc\"");

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("请求体无效或缺失"), "实际: " + body);
        assertFalse(body.contains("Cannot deserialize"), "不得回显解析细节，实际: " + body);
        assertFalse(body.contains("abc"), "不得回显请求体片段，实际: " + body);
    }

    @Test
    @DisplayName("1197-002 参数校验异常：原文只进日志")
    void illegalArgumentExceptionHidesDetail() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/models").build());
        final IllegalArgumentException exception =
                new IllegalArgumentException("'username' must not be null");

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("参数验证失败"), "实际: " + body);
        assertFalse(body.contains("must not be null"), "不得回显校验原文，实际: " + body);
        assertFalse(body.contains("'username'"), "不得回显字段名，实际: " + body);
    }

    @Test
    @DisplayName("1197-003 ClientFacingException：人工确认过的业务语仍可外显")
    void clientFacingExceptionStaysVisible() {
        final ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/models").build());
        final ClientFacingException exception = new ClientFacingException("用户名不能为空");

        StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        final String body = bodyOf(exchange);
        assertTrue(body.contains("用户名不能为空"), "业务校验语应到达调用方，实际: " + body);
        assertTrue(body.contains("参数验证失败"), "实际: " + body);
    }

    private static String bodyOf(final ServerWebExchange exchange) {
        return ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block();
    }
}

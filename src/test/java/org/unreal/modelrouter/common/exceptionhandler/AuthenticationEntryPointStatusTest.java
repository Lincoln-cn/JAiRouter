package org.unreal.modelrouter.common.exceptionhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * issue #420 ①：{@code authenticationEntryPoint} 这条路径上的状态语义 —— 钉住「硬编码 401 的边界」。
 *
 * <p><b>取证结论（本测试即其可执行形态）</b>：{@code SecurityConfiguration#securityWebFilterChain}
 * 的 {@code authenticationEntryPoint} 是 {@code ReactiveGlobalExceptionHandler#handle} 的<b>唯一</b>装配点，
 * 而 {@code handle} 的分派顺序把本仓 {@code AuthenticationException}（携带状态，认证链上唯一非 401 的是
 * {@code RATE_LIMITED_API_KEY} = 429）先交给 {@code ex instanceof SecurityException} 分支，
 * 最终由 {@code handleAuthenticationException} 用 {@code ex.getHttpStatus()} 渲染 ⇒</p>
 * <ul>
 *   <li>ENTRY-001：携带 429 的本仓认证异常**即使经 entryPoint** 也仍是 429（不会被硬编码的 401 压回）；</li>
 *   <li>ENTRY-002 / ENTRY-003：Spring Security 自身的认证异常（entryPoint 实际会收到的类型，无状态载体）
 *       仍是 401，本仓的默认 401 异常也仍是 401 —— 即 {@code handleSpringSecurityAuthenticationException}
 *       的硬编码 401 有依据且无遗漏分支；</li>
 *   <li>ENTRY-004：entryPoint 仍然把异常交给本处理器（源码级守卫）—— 上面三条断言的适用前提。</li>
 * </ul>
 *
 * <p>为什么不用集成测试：本仓没有任何生产路径会把本仓 {@code AuthenticationException} 交给 entryPoint
 * （API Key 校验链的异常经 {@code CustomReactiveAuthenticationManager} 回到
 * {@code SpringSecurityAuthenticationFilter} 并被它自己的 {@code onErrorResume} 吃掉），
 * 所以这里直接对准 entryPoint 的终点 {@code handle}，把「万一将来有路径交给 entryPoint」时的语义钉死。</p>
 *
 * @author JAiRouter Team
 * @since v3.3.5
 */
@DisplayName("issue #420: entryPoint 路径的认证异常状态（硬编码 401 的边界）")
class AuthenticationEntryPointStatusTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String OPENAI_PATH = "/v1/chat/completions";

    private static final String CONFIG_FILE =
            "src/main/java/org/unreal/modelrouter/auth/security/config/SecurityConfiguration.java";

    private final ReactiveGlobalExceptionHandler handler =
            new ReactiveGlobalExceptionHandler(new ErrorTracker(mock(StructuredLogger.class)));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("ENTRY-001: 本仓认证异常携带 429 ⇒ 经 entryPoint 的终点仍是 429 + code 原样（不被压回 401）")
    void repositoryAuthenticationExceptionWith429_shouldKeep429() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final AuthenticationException rateLimited = new AuthenticationException(
                "请求频率超过限制，请稍后再试",
                ErrorCode.RATE_LIMITED_API_KEY.code(),
                HttpStatus.TOO_MANY_REQUESTS);

        handler.handle(exchange, rateLimited).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode(),
                "本仓认证异常携带的状态必须先于「Spring Security 异常 ⇒ 401」分支生效");
        final JsonNode error = bodyJson(exchange).path("error");
        assertEquals("rate_limit_error", error.path("type").asText(),
                "状态 429 的 type 必须按状态映射，不能是 authentication_error：" + bodyText(exchange));
        assertEquals(ErrorCode.RATE_LIMITED_API_KEY.code(), error.path("code").asText(),
                "错误码不得被压成兜底的 \"401\"");
    }

    @Test
    @DisplayName("ENTRY-002: Spring Security 自身认证异常（无状态载体）⇒ 仍是 401")
    void springSecurityAuthenticationException_shouldStayUnauthorized() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);

        // entryPoint 在真实链路上收到的就是这一类（由 ExceptionTranslationWebFilter 产生的
        // InsufficientAuthenticationException / AuthorizationWebFilter 产生的
        // AuthenticationCredentialsNotFoundException）；它们不携带任何 HTTP 状态
        handler.handle(exchange, new InsufficientAuthenticationException("Full authentication is required"))
                .block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(),
                "Spring Security 认证异常没有可透传的状态，401 是唯一有依据的取值（issue #205 的既有行为）");
        assertEquals("authentication_error", bodyJson(exchange).path("error").path("type").asText(),
                "401 的协议面 type 仍是 authentication_error：" + bodyText(exchange));
    }

    @Test
    @DisplayName("ENTRY-003: 本仓认证异常的默认 401 ⇒ 401 且码原样透出")
    void repositoryAuthenticationExceptionWithDefaultStatus_shouldStayUnauthorized() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final AuthenticationException invalid = new AuthenticationException(
                "无效的API Key", ErrorCode.INVALID_API_KEY.code());

        handler.handle(exchange, invalid).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final JsonNode error = bodyJson(exchange).path("error");
        assertEquals("authentication_error", error.path("type").asText());
        assertEquals(ErrorCode.INVALID_API_KEY.code(), error.path("code").asText(),
                "默认 401 的异常同样要透出自己的码，不能落到兜底 \"401\"");
    }

    @Test
    @DisplayName("ENTRY-004: entryPoint 仍把认证异常交给本处理器（源码级守卫，ENTRY-001~003 的适用前提）")
    void entryPoint_shouldDelegateToThisHandler() throws IOException {
        final String source = Files.readString(Path.of(CONFIG_FILE), StandardCharsets.UTF_8);

        assertTrue(source.contains(".authenticationEntryPoint("),
                "认证入口点装配点必须仍在 " + CONFIG_FILE);
        assertTrue(Pattern.compile("ReactiveGlobalExceptionHandler\\s+\\w+\\s*=[^;]*"
                        + "ReactiveGlobalExceptionHandler\\.class", Pattern.DOTALL).matcher(source).find(),
                "entryPoint 必须从容器取 ReactiveGlobalExceptionHandler");
        assertTrue(Pattern.compile("\\.handle\\(\\s*exchange\\s*,\\s*ex\\s*\\)").matcher(source).find(),
                "entryPoint 必须把 (exchange, ex) 交给 handle() —— 上面的状态语义断言正建立在这一点上");
    }

    // ==================== 辅助方法 ====================

    private static MockServerWebExchange exchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private JsonNode bodyJson(final MockServerWebExchange exchange) throws Exception {
        return objectMapper.readTree(bodyText(exchange));
    }

    private String bodyText(final MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().defaultIfEmpty("").block(TIMEOUT);
    }
}

package org.unreal.modelrouter.common.exceptionhandler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.filter.SpringSecurityAuthenticationFilter;
import org.unreal.modelrouter.auth.security.config.properties.ApiKeyConfig;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * issue #686：认证失败响应体是 JSON，必须声明 {@code Content-Type: application/json}.
 *
 * <p>此前 {@link SpringSecurityAuthenticationFilter} 的认证失败渲染点只写状态码与响应体，
 * 三个分支（Anthropic 面 / 其余 {@code /v1} 面 / 非 {@code /v1} 路径的硬编码形状）都没有设置
 * {@code Content-Type}，客户端与中间件只能靠内容嗅探判定响应类型。本测试对三条分支与 429 限流
 * 逐一断言「状态码不变 + {@code Content-Type} 已声明 + 响应体仍是单个可解析的 JSON 对象」。</p>
 *
 * <p>断言的是**行为契约**：状态码与错误体形状由 {@code SpringSecurityAuthenticationFilterV1BodyTest}
 * 守护，这里只补 {@code Content-Type} 这一条此前无人守卫的事实。</p>
 *
 * @author JAiRouter Team
 * @since v3.4
 */
@DisplayName("issue #686: 认证失败响应声明 Content-Type")
class SpringSecurityAuthenticationFilterContentTypeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String ANTHROPIC_PATH = "/v1/messages";

    private static final String OPENAI_PATH = "/v1/chat/completions";

    private static final String LEGACY_PATH = "/api/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("/v1/messages 缺凭据 → 401 且 Content-Type 为 application/json")
    void anthropicFace_shouldDeclareJsonContentType() {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());

        filterWith(converter).filter(exchange, mockChain()).block(TIMEOUT);

        final JsonNode json = assertJsonErrorResponse(exchange, HttpStatus.UNAUTHORIZED);
        assertEquals("error", json.path("type").asText(),
                "Anthropic 面错误体外层 type 仍须是 error");
    }

    @Test
    @DisplayName("/v1/chat/completions 凭据无效 → 401 且 Content-Type 为 application/json")
    void openAiFace_shouldDeclareJsonContentType() {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        final ReactiveAuthenticationManager manager = mock(ReactiveAuthenticationManager.class);
        when(converter.convert(any()))
                .thenReturn(Mono.just(new UsernamePasswordAuthenticationToken("user", "creds")));
        when(manager.authenticate(any()))
                .thenReturn(Mono.error(AuthenticationException.invalidApiKey()));

        filterWith(converter, manager).filter(exchange, mockChain()).block(TIMEOUT);

        final JsonNode json = assertJsonErrorResponse(exchange, HttpStatus.UNAUTHORIZED);
        assertEquals("authentication_error", json.path("error").path("type").asText(),
                "OpenAI 面 error.type 仍须是 authentication_error");
    }

    @Test
    @DisplayName("/api/v1/... 缺凭据 → 401 且 Content-Type 为 application/json（硬编码形状分支）")
    void legacyFace_shouldDeclareJsonContentType() {
        final MockServerWebExchange exchange = exchangeOf(LEGACY_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());

        filterWith(converter).filter(exchange, mockChain()).block(TIMEOUT);

        final JsonNode json = assertJsonErrorResponse(exchange, HttpStatus.UNAUTHORIZED);
        assertNotNull(json.path("error").path("message").asText(),
                "非 /v1 面的硬编码形状仍须带 error.message");
    }

    @Test
    @DisplayName("限流 429 → 状态不变且 Content-Type 为 application/json")
    void rateLimited_shouldDeclareJsonContentType() {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        final ReactiveAuthenticationManager manager = mock(ReactiveAuthenticationManager.class);
        when(converter.convert(any()))
                .thenReturn(Mono.just(new UsernamePasswordAuthenticationToken("user", "creds")));
        when(manager.authenticate(any())).thenReturn(Mono.error(new AuthenticationException(
                "请求频率超过限制，请稍后再试",
                ErrorCode.RATE_LIMITED_API_KEY.code(),
                HttpStatus.TOO_MANY_REQUESTS)));

        filterWith(converter, manager).filter(exchange, mockChain()).block(TIMEOUT);

        final JsonNode json = assertJsonErrorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS);
        assertEquals("rate_limit_error", json.path("error").path("type").asText(),
                "429 的 error.type 仍须是 rate_limit_error");
    }

    // ==================== 辅助方法 ====================

    /**
     * 断言错误响应的状态码与 {@code Content-Type}，并返回解析后的响应体.
     *
     * <p>响应体只能读一次（{@code MockServerHttpResponse#getBody()} 是单订阅序列），
     * 故把解析结果返回给调用方复用。</p>
     *
     * @param exchange       已执行过过滤器的交换对象
     * @param expectedStatus 期望的状态码
     * @return 响应体解析出的单个 JSON 对象
     */
    private JsonNode assertJsonErrorResponse(
            final MockServerWebExchange exchange, final HttpStatus expectedStatus) {
        final MockServerHttpResponse response = exchange.getResponse();
        assertEquals(expectedStatus, response.getStatusCode(), "状态码不得因声明 Content-Type 而改变");
        assertEquals("application/json",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                "#686：错误响应必须声明 Content-Type: application/json");
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType(),
                "#686：错误响应的 Content-Type 应为 application/json（不带任何参数）");

        final String body = new String(bodyBytes(exchange), StandardCharsets.UTF_8);
        try {
            final JsonParser parser = objectMapper.getFactory().createParser(body);
            final JsonNode json = objectMapper.readTree(parser);
            assertNotNull(json, "响应体应可解析为 JSON: " + body);
            assertNull(parser.nextToken(), "响应体应只包含一个 JSON 对象: " + body);
            return json;
        } catch (final IOException ex) {
            throw new AssertionError("响应体应为合法 JSON: " + body, ex);
        }
    }

    private SpringSecurityAuthenticationFilter filterWith(
            final ServerAuthenticationConverter converter) {
        return filterWith(converter, mock(ReactiveAuthenticationManager.class));
    }

    private SpringSecurityAuthenticationFilter filterWith(
            final ServerAuthenticationConverter converter,
            final ReactiveAuthenticationManager manager) {
        final SecurityProperties props = mock(SecurityProperties.class);
        final ApiKeyConfig apiKeyConfig = mock(ApiKeyConfig.class);
        final JwtConfig jwtConfig = mock(JwtConfig.class);
        when(props.getApiKey()).thenReturn(apiKeyConfig);
        when(props.getJwt()).thenReturn(jwtConfig);
        when(apiKeyConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.isEnabled()).thenReturn(true);
        return new SpringSecurityAuthenticationFilter(props, converter, manager);
    }

    private WebFilterChain mockChain() {
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    private MockServerWebExchange exchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private byte[] bodyBytes(final MockServerWebExchange exchange) {
        final DataBuffer buffer = exchange.getResponse().getBody().blockLast(TIMEOUT);
        assertNotNull(buffer, "响应体不应为空");
        final byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }
}

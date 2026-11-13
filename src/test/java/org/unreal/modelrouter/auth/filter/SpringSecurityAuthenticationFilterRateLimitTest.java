package org.unreal.modelrouter.auth.filter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.security.config.properties.ApiKeyConfig;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.ApiKeyValidator;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * issue #404：认证过滤器把「认证异常携带的状态」透出，而不是硬编码 401.
 *
 * <p><b>为什么必须测这里</b>：{@code ApiKeyService.validateApiKey} 抛出的
 * {@link AuthenticationException} 经 {@code CustomReactiveAuthenticationManager} 回到
 * {@link SpringSecurityAuthenticationFilter}、被它自己的 {@code onErrorResume} 吃掉 ⇒
 * {@code /v1/**}、{@code /api/**} 与其它需认证路径的**客户端可见状态码全部由该过滤器渲染**
 * （两个 {@code @RestControllerAdvice} 在这条链上根本到不了）。因此「校验链携带 429」只有在
 * 过滤器把它写出去之后才算真的到达客户端。</p>
 *
 * <p>断言面：</p>
 * <ul>
 *   <li>携带 429 的异常 ⇒ ①② /v1 OpenAI 形状 与 ③ /v1/messages Anthropic 形状、
 *       ④ 非 /v1 旧形状 —— 状态码 429 且 {@code type} 由状态映射为 {@code rate_limit_error}
 *       （只改状态不改 type 会产出「429 + authentication_error」的自相矛盾）；</li>
 *   <li>401 的三条老路径 → 响应体**逐字节**等于改动前的输出（见 {@link #legacyPaths401_shouldBeByteIdentical()}）。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since v3.3.3
 */
@DisplayName("issue #404: SpringSecurityAuthenticationFilter 透出认证异常携带的 429")
class SpringSecurityAuthenticationFilterRateLimitTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String ANTHROPIC_PATH = "/v1/messages";

    private static final String OPENAI_PATH = "/v1/chat/completions";

    private static final String LEGACY_PATH = "/api/chat";

    private static final String RATE_LIMITED_MESSAGE = "请求频率超过限制，请稍后再试";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("RATE-001: /v1 OpenAI 面 → 429 + type=rate_limit_error + code=RATE_LIMITED_API_KEY")
    void openAiPath_rateLimitedException_shouldRender429RateLimitError() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);

        filterWithRateLimitError(rateLimitedException())
                .filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode json = bodyJson(exchange);
        assertFalse(json.has("type"), "OpenAI 面不应有外层 type: " + json);
        final JsonNode error = json.path("error");
        assertEquals("rate_limit_error", error.path("type").asText(),
                "429 的 type 必须是 rate_limit_error（不能沿用 authentication_error）: " + json);
        assertEquals(ErrorCode.RATE_LIMITED_API_KEY.code(), error.path("code").asText());
        assertEquals(RATE_LIMITED_MESSAGE, error.path("message").asText());
    }

    @Test
    @DisplayName("RATE-002: /v1/messages Anthropic 面 → 429 + error.type=rate_limit_error + 不带 code")
    void anthropicPath_rateLimitedException_shouldRender429RateLimitError() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);

        filterWithRateLimitError(rateLimitedException())
                .filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode json = bodyJson(exchange);
        assertEquals("error", json.path("type").asText(), "Anthropic 错误体外层 type 应为 error: " + json);
        assertEquals("rate_limit_error", json.path("error").path("type").asText(),
                "429 的 type 必须是 rate_limit_error: " + json);
        assertFalse(json.path("error").has("code"), "Anthropic 面不带 code 字段: " + json);
    }

    @Test
    @DisplayName("RATE-003: 非 /v1 路径 → 429 + 旧形状里的 type=rate_limit_error")
    void legacyPath_rateLimitedException_shouldRender429() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(LEGACY_PATH);

        filterWithRateLimitError(rateLimitedException())
                .filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode json = bodyJson(exchange);
        assertTrue(json.has("error"), "应有 error 字段: " + json);
        assertFalse(json.has("type"), "非 /v1 路径不应有 Anthropic 外层 type: " + json);
        assertEquals("rate_limit_error", json.path("error").path("type").asText(),
                "429 的 type 必须是 rate_limit_error: " + json);
        assertEquals(ErrorCode.RATE_LIMITED_API_KEY.code(), json.path("error").path("code").asText());
    }

    @Test
    @DisplayName("RATE-004: 异常由校验链真实产出（FullValidationResult.toException()）时同样是 429")
    void exceptionProducedByTheValidationChain_shouldAlsoRender429() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final AuthenticationException fromChain = ApiKeyValidator.FullValidationResult
                .validationError("key-001", RATE_LIMITED_MESSAGE, ErrorCode.RATE_LIMITED_API_KEY.code())
                .toException();

        filterWithRateLimitError(fromChain)
                .filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode error = bodyJson(exchange).path("error");
        assertEquals("rate_limit_error", error.path("type").asText());
        assertEquals(ErrorCode.RATE_LIMITED_API_KEY.code(), error.path("code").asText());
    }

    @Test
    @DisplayName("RATE-005: 三条 401 老路径的响应体形状与状态码不变（文案 v3.8.6 起点名可用头）")
    void legacyPaths401_shouldBeByteIdentical() {
        // 期望值 = 当前实现的原始字节（硬编码 "authentication_error" + 硬编码 401）。
        // v3.8.6：文案不再写「请提供API Key或JWT Token」（它没说头名，客户端按 OpenAI 习惯发
        // Authorization: Bearer 只会看到 401），改为点名 X-API-Key / X-Jairouter-Token；
        // 形状、type、code 与状态码均未变，本用例守住这三点。
        final String legacyMissing = "{\"error\": {\"message\": \"请求缺少认证信息，请通过 X-API-Key 或 X-Jairouter-Token "
                + "请求头提供凭据（Authorization 头不参与网关认证，原样透传给下游服务）\", "
                + "\"type\": \"authentication_error\", \"code\": \"AUTH_MISSING\"}}";
        final String openAiMissing = "{\"error\":{\"message\":\"请求缺少认证信息，请通过 X-API-Key 或 X-Jairouter-Token "
                + "请求头提供凭据（Authorization 头不参与网关认证，原样透传给下游服务）\","
                + "\"type\":\"authentication_error\",\"code\":\"AUTH_MISSING\"}}";
        final String anthropicMissing = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
                + "\"message\":\"请求缺少认证信息，请通过 X-API-Key 或 X-Jairouter-Token "
                + "请求头提供凭据（Authorization 头不参与网关认证，原样透传给下游服务）\"}}";

        final MockServerWebExchange legacy = exchangeOf(LEGACY_PATH);
        filterWithoutCredentials().filter(legacy, mockChain()).block(TIMEOUT);
        assertEquals(HttpStatus.UNAUTHORIZED, legacy.getResponse().getStatusCode());
        assertEquals(legacyMissing, bodyText(legacy));

        final MockServerWebExchange openAi = exchangeOf(OPENAI_PATH);
        filterWithoutCredentials().filter(openAi, mockChain()).block(TIMEOUT);
        assertEquals(HttpStatus.UNAUTHORIZED, openAi.getResponse().getStatusCode());
        assertEquals(openAiMissing, bodyText(openAi));

        final MockServerWebExchange anthropic = exchangeOf(ANTHROPIC_PATH);
        filterWithoutCredentials().filter(anthropic, mockChain()).block(TIMEOUT);
        assertEquals(HttpStatus.UNAUTHORIZED, anthropic.getResponse().getStatusCode());
        assertEquals(anthropicMissing, bodyText(anthropic));
    }

    @Test
    @DisplayName("RATE-006: 凭据缺失提示点名声明的头名（自定义头名跟着变，不再只写「API Key 或 JWT」）")
    void missingCredentialsMessage_shouldNameConfiguredHeaders() {
        // v3.8.6 起文案取自配置：JWT 走 JwtConfig.jwtHeader、API Key 走 ApiKeyConfig.headerName，
        // 因为 Authorization 头刻意留给下游透传（客户端按 OpenAI 习惯发 Bearer 只会得到 401）。
        final SecurityProperties props = mock(SecurityProperties.class);
        final ApiKeyConfig apiKeyConfig = mock(ApiKeyConfig.class);
        final JwtConfig jwtConfig = mock(JwtConfig.class);
        when(props.getApiKey()).thenReturn(apiKeyConfig);
        when(props.getJwt()).thenReturn(jwtConfig);
        when(apiKeyConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(apiKeyConfig.getHeaderName()).thenReturn("X-Tenant-Key");
        when(jwtConfig.getJwtHeader()).thenReturn("X-Tenant-Token");

        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());
        final SpringSecurityAuthenticationFilter filter = new SpringSecurityAuthenticationFilter(
                props, converter, mock(ReactiveAuthenticationManager.class));

        final MockServerWebExchange exchange = exchangeOf(LEGACY_PATH);
        filter.filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final String body = bodyText(exchange);
        assertTrue(body.contains("X-Tenant-Key"), "文案应点名 API Key 头名: " + body);
        assertTrue(body.contains("X-Tenant-Token"), "文案应点名 JWT 头名: " + body);
        assertFalse(body.contains("请提供API Key或JWT Token"), "旧文案没有头名，不该再出现: " + body);
    }

    @Test
    @DisplayName("RATE-007: 头名空白时提示回落到 X-API-Key / X-Jairouter-Token，不出现 null")
    void missingCredentialsMessage_shouldFallBackWhenHeaderNameBlank() {
        final SecurityProperties props = mock(SecurityProperties.class);
        final ApiKeyConfig apiKeyConfig = mock(ApiKeyConfig.class);
        final JwtConfig jwtConfig = mock(JwtConfig.class);
        when(props.getApiKey()).thenReturn(apiKeyConfig);
        when(props.getJwt()).thenReturn(jwtConfig);
        when(apiKeyConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(apiKeyConfig.getHeaderName()).thenReturn(null);
        when(jwtConfig.getJwtHeader()).thenReturn("  ");

        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());
        final SpringSecurityAuthenticationFilter filter = new SpringSecurityAuthenticationFilter(
                props, converter, mock(ReactiveAuthenticationManager.class));

        final MockServerWebExchange exchange = exchangeOf(LEGACY_PATH);
        filter.filter(exchange, mockChain()).block(TIMEOUT);

        final String body = bodyText(exchange);
        assertTrue(body.contains("X-API-Key"), body);
        assertTrue(body.contains("X-Jairouter-Token"), body);
        assertFalse(body.contains("null"), "提示里不得出现 null: " + body);
    }

    // ==================== 辅助方法 ====================

    private AuthenticationException rateLimitedException() {
        return new AuthenticationException(RATE_LIMITED_MESSAGE,
                ErrorCode.RATE_LIMITED_API_KEY.code(), HttpStatus.TOO_MANY_REQUESTS);
    }

    private SpringSecurityAuthenticationFilter filterWithRateLimitError(final AuthenticationException error) {
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        final ReactiveAuthenticationManager manager = mock(ReactiveAuthenticationManager.class);
        when(converter.convert(any()))
                .thenReturn(Mono.just(new UsernamePasswordAuthenticationToken("user", "creds")));
        when(manager.authenticate(any())).thenReturn(Mono.error(error));
        return filterWith(converter, manager);
    }

    private SpringSecurityAuthenticationFilter filterWithoutCredentials() {
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());
        return filterWith(converter, mock(ReactiveAuthenticationManager.class));
    }

    private SpringSecurityAuthenticationFilter filterWith(final ServerAuthenticationConverter converter,
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

    private JsonNode bodyJson(final MockServerWebExchange exchange) throws Exception {
        final String body = bodyText(exchange);
        final JsonNode json = objectMapper.readTree(body);
        assertNotNull(json, "应解析为 JSON: " + body);
        return json;
    }

    private String bodyText(final MockServerWebExchange exchange) {
        return new String(bodyBytes(exchange), StandardCharsets.UTF_8);
    }

    private byte[] bodyBytes(final MockServerWebExchange exchange) {
        final DataBuffer buffer = exchange.getResponse().getBody().blockLast(TIMEOUT);
        assertNotNull(buffer, "响应体不应为空");
        final byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }
}

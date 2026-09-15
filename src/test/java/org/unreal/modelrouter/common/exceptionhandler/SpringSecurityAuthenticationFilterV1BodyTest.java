package org.unreal.modelrouter.common.exceptionhandler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
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
import org.unreal.modelrouter.common.exception.AuthenticationException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * v3.1 修复：{@link SpringSecurityAuthenticationFilter} 401 错误体按路径分形状输出.
 *
 * <p>此前该 filter 的 {@code createAuthenticationErrorResponse} 硬编码 OpenAI 形状且无路径判定，
 * 导致 Anthropic-only 客户端（如 Claude Code）拿到的 401 体解析不了。现在：
 * <ul>
 *   <li>{@code /v1/messages}（含子路径）→ Anthropic 形状
 *       {@code {"type":"error","error":{"type":"authentication_error","message":"..."}}}；</li>
 *   <li>其余 {@code /v1/**} → OpenAI 形状
 *       {@code {"error":{"message":"...","type":"authentication_error","code":"..."}}}；</li>
 *   <li>非 {@code /v1} 路径沿用既有硬编码形状（逐字节不变）。</li>
 * </ul>
 * 路径判定复用 {@link V1ErrorBodyMapper}（与 {@code SecurityExceptionHandler} 同源）。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@DisplayName("v3.1: SpringSecurityAuthenticationFilter /v1 401 错误体形状")
class SpringSecurityAuthenticationFilterV1BodyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String ANTHROPIC_PATH = "/v1/messages";

    private static final String OPENAI_PATH = "/v1/chat/completions";

    private static final String API_PATH = "/api/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== /v1/messages（Anthropic 面） ====================

    @Test
    @DisplayName("/v1/messages 缺凭据 → 401 + Anthropic 形状 + authentication_error")
    void anthropicMessagesMissingAuth_shouldRenderAnthropicShape() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());

        filterWith(converter).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final JsonNode json = bodyJson(exchange);
        assertEquals("error", json.path("type").asText(),
                "Anthropic 错误体外层 type 应为 error: " + json);
        assertEquals("authentication_error", json.path("error").path("type").asText());
        assertTrue(json.path("error").path("message").asText().contains("请求缺少认证信息"),
                "消息应包含认证缺失说明: " + json);
        assertFalse(json.has("success"), "不得输出 RouterResponse 字段: " + json);
        assertFalse(json.path("error").has("code"),
                "Anthropic 面不带 code 字段: " + json);
    }

    @Test
    @DisplayName("/v1/messages 凭据无效 → 401 + Anthropic 形状 + authentication_error")
    void anthropicMessagesInvalidAuth_shouldRenderAnthropicShape() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        final ReactiveAuthenticationManager manager = mock(ReactiveAuthenticationManager.class);
        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user", "creds");
        when(converter.convert(any())).thenReturn(Mono.just(auth));
        when(manager.authenticate(any()))
                .thenReturn(Mono.error(AuthenticationException.invalidApiKey()));

        filterWith(converter, manager).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final JsonNode json = bodyJson(exchange);
        assertEquals("error", json.path("type").asText());
        assertEquals("authentication_error", json.path("error").path("type").asText());
        assertTrue(json.path("error").path("message").asText().contains("无效的API Key"),
                "消息应包含具体认证错误: " + json);
        assertFalse(json.path("error").has("code"), "Anthropic 面不带 code 字段: " + json);
    }

    // ==================== 其余 /v1/**（OpenAI 面） ====================

    @Test
    @DisplayName("/v1/chat/completions 缺凭据 → 401 + OpenAI 形状 + authentication_error")
    void openAiChatCompletionsMissingAuth_shouldRenderOpenAiShape() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());

        filterWith(converter).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final JsonNode json = bodyJson(exchange);
        assertFalse(json.has("type"), "OpenAI 面不应有外层 type: " + json);
        final JsonNode error = json.path("error");
        assertTrue(error.isObject(), "应有 error 对象: " + json);
        assertEquals("authentication_error", error.path("type").asText());
        assertEquals("AUTH_MISSING", error.path("code").asText());
        assertTrue(error.path("message").asText().contains("请求缺少认证信息"),
                "消息应包含认证缺失说明: " + json);
        assertFalse(json.has("success"), "不得输出 RouterResponse 字段: " + json);
    }

    // ==================== 非 /v1 路径（既有形状逐字节不变） ====================

    @Test
    @DisplayName("/api/v1/... 缺凭据 → 401 + 既有硬编码形状（逐字节回归）")
    void apiPathMissingAuth_shouldKeepLegacyShape() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(API_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());

        filterWith(converter).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final JsonNode json = bodyJson(exchange);
        assertTrue(json.has("error"), "应有 error 字段: " + json);
        assertFalse(json.has("type"), "非 /v1 路径不应有 Anthropic 外层 type: " + json);
        assertTrue(json.path("error").path("message").asText().contains("请求缺少认证信息"),
                "消息应包含认证缺失说明: " + json);
    }

    // ==================== UTF-8 字符集 ====================

    @Test
    @DisplayName("401 中文消息以 UTF-8 字节写出（/v1 形状下同样保证）")
    void v1AuthErrorMessage_shouldBeUtf8Encoded() {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());

        filterWith(converter).filter(exchange, mockChain()).block(TIMEOUT);

        final byte[] bytes = bodyBytes(exchange);
        final String body = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(body.contains("请求缺少认证信息"),
                "按 UTF-8 解码后应为可读中文: " + body);
        assertTrue(body.indexOf('\uFFFD') < 0,
                "按 UTF-8 解码不应出现乱码替换字符: " + body);
    }

    // ==================== 辅助方法 ====================

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

    private JsonNode bodyJson(final MockServerWebExchange exchange) throws Exception {
        final byte[] bytes = bodyBytes(exchange);
        assertNotNull(bytes, "响应体字节不应为空");
        final String body = new String(bytes, StandardCharsets.UTF_8);
        final JsonParser parser = objectMapper.getFactory().createParser(body);
        final JsonNode json = objectMapper.readTree(parser);
        assertNotNull(json, "应解析为 JSON: " + body);
        assertNull(parser.nextToken(),
                "响应体应只包含一个 JSON 对象: " + body);
        return json;
    }

    private byte[] bodyBytes(final MockServerWebExchange exchange) {
        final DataBuffer buffer = exchange.getResponse().getBody().blockLast(TIMEOUT);
        assertNotNull(buffer, "响应体不应为空");
        final byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }
}

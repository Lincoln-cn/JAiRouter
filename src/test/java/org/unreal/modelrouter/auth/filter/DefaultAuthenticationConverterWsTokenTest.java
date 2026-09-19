package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket 握手鉴权：浏览器 {@code new WebSocket} 无法携带自定义头，
 * 必须从 query 提取 token（P1 审计：/ws/** 原先匿名）。
 */
@DisplayName("DefaultAuthenticationConverter WebSocket query token")
class DefaultAuthenticationConverterWsTokenTest {

    private DefaultAuthenticationConverter converter;
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getJwt().setEnabled(true);
        securityProperties.getJwt().setJwtHeader("Jairouter_Token");
        securityProperties.getApiKey().setEnabled(true);
        securityProperties.getApiKey().setHeaderName("X-API-Key");
        converter = new DefaultAuthenticationConverter(securityProperties);
    }

    @Test
    @DisplayName("JWT：从 ?token= 提取（WebSocket 握手）")
    void jwt_fromQueryToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/routing-monitor?token=jwt-query-1").build());

        Authentication auth = converter.convert(exchange).block();

        assertInstanceOf(JwtAuthentication.class, auth);
        assertEquals("jwt-query-1", auth.getCredentials());
    }

    @Test
    @DisplayName("JWT：从 ?access_token= 提取（兼容常见 OAuth 查询参数）")
    void jwt_fromAccessTokenQuery() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/circuit-breaker-monitor?access_token=jwt-qs-2").build());

        Authentication auth = converter.convert(exchange).block();

        assertInstanceOf(JwtAuthentication.class, auth);
        assertEquals("jwt-qs-2", auth.getCredentials());
    }

    @Test
    @DisplayName("JWT：请求头优先于 query（header 与 query 同时存在）")
    void jwt_headerWinsOverQuery() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/ws/routing-monitor?token=query-tok")
                .header("Jairouter_Token", "header-tok")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Authentication auth = converter.convert(exchange).block();

        assertInstanceOf(JwtAuthentication.class, auth);
        assertEquals("header-tok", auth.getCredentials());
    }

    @Test
    @DisplayName("API Key：无头时从 ?api_key= 提取")
    void apiKey_fromQueryWhenHeaderMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/routing-monitor?api_key=sk-query-key").build());

        Authentication auth = converter.convert(exchange).block();

        assertInstanceOf(ApiKeyAuthentication.class, auth);
        assertEquals("sk-query-key", auth.getCredentials());
    }

    @Test
    @DisplayName("无任何凭证时返回 empty")
    void empty_whenNoCredentials() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/routing-monitor").build());

        assertTrue(converter.convert(exchange).blockOptional().isEmpty());
    }

    @Test
    @DisplayName("API Key 头仍然优先")
    void apiKey_headerStillWins() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/ws/routing-monitor?api_key=sk-query")
                .header("X-API-Key", "sk-header")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Authentication auth = converter.convert(exchange).block();

        assertInstanceOf(ApiKeyAuthentication.class, auth);
        assertEquals("sk-header", auth.getCredentials());
    }
}

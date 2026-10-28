package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #695：query 取凭据只允许在 WebSocket 握手上。
 *
 * <p>背景：{@code /ws/**} 之外路径的完整 URL（含 query）会进结构化日志与 tracing span，
 * 从 query 取凭据等于把令牌明文写进日志 / APM。这里锁住「非 /ws/** 不再回退 query」。</p>
 */
@DisplayName("DefaultAuthenticationConverter query 凭据作用域（#695）")
class DefaultAuthenticationConverterQueryScopeTest {

    private DefaultAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setEnabled(true);
        securityProperties.getJwt().setJwtHeader("Jairouter_Token");
        securityProperties.getApiKey().setEnabled(true);
        securityProperties.getApiKey().setHeaderName("X-API-Key");
        converter = new DefaultAuthenticationConverter(securityProperties);
    }

    private ServerWebExchange get(String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uri).build());
    }

    @Test
    @DisplayName("普通路径：?token= 不再被当作凭据（否则凭据会随 URL 进日志）")
    void apiPath_queryTokenIsIgnored() {
        assertTrue(converter.convert(get("/api/models?token=jwt-leaked-in-log")).blockOptional().isEmpty(),
                "非 /ws/** 路径不得从 query 取 JWT");
    }

    @Test
    @DisplayName("普通路径：?access_token= 同样被忽略")
    void apiPath_queryAccessTokenIsIgnored() {
        assertTrue(converter.convert(get("/v1/chat/completions?access_token=oauth-leaked")).blockOptional().isEmpty(),
                "非 /ws/** 路径不得从 query 取 JWT");
    }

    @Test
    @DisplayName("普通路径：?api_key= 不再被当作凭据")
    void apiPath_queryApiKeyIsIgnored() {
        assertTrue(converter.convert(get("/v1/models?api_key=sk-leaked-in-log")).blockOptional().isEmpty(),
                "非 /ws/** 路径不得从 query 取 API Key");
    }

    @Test
    @DisplayName("普通路径：请求头仍然是有效凭据（修复没有把整条头通道一起关掉）")
    void apiPath_headerStillAuthenticates() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/models").header("Jairouter_Token", "header-token").build());

        Authentication auth = converter.convert(exchange).block();

        assertInstanceOf(JwtAuthentication.class, auth);
        assertEquals("header-token", auth.getCredentials());
    }

    @Test
    @DisplayName("WebSocket 握手：/ws/** 仍然可以从 query 取凭据（回归）")
    void websocketPath_queryTokenStillWorks() {
        Authentication auth = converter.convert(get("/ws/routing-monitor?token=jwt-ws")).block();

        assertInstanceOf(JwtAuthentication.class, auth);
        assertEquals("jwt-ws", auth.getCredentials());
    }
}

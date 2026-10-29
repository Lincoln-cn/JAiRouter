package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JWT 头名解析（issue #805）。
 *
 * <p>缺陷背景：默认头名曾叫 {@code Jairouter_Token}，含下划线。nginx 默认
 * {@code underscores_in_headers off} 会直接丢弃带下划线的请求头 —— 客户端看到的是
 * 「登录成功，之后每个管理接口都 401」，且没有任何服务端错误可查。</p>
 *
 * <p>修法 = 默认头名改成连字符形式（{@code X-Jairouter-Token}），同时继续接受旧的下划线头名，
 * 避免老客户端升级后突然全部 401。这组用例把两件事都钉住。</p>
 */
@DisplayName("DefaultAuthenticationConverter JWT 头名（#805）")
class DefaultAuthenticationConverterJwtHeaderTest {

    private static DefaultAuthenticationConverter converterWithJwtHeader(final String headerName) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setEnabled(true);
        properties.getJwt().setJwtHeader(headerName);
        properties.getApiKey().setEnabled(false);
        return new DefaultAuthenticationConverter(properties);
    }

    private static MockServerWebExchange requestWithHeader(final String name, final String value) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/models").header(name, value).build());
    }

    @Test
    @DisplayName("默认头名 X-Jairouter-Token 能取到令牌")
    void extractsFromDefaultHyphenatedHeader() {
        DefaultAuthenticationConverter converter = converterWithJwtHeader("X-Jairouter-Token");

        Authentication auth = converter.convert(requestWithHeader("X-Jairouter-Token", "jwt-1")).block();

        JwtAuthentication jwtAuthentication = assertInstanceOf(JwtAuthentication.class, auth);
        assertEquals("jwt-1", jwtAuthentication.getCredentials());
    }

    @Test
    @DisplayName("兼容：旧的下划线头名 Jairouter_Token 仍然可用")
    void stillAcceptsLegacyUnderscoreHeader() {
        DefaultAuthenticationConverter converter = converterWithJwtHeader("X-Jairouter-Token");

        Authentication auth = converter.convert(requestWithHeader("Jairouter_Token", "legacy-jwt")).block();

        JwtAuthentication jwtAuthentication = assertInstanceOf(JwtAuthentication.class, auth);
        assertEquals("legacy-jwt", jwtAuthentication.getCredentials());
    }

    @Test
    @DisplayName("两个头同时出现时以配置的头名为准（不被旧头名劫持）")
    void configuredHeaderWinsOverLegacyAlias() {
        DefaultAuthenticationConverter converter = converterWithJwtHeader("X-Jairouter-Token");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/models")
                        .header("X-Jairouter-Token", "configured")
                        .header("Jairouter_Token", "legacy")
                        .build());

        JwtAuthentication auth = assertInstanceOf(JwtAuthentication.class, converter.convert(exchange).block());
        assertEquals("configured", auth.getCredentials());
    }

    @Test
    @DisplayName("没带任何 JWT 头时返回空 Mono（由过滤器转 401）")
    void returnsEmptyWhenNoTokenHeader() {
        DefaultAuthenticationConverter converter = converterWithJwtHeader("X-Jairouter-Token");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/models").build());

        assertNull(converter.convert(exchange).block());
    }
}

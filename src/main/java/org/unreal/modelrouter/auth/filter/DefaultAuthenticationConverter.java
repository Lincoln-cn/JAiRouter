package org.unreal.modelrouter.auth.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;

import java.util.List;
import org.unreal.modelrouter.common.util.IpUtils;

/**
 * 默认的认证转换器
 */
public final class DefaultAuthenticationConverter implements ServerAuthenticationConverter {

    /** 允许从 query 取凭据的路径前缀（WebSocket 握手，见 {@link #isWebSocketHandshake}）。 */
    private static final String WEBSOCKET_PATH_PREFIX = "/ws/";

    /**
     * 历史 JWT 头名（issue #805）：下划线形式会被 nginx 默认配置丢弃，已不是默认值，
     * 但仍接受，避免老客户端升级后突然全部 401。
     */
    private static final List<String> LEGACY_JWT_HEADERS = List.of("Jairouter_Token");

    private final SecurityProperties securityProperties;

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthenticationConverter.class);

    public DefaultAuthenticationConverter(final SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Mono<Authentication> convert(final ServerWebExchange exchange) {
        String apiKey = null;
        String jwtToken = null;

        // 首先尝试提取API Key（如果启用）
        if (securityProperties.getApiKey().isEnabled()) {
            apiKey = extractApiKey(exchange);
        }

        // 然后尝试提取JWT令牌（如果启用）
        if (securityProperties.getJwt().isEnabled()) {
            jwtToken = extractJwtToken(exchange);
        }

        // 头缺失时回落 query —— 仅限 WebSocket 握手（#695）
        //
        // 浏览器 new WebSocket 无法携带自定义头，/ws/** 只能从 query 取凭据；其它路径的完整
        // URL 会被结构化日志与 tracing span 原样记录，凭据放在 query 里等于明文落盘，故不再回退。
        if (isWebSocketHandshake(exchange)) {
            if (jwtToken == null && securityProperties.getJwt().isEnabled()) {
                jwtToken = firstQueryParam(exchange, "token", "access_token");
            }
            if (apiKey == null && securityProperties.getApiKey().isEnabled()) {
                apiKey = firstQueryParam(exchange, "api_key", "apiKey");
            }
        }

        // 如果同时提供了API Key和JWT令牌，则优先使用JWT
        if (jwtToken != null) {
            log.debug("提取到JWT令牌，创建JWT认证对象");
            return Mono.just(new JwtAuthentication(jwtToken));
        } else if (apiKey != null) {
            log.debug("提取到API Key，创建API Key认证对象");
            return Mono.just(new ApiKeyAuthentication(apiKey,
                    IpUtils.getClientIp(exchange.getRequest()),
                    exchange.getRequest().getPath().value()));
        }

        // 没有找到认证信息
        return Mono.empty();
    }

    /**
     * 提取API Key
     */
    private String extractApiKey(final ServerWebExchange exchange) {
        String headerName = securityProperties.getApiKey().getHeaderName();
        List<String> headerValues = exchange.getRequest().getHeaders().get(headerName);

        if (headerValues != null && !headerValues.isEmpty()) {
            return headerValues.get(0);
        }

        return null;
    }

    /**
     * 提取JWT令牌
     * 注意：Authorization 头保留给下游 AI 服务使用，JAiRouter 认证使用配置的 JWT 头
     * （默认 {@code X-Jairouter-Token}，见 issue #805）。
     */
    private String extractJwtToken(final ServerWebExchange exchange) {
        String jwtHeader = securityProperties.getJwt().getJwtHeader();
        String token = getHeaderIgnoreCase(exchange, jwtHeader);
        if (token != null) {
            return token;
        }
        // 历史头名兜底（issue #805）：默认头名曾叫 Jairouter_Token，含下划线；nginx 默认
        // underscores_in_headers off 会把这个头直接丢掉（客户端看到「登录成功但后续 401」）。
        // 头名已改为连字符形式，但老客户端/旧脚本仍可能只发送下划线头，这里继续接受。
        for (String legacyHeader : LEGACY_JWT_HEADERS) {
            if (legacyHeader.equalsIgnoreCase(jwtHeader)) {
                continue;
            }
            token = getHeaderIgnoreCase(exchange, legacyHeader);
            if (token != null) {
                log.debug("使用历史 JWT 头名 {}（建议迁移到 {}）", legacyHeader, jwtHeader);
                return token;
            }
        }
        return null;
    }

    /**
     * 大小写不敏感地获取请求头
     */
    private String getHeaderIgnoreCase(final ServerWebExchange exchange, final String headerName) {
        // 首先尝试直接获取
        List<String> values = exchange.getRequest().getHeaders().get(headerName);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }

        // 如果直接获取失败，进行大小写不敏感的搜索
        String lowerHeaderName = headerName.toLowerCase();
        for (String key : exchange.getRequest().getHeaders().keySet()) {
            if (key.toLowerCase().equals(lowerHeaderName)) {
                List<String> headerValues = exchange.getRequest().getHeaders().get(key);
                if (headerValues != null && !headerValues.isEmpty()) {
                    return headerValues.get(0);
                }
            }
        }
        return null;
    }

    /**
     * 是否 WebSocket 握手请求。
     *
     * <p>只有 {@code /ws/**} 允许从 query 取凭据：这是浏览器 {@code new WebSocket} 无法设置
     * 自定义头时唯一的传参通道；其余路径的 query 会随完整 URL 进入日志 / span（#695）。</p>
     */
    private static boolean isWebSocketHandshake(final ServerWebExchange exchange) {
        final String path = exchange.getRequest().getPath().value();
        return path != null && (path.equals("/ws") || path.startsWith(WEBSOCKET_PATH_PREFIX));
    }

    /**
     * 按参数名顺序返回第一个非空 query 参数值（用于 WebSocket 握手 token）。
     */
    private static String firstQueryParam(final ServerWebExchange exchange, final String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            String value = exchange.getRequest().getQueryParams().getFirst(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

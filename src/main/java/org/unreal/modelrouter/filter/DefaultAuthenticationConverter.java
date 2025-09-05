package org.unreal.modelrouter.filter;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 默认的认证转换器
 */
public class DefaultAuthenticationConverter implements ServerAuthenticationConverter {

    private final SecurityProperties securityProperties;

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthenticationConverter.class);

    public DefaultAuthenticationConverter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String apiKey = null;
        String jwtToken = null;

        // 首先尝试提取API Key（如果启用）
        if (Boolean.TRUE.equals(securityProperties.getApiKey().isEnabled())) {
            apiKey = extractApiKey(exchange);
        }

        // 然后尝试提取JWT令牌（如果启用）
        if (Boolean.TRUE.equals(securityProperties.getJwt().isEnabled())) {
            jwtToken = extractJwtToken(exchange);
        }

        // 如果同时提供了API Key和JWT令牌，则优先使用JWT
        if (jwtToken != null) {
            log.debug("提取到JWT令牌，创建JWT认证对象");
            return Mono.just(new JwtAuthentication(jwtToken));
        } else if (apiKey != null) {
            log.debug("提取到API Key，创建API Key认证对象");
            return Mono.just(new ApiKeyAuthentication(apiKey));
        }

        // 没有找到认证信息
        return Mono.empty();
    }

    /**
     * 提取API Key
     */
    private String extractApiKey(ServerWebExchange exchange) {
        String headerName = securityProperties.getApiKey().getHeaderName();
        List<String> headerValues = exchange.getRequest().getHeaders().get(headerName);

        if (headerValues != null && !headerValues.isEmpty()) {
            return headerValues.get(0);
        }

        return null;
    }

    /**
     * 提取JWT令牌
     */
    private String extractJwtToken(ServerWebExchange exchange) {
        String jwtHeader = securityProperties.getJwt().getJwtHeader();
        List<String> jwtHeaders = exchange.getRequest().getHeaders().get(jwtHeader);
        if (jwtHeaders != null && !jwtHeaders.isEmpty()) {
            String authHeader = jwtHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            return authHeader;
        }

        return null;
    }
}

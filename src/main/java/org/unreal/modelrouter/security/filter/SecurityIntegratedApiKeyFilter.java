package org.unreal.modelrouter.security.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 与Spring Security集成的API Key认证过滤器
 * 简化版本，专注于认证逻辑
 */
@Slf4j
@RequiredArgsConstructor
public class SecurityIntegratedApiKeyFilter implements WebFilter {
    
    private final ApiKeyService apiKeyService;
    private final JwtTokenValidator jwtTokenValidator;
    private final SecurityProperties securityProperties;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 检查是否需要认证
        if (isExcludedPath(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        
        // 尝试提取认证信息
        return extractAuthentication(exchange)
                .flatMap(this::authenticateRequest)
                .flatMap(authentication -> {
                    // 设置安全上下文
                    SecurityContext securityContext = new SecurityContextImpl(authentication);
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                })
                .switchIfEmpty(chain.filter(exchange));
    }
    
    /**
     * 提取认证信息
     */
    private Mono<Authentication> extractAuthentication(ServerWebExchange exchange) {
        // 首先尝试API Key
        String apiKey = extractApiKey(exchange);
        if (apiKey != null) {
            return Mono.just(new ApiKeyAuthentication(apiKey));
        }
        
        // 然后尝试JWT
        if (securityProperties.getJwt().isEnabled()) {
            String jwtToken = extractJwtToken(exchange);
            if (jwtToken != null) {
                return Mono.just(new JwtAuthentication(jwtToken));
            }
        }
        
        return Mono.empty();
    }
    
    /**
     * 认证请求
     */
    private Mono<Authentication> authenticateRequest(Authentication authentication) {
        if (authentication instanceof ApiKeyAuthentication) {
            return authenticateApiKey((ApiKeyAuthentication) authentication);
        } else if (authentication instanceof JwtAuthentication) {
            return authenticateJwt((JwtAuthentication) authentication);
        }
        
        return Mono.empty();
    }
    
    /**
     * 认证API Key
     */
    private Mono<Authentication> authenticateApiKey(ApiKeyAuthentication authentication) {
        String apiKey = (String) authentication.getCredentials();
        
        return apiKeyService.validateApiKey(apiKey)
                .map(apiKeyInfo -> {
                    ApiKeyAuthentication authenticated = new ApiKeyAuthentication(
                            apiKeyInfo.getKeyId(),
                            apiKey,
                            apiKeyInfo.getPermissions()
                    );
                    authenticated.setAuthenticated(true);
                    authenticated.setDetails(apiKeyInfo);
                    return (Authentication) authenticated;
                })
                .doOnNext(auth -> log.debug("API Key认证成功: {}", auth.getName()))
                .doOnError(error -> log.debug("API Key认证失败: {}", error.getMessage()));
    }
    
    /**
     * 认证JWT
     */
    private Mono<Authentication> authenticateJwt(JwtAuthentication authentication) {
        String token = (String) authentication.getCredentials();
        
        return jwtTokenValidator.validateToken(token)
                .doOnNext(auth -> log.debug("JWT认证成功: {}", auth.getName()))
                .doOnError(error -> log.debug("JWT认证失败: {}", error.getMessage()));
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
        List<String> authHeaders = exchange.getRequest().getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        
        return null;
    }
    
    /**
     * 检查是否为排除的路径
     */
    private boolean isExcludedPath(String path) {
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/webjars/") ||
               path.equals("/favicon.ico");
    }
}
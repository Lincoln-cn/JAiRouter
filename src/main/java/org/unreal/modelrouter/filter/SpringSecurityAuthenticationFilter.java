package org.unreal.modelrouter.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.WebFilterChainServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.config.ExcludedPathsConfig;
import org.unreal.modelrouter.security.config.SecurityProperties;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Spring Security集成的API Key认证过滤器
 * 与Spring Security框架集成，支持API Key和JWT认证
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true", matchIfMissing = true)
public class SpringSecurityAuthenticationFilter implements WebFilter {
    
    private final SecurityProperties securityProperties;
    private final ServerAuthenticationConverter authenticationConverter;
    private final ReactiveAuthenticationManager authenticationManager;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 检查是否需要认证
        if (!requiresAuthentication(exchange)) {
            return chain.filter(exchange);
        }
        
        // 如果API Key和JWT都未启用，则跳过认证
        if (!securityProperties.getApiKey().isEnabled() && !securityProperties.getJwt().isEnabled()) {
            return chain.filter(exchange);
        }
        
        // 转换请求为认证对象并执行认证
        return authenticationConverter.convert(exchange)
                .flatMap(authentication -> {
                    // 使用认证管理器进行实际认证
                    return authenticationManager.authenticate(authentication)
                            .flatMap(authenticated -> {
                                // 创建已认证的安全上下文
                                SecurityContextImpl securityContext = new SecurityContextImpl(authenticated);
                                // 在安全上下文中继续执行过滤器链
                                return chain.filter(exchange)
                                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                            })
                            .onErrorResume(throwable -> {
                                // 认证失败，传递给失败处理器
                                return Mono.error(throwable);
                            });
                });
    }
    
    /**
     * 检查请求是否需要认证
     */
    private boolean requiresAuthentication(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        
        // 排除不需要认证的路径
        return !ExcludedPathsConfig.isAuthExcluded(path);
    }
    
    /**
     * 默认的认证转换器
     */
    public static class DefaultAuthenticationConverter implements ServerAuthenticationConverter {
        
        private final SecurityProperties securityProperties;
        
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
            
            // 也支持从Authorization头中提取（Bearer格式）
            List<String> authHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
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
                return jwtHeaders.get(0);
            }
            
            // 兼容Bearer格式
            List<String> authHeaders = exchange.getRequest().getHeaders().get(securityProperties.getJwt().getJwtHeader());
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }
            return null;
        }
    }
}
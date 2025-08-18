package org.unreal.modelrouter.security.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.WebFilterChainServerAuthenticationSuccessHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Spring Security集成的API Key认证过滤器
 * 与Spring Security框架集成，支持API Key和JWT认证
 */
@Slf4j
@RequiredArgsConstructor
public class SpringSecurityApiKeyAuthenticationFilter implements WebFilter {
    
    private final SecurityProperties securityProperties;
    private final ServerAuthenticationConverter authenticationConverter;
    private final ServerAuthenticationSuccessHandler successHandler;
    private final ServerAuthenticationFailureHandler failureHandler;
    
    /**
     * 默认构造函数，使用默认的成功处理器
     */
    public SpringSecurityApiKeyAuthenticationFilter(
            SecurityProperties securityProperties,
            ServerAuthenticationConverter authenticationConverter,
            ServerAuthenticationFailureHandler failureHandler) {
        this.securityProperties = securityProperties;
        this.authenticationConverter = authenticationConverter;
        this.successHandler = new WebFilterChainServerAuthenticationSuccessHandler();
        this.failureHandler = failureHandler;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 检查是否需要认证
        if (!requiresAuthentication(exchange)) {
            return chain.filter(exchange);
        }
        
        // 转换请求为认证对象
        return authenticationConverter.convert(exchange)
                .cast(Authentication.class)
                .flatMap(authentication -> {
                    // 将认证对象存储到exchange中，让认证管理器处理
                    exchange.getAttributes().put("AUTHENTICATION_REQUEST", authentication);
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }
    
    /**
     * 检查请求是否需要认证
     */
    private boolean requiresAuthentication(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        
        // 排除不需要认证的路径
        return !isExcludedPath(path);
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
    
    /**
     * 执行认证
     */
    private Mono<Authentication> authenticate(ServerWebExchange exchange, Authentication authentication) {
        // 这里应该调用认证管理器进行认证
        // 但由于我们在过滤器中，我们需要手动处理
        log.debug("执行认证: {}", authentication.getClass().getSimpleName());
        
        // 返回未认证的Authentication，让后续的认证管理器处理
        return Mono.just(authentication);
    }
    
    /**
     * 将认证信息设置到安全上下文中
     */
    private Mono<Void> setAuthenticationInContext(Authentication authentication) {
        return ReactiveSecurityContextHolder.getContext()
                .doOnNext(securityContext -> {
                    securityContext.setAuthentication(authentication);
                    log.debug("设置安全上下文: {}", authentication.getName());
                })
                .then();
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
            // 首先尝试提取API Key
            String apiKey = extractApiKey(exchange);
            if (apiKey != null) {
                log.debug("提取到API Key，创建API Key认证对象");
                return Mono.just(new ApiKeyAuthentication(apiKey));
            }
            
            // 然后尝试提取JWT令牌
            if (securityProperties.getJwt().isEnabled()) {
                String jwtToken = extractJwtToken(exchange);
                if (jwtToken != null) {
                    log.debug("提取到JWT令牌，创建JWT认证对象");
                    return Mono.just(new JwtAuthentication(jwtToken));
                }
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
            List<String> authHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
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
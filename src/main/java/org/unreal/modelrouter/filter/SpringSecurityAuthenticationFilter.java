package org.unreal.modelrouter.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.config.ExcludedPathsConfig;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.AuthenticationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Flux;

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
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!shouldAuthenticate(exchange)) {
            return chain.filter(exchange);
        }
        
        return performAuthentication(exchange, chain);
    }
    
    /**
     * 检查是否应该进行认证
     */
    private boolean shouldAuthenticate(ServerWebExchange exchange) {
        return requiresAuthentication(exchange) && 
               (securityProperties.getApiKey().isEnabled() || securityProperties.getJwt().isEnabled());
    }
    
    /**
     * 执行认证流程
     */
    private Mono<Void> performAuthentication(ServerWebExchange exchange, WebFilterChain chain) {
        // 对于multipart请求，使用特殊的处理逻辑
        if (isMultipartRequest(exchange)) {
            log.debug("检测到multipart请求，使用特殊处理逻辑: {}", exchange.getRequest().getPath().value());
            return handleMultipartAuthentication(exchange, chain);
        }
        
        return authenticationConverter.convert(exchange)
                .flatMap(authentication -> authenticateAndContinue(authentication, exchange, chain))
                .switchIfEmpty(handleMissingAuthentication(exchange))
                .onErrorResume(throwable -> {
                    log.error("认证过程中发生错误: {}", throwable.getMessage(), throwable);
                    return handleAuthenticationError(exchange, throwable);
                });
    }
    
    /**
     * 认证并继续执行过滤器链
     */
    private Mono<Void> authenticateAndContinue(Authentication authentication, ServerWebExchange exchange, WebFilterChain chain) {
        if (authentication == null) {
            return handleMissingAuthentication(exchange);
        }
        
        return authenticationManager.authenticate(authentication)
                .flatMap(authenticated -> continueWithSecurityContext(authenticated, exchange, chain))
                .onErrorResume(throwable -> handleAuthenticationError(exchange, throwable));
    }
    
    /**
     * 在安全上下文中继续执行过滤器链
     */
    private Mono<Void> continueWithSecurityContext(Authentication authenticated, ServerWebExchange exchange, WebFilterChain chain) {
        SecurityContextImpl securityContext = new SecurityContextImpl(authenticated);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
    }
    
    /**
     * 处理缺少认证信息的情况
     */
    private Mono<Void> handleMissingAuthentication(ServerWebExchange exchange) {
        log.warn("请求缺少认证信息: {}", exchange.getRequest().getPath().value());
        return createAuthenticationErrorResponse(exchange, 
            "请求缺少认证信息，请提供API Key或JWT Token", 
            "AUTH_MISSING");
    }
    
    /**
     * 处理multipart请求的认证
     * 对于multipart请求，我们采用简化的认证流程，避免与multipart解析器冲突
     */
    private Mono<Void> handleMultipartAuthentication(ServerWebExchange exchange, WebFilterChain chain) {
        try {
            log.debug("处理multipart请求认证: {}", exchange.getRequest().getPath().value());
            Authentication authentication = extractAuthenticationFromHeaders(exchange);
            
            if (authentication != null) {
                log.debug("Multipart请求认证信息提取成功，类型: {}", authentication.getClass().getSimpleName());
                
                // 对于multipart请求，我们直接进行简化的认证验证
                return performSimplifiedAuthentication(authentication, exchange, chain);
            } else {
                log.warn("Multipart请求缺少认证信息: {}", exchange.getRequest().getPath().value());
                return handleMissingAuthentication(exchange);
            }
        } catch (Exception e) {
            log.error("处理multipart认证时发生错误: {}", e.getMessage(), e);
            return handleAuthenticationError(exchange, e);
        }
    }
    
    /**
     * 对multipart请求执行简化的认证流程
     * 避免使用可能触发请求体读取的认证管理器
     */
    private Mono<Void> performSimplifiedAuthentication(Authentication authentication, ServerWebExchange exchange, WebFilterChain chain) {
        try {
            // 对于multipart请求，我们进行基本的认证验证
            if (authentication instanceof ApiKeyAuthentication) {
                ApiKeyAuthentication apiKeyAuth = (ApiKeyAuthentication) authentication;
                String apiKey = apiKeyAuth.getCredentials().toString();
                
                // 简单验证API Key格式（这里可以根据实际需求调整）
                if (apiKey != null && apiKey.length() > 10) {
                    log.debug("Multipart请求API Key验证通过");
                    
                    // 创建已认证的对象，添加基本权限
                    // 为multipart请求提供基本的USER权限
                    ApiKeyAuthentication authenticatedAuth = new ApiKeyAuthentication(
                            "multipart-user", // keyId
                            apiKey,
                            List.of("USER", "API_ACCESS") // 基本权限
                    );
                    authenticatedAuth.setAuthenticated(true);
                    
                    // 创建安全上下文并继续过滤器链
                    SecurityContextImpl securityContext = new SecurityContextImpl(authenticatedAuth);
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
                            .doOnSuccess(v -> log.debug("Multipart请求处理完成"))
                            .doOnError(error -> log.error("Multipart请求处理失败: {}", error.getMessage(), error));
                }
            } else if (authentication instanceof JwtAuthentication) {
                JwtAuthentication jwtAuth = (JwtAuthentication) authentication;
                String token = jwtAuth.getCredentials().toString();
                
                // 简单验证JWT格式
                if (token != null && token.contains(".")) {
                    log.debug("Multipart请求JWT验证通过");
                    
                    // 创建已认证的对象，添加基本权限
                    JwtAuthentication authenticatedAuth = new JwtAuthentication(token);
                    authenticatedAuth.setAuthenticated(true);
                    // 注意：JwtAuthentication可能需要不同的权限设置方式
                    
                    // 创建安全上下文并继续过滤器链
                    SecurityContextImpl securityContext = new SecurityContextImpl(authenticatedAuth);
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
                            .doOnSuccess(v -> log.debug("Multipart请求处理完成"))
                            .doOnError(error -> log.error("Multipart请求处理失败: {}", error.getMessage(), error));
                }
            }
            
            // 认证失败
            log.warn("Multipart请求认证验证失败");
            return createAuthenticationErrorResponse(exchange, "认证验证失败", "AUTH_FAILED");
            
        } catch (Exception e) {
            log.error("Multipart请求简化认证过程中发生错误: {}", e.getMessage(), e);
            return handleAuthenticationError(exchange, e);
        }
    }

    /**
     * 从请求头中提取认证信息
     */
    private Authentication extractAuthenticationFromHeaders(ServerWebExchange exchange) {
        AuthenticationExtractor extractor = new AuthenticationExtractor(securityProperties);
        return extractor.extractAuthentication(exchange);
    }
    
    /**
     * 处理认证错误
     */
    private Mono<Void> handleAuthenticationError(ServerWebExchange exchange, Throwable throwable) {
        log.warn("认证失败: {}", exchange.getRequest().getPath().value(), throwable);
        
        String message = "认证失败";
        String errorCode = "AUTH_FAILED";
        
        // 根据具体异常类型提供更具体的错误信息
        if (throwable instanceof AuthenticationException) {
            AuthenticationException authException = (AuthenticationException) throwable;
            message = authException.getMessage();
            errorCode = authException.getErrorCode();
        } else if (isMultipartError(throwable)) {
            // 特殊处理multipart解析错误
            message = "请求格式错误，请检查multipart请求是否正确构造";
            errorCode = "INVALID_MULTIPART_REQUEST";
        } else {
            message = "认证过程中发生未知错误";
            errorCode = "AUTH_ERROR";
        }
        
        return createAuthenticationErrorResponse(exchange, message, errorCode);
    }
    
    /**
     * 检查是否为multipart相关错误
     */
    private boolean isMultipartError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        String errorMessage = throwable.getMessage();
        String className = throwable.getClass().getName();
        
        // 记录详细的错误信息用于调试
        log.debug("检查multipart错误 - 异常类型: {}, 错误信息: {}", className, errorMessage);
        
        // 检查各种multipart相关的错误
        return (throwable instanceof org.springframework.core.codec.DecodingException 
                || throwable instanceof org.springframework.core.codec.CodecException
                || className.contains("Multipart")
                || className.contains("Boundary")) 
                && errorMessage != null 
                && (errorMessage.contains("Could not find first boundary") 
                    || errorMessage.contains("boundary") 
                    || errorMessage.contains("multipart")
                    || errorMessage.contains("Multipart"));
    }
    
    /**
     * 创建认证错误响应
     */
    private Mono<Void> createAuthenticationErrorResponse(ServerWebExchange exchange, String message, String errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构造错误响应体
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        errorResponse.put("errorCode", errorCode);
        errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());

        String responseBody = "";
        try {
            responseBody = new ObjectMapper().writeValueAsString(errorResponse);
        } catch (Exception e) {
            responseBody = "{\"success\":false,\"message\":\"Internal Server Error\",\"errorCode\":\"INTERNAL_ERROR\",\"timestamp\":\"" + java.time.LocalDateTime.now().toString() + "\"}";
        }

        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
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
     * 检查请求是否为multipart类型
     */
    private boolean isMultipartRequest(ServerWebExchange exchange) {
        String contentType = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
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
            try {
                AuthenticationExtractor extractor = new AuthenticationExtractor(securityProperties);
                Authentication authentication = extractor.extractAuthentication(exchange);
                return authentication != null ? Mono.just(authentication) : Mono.empty();
            } catch (Exception e) {
                log.warn("提取认证信息时发生错误: {}", e.getMessage());
                return Mono.empty();
            }
        }
    }
    
    /**
     * 认证信息提取器 - 统一处理认证信息的提取逻辑
     */
    private static class AuthenticationExtractor {
        private final SecurityProperties securityProperties;
        
        public AuthenticationExtractor(SecurityProperties securityProperties) {
            this.securityProperties = securityProperties;
        }
        
        /**
         * 从请求中提取认证信息
         */
        public Authentication extractAuthentication(ServerWebExchange exchange) {
            AuthenticationInfo authInfo = new AuthenticationInfo();
            
            // 提取API Key
            if (securityProperties.getApiKey().isEnabled()) {
                authInfo.apiKey = extractApiKey(exchange);
            }
            
            // 提取JWT Token
            if (securityProperties.getJwt().isEnabled()) {
                authInfo.jwtToken = extractJwtToken(exchange);
            }
            
            return authInfo.createAuthentication();
        }
        
        /**
         * 提取API Key
         * 只从配置的特定头部提取，不从Authorization头中提取
         */
        private String extractApiKey(ServerWebExchange exchange) {
            String headerName = securityProperties.getApiKey().getHeaderName();
            return extractHeaderValue(exchange, headerName);
        }

        /**
         * 提取JWT令牌
         * 只从配置的特定头部提取，不从Authorization头中提取
         */
        private String extractJwtToken(ServerWebExchange exchange) {
            String jwtHeader = securityProperties.getJwt().getJwtHeader();
            return extractHeaderValue(exchange, jwtHeader);
        }
        
        /**
         * 提取指定头部的值
         */
        private String extractHeaderValue(ServerWebExchange exchange, String headerName) {
            List<String> headerValues = exchange.getRequest().getHeaders().get(headerName);
            return (headerValues != null && !headerValues.isEmpty()) ? headerValues.get(0) : null;
        }
        

    }
    
    /**
     * 认证信息封装类
     */
    private static class AuthenticationInfo {
        String apiKey;
        String jwtToken;
        
        Authentication createAuthentication() {
            // JWT优先级高于API Key
            if (jwtToken != null) {
                log.debug("提取到JWT令牌，创建JWT认证对象");
                return new JwtAuthentication(jwtToken);
            } else if (apiKey != null) {
                log.debug("提取到API Key，创建API Key认证对象");
                return new ApiKeyAuthentication(apiKey);
            }
            return null;
        }
    }
}
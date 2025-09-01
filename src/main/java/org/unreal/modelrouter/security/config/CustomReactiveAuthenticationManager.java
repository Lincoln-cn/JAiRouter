package org.unreal.modelrouter.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.unreal.modelrouter.exception.exception.SecurityAuthenticationException;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;

/**
 * 自定义响应式认证管理器
 * 处理API Key和JWT令牌认证
 */
@Slf4j
@RequiredArgsConstructor
public class CustomReactiveAuthenticationManager implements ReactiveAuthenticationManager {
    
    private final ApiKeyService apiKeyService;
    private final JwtTokenValidator jwtTokenValidator;
    private final SecurityProperties securityProperties;
    
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) throws AuthenticationException {
        log.debug("开始认证，认证类型: {}", authentication.getClass().getSimpleName());
        
        // 如果是API Key认证
        if (authentication instanceof ApiKeyAuthentication) {
            return authenticateApiKey((ApiKeyAuthentication) authentication);
        } 
        // 如果是JWT认证
        else if (authentication instanceof JwtAuthentication) {
            return authenticateJwt((JwtAuthentication) authentication);
        }
        
        // 如果都不是，返回认证失败
        log.warn("不支持的认证类型: {}", authentication.getClass().getSimpleName());
        return Mono.error(new SecurityAuthenticationException(
                "UNSUPPORTED_AUTH_TYPE", 
                "不支持的认证类型: " + authentication.getClass().getSimpleName()
        ));
    }
    
    /**
     * 认证API Key
     */
    private Mono<Authentication> authenticateApiKey(ApiKeyAuthentication authentication) {
        // 检查API Key功能是否启用
        if (!securityProperties.getApiKey().isEnabled()) {
            log.debug("API Key认证未启用");
            return Mono.error(new SecurityAuthenticationException(
                    "API_KEY_DISABLED", 
                    "API Key认证功能未启用"
            ));
        }
        
        String apiKey = (String) authentication.getCredentials();
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.debug("API Key为空");
            return Mono.error(new SecurityAuthenticationException(
                    "EMPTY_API_KEY", 
                    "API Key不能为空"
            ));
        }
        
        return apiKeyService.validateApiKey(apiKey)
                .doOnNext(apiKeyInfo -> log.debug("API Key认证成功: {}", apiKeyInfo.getKeyId()))
                .map(apiKeyInfo -> {
                    // 创建已认证的Authentication对象
                    ApiKeyAuthentication authenticated = new ApiKeyAuthentication(
                            apiKeyInfo.getKeyId(),
                            apiKey,
                            apiKeyInfo.getPermissions()
                    );
                    authenticated.setAuthenticated(true);
                    authenticated.setDetails(apiKeyInfo);
                    return (Authentication) authenticated;
                })
                .doOnError(error -> log.debug("API Key认证失败: {}", error.getMessage()))
                .onErrorMap(throwable -> {
                    if (throwable instanceof AuthenticationException) {
                        return throwable;
                    }
                    return new SecurityAuthenticationException(
                            "API_KEY_AUTH_FAILED", 
                            "API Key认证失败: " + throwable.getMessage(),
                            throwable
                    );
                });
    }
    
    /**
     * 认证JWT令牌
     */
    private Mono<Authentication> authenticateJwt(JwtAuthentication authentication) {
        // 检查JWT功能是否启用
        if (!securityProperties.getJwt().isEnabled()) {
            log.debug("JWT认证未启用");
            return Mono.error(new SecurityAuthenticationException(
                    "JWT_DISABLED", 
                    "JWT认证功能未启用"
            ));
        }
        
        String token = (String) authentication.getCredentials();
        
        if (token == null || token.trim().isEmpty()) {
            log.debug("JWT令牌为空");
            return Mono.error(new SecurityAuthenticationException(
                    "EMPTY_JWT_TOKEN", 
                    "JWT令牌不能为空"
            ));
        }
        
        return jwtTokenValidator.validateToken(token)
                .doOnNext(validatedAuth -> log.debug("JWT令牌认证成功: {}", validatedAuth.getName()))
                .doOnError(error -> log.debug("JWT令牌认证失败: {}", error.getMessage()))
                .onErrorMap(throwable -> {
                    if (throwable instanceof AuthenticationException) {
                        return throwable;
                    }
                    
                    // 检查是否是JWT过期相关的错误
                    String message = throwable.getMessage();
                    if (message != null && message.contains("expired")) {
                        return new org.unreal.modelrouter.exception.exception.AuthenticationException(
                                "JWT令牌已过期，请重新获取",
                                "EXPIRED_JWT_TOKEN"
                        );
                    }
                    
                    return new org.unreal.modelrouter.exception.exception.SecurityAuthenticationException(
                            "JWT_AUTH_FAILED", 
                            "JWT令牌认证失败: " + throwable.getMessage(),
                            throwable
                    );
                });
    }
}
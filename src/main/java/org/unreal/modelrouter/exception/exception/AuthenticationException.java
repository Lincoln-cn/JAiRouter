package org.unreal.modelrouter.exception.exception;

import org.springframework.http.HttpStatus;

/**
 * 认证异常类
 * 用于处理API Key和JWT认证相关的异常
 */
public class AuthenticationException extends SecurityException {
    
    public static final String INVALID_API_KEY = "INVALID_API_KEY";
    public static final String EXPIRED_API_KEY = "EXPIRED_API_KEY";
    public static final String MISSING_API_KEY = "MISSING_API_KEY";
    public static final String INVALID_JWT_TOKEN = "INVALID_JWT_TOKEN";
    public static final String EXPIRED_JWT_TOKEN = "EXPIRED_JWT_TOKEN";
    public static final String BLACKLISTED_TOKEN = "BLACKLISTED_TOKEN";
    
    public AuthenticationException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.UNAUTHORIZED);
    }
    
    public AuthenticationException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * 创建无效API Key异常
     */
    public static AuthenticationException invalidApiKey() {
        return new AuthenticationException("无效的API Key", INVALID_API_KEY);
    }
    
    /**
     * 创建过期API Key异常
     */
    public static AuthenticationException expiredApiKey() {
        return new AuthenticationException("API Key已过期", EXPIRED_API_KEY);
    }
    
    /**
     * 创建缺失API Key异常
     */
    public static AuthenticationException missingApiKey() {
        return new AuthenticationException("缺少API Key", MISSING_API_KEY);
    }
    
    /**
     * 创建无效JWT令牌异常
     */
    public static AuthenticationException invalidJwtToken() {
        return new AuthenticationException("无效的JWT令牌", INVALID_JWT_TOKEN);
    }
    
    /**
     * 创建过期JWT令牌异常
     */
    public static AuthenticationException expiredJwtToken() {
        return new AuthenticationException("JWT令牌已过期", EXPIRED_JWT_TOKEN);
    }
    
    /**
     * 创建黑名单令牌异常
     */
    public static AuthenticationException blacklistedToken() {
        return new AuthenticationException("令牌已被列入黑名单", BLACKLISTED_TOKEN);
    }
}
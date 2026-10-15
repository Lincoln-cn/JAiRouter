package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.constants.ErrorCode;

/**
 * 认证异常类
 * 用于处理API Key和JWT认证相关的异常
 *
 * <p>错误码的单一事实来源是 {@link ErrorCode}（issue #370 方案 A）：本类的码常量一律由它派生。</p>
 */
public class AuthenticationException extends SecurityException {
    
    public static final String INVALID_API_KEY = ErrorCode.INVALID_API_KEY.code();
    public static final String EXPIRED_API_KEY = ErrorCode.EXPIRED_API_KEY.code();
    public static final String EXPIRED_JWT_TOKEN = ErrorCode.EXPIRED_JWT_TOKEN.code();
    public static final String DAILY_QUOTA_EXCEEDED = ErrorCode.DAILY_QUOTA_EXCEEDED.code();
    public static final String TOKEN_QUOTA_EXCEEDED = ErrorCode.TOKEN_QUOTA_EXCEEDED.code();
    public static final String RATE_LIMIT_EXCEEDED = ErrorCode.RATE_LIMIT_EXCEEDED.code();
    
    public AuthenticationException(final String message, final String errorCode) {
        super(message, errorCode, HttpStatus.UNAUTHORIZED);
    }

    public AuthenticationException(final String message, final Throwable cause, final String errorCode) {
        super(message, cause, errorCode, HttpStatus.UNAUTHORIZED);
    }

    public AuthenticationException(final String message, final String errorCode, final HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
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
     * 创建过期JWT令牌异常
     */
    public static AuthenticationException expiredJwtToken() {
        return new AuthenticationException("JWT令牌已过期", EXPIRED_JWT_TOKEN);
    }
    
    /**
     * 创建每日配额超限异常
     */
    public static AuthenticationException dailyQuotaExceeded() {
        return new AuthenticationException("超过每日请求配额限制", DAILY_QUOTA_EXCEEDED);
    }

    /**
     * 创建 Token 配额超限异常
     */
    public static AuthenticationException tokenQuotaExceeded() {
        return new AuthenticationException("超过每日 Token 使用配额限制", TOKEN_QUOTA_EXCEEDED);
    }

    /**
     * 创建速率限制超限异常（HTTP 429）
     */
    public static AuthenticationException rateLimitExceeded() {
        return new AuthenticationException("请求频率超过限制，请稍后再试",
                RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS);
    }
}
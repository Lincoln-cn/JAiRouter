package org.unreal.modelrouter.exception.exception;

import org.springframework.http.HttpStatus;

/**
 * 授权异常类
 * 用于处理权限不足相关的异常
 */
public class AuthorizationException extends SecurityException {
    
    public static final String INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String RESOURCE_FORBIDDEN = "RESOURCE_FORBIDDEN";
    
    public AuthorizationException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.FORBIDDEN);
    }
    
    public AuthorizationException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode, HttpStatus.FORBIDDEN);
    }
    
    /**
     * 创建权限不足异常
     */
    public static AuthorizationException insufficientPermissions(String requiredPermission) {
        return new AuthorizationException(
            String.format("权限不足，需要权限: %s", requiredPermission), 
            INSUFFICIENT_PERMISSIONS
        );
    }
    
    /**
     * 创建访问被拒绝异常
     */
    public static AuthorizationException accessDenied(String resource) {
        return new AuthorizationException(
            String.format("访问被拒绝，资源: %s", resource), 
            ACCESS_DENIED
        );
    }
    
    /**
     * 创建资源禁止访问异常
     */
    public static AuthorizationException resourceForbidden(String resource) {
        return new AuthorizationException(
            String.format("禁止访问资源: %s", resource), 
            RESOURCE_FORBIDDEN
        );
    }
}
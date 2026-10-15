package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 授权异常类
 * 用于处理权限不足相关的异常
 */
public class AuthorizationException extends SecurityException {
    
    public AuthorizationException(final String message, final String errorCode) {
        super(message, errorCode, HttpStatus.FORBIDDEN);
    }
    
    public AuthorizationException(final String message, final Throwable cause, final String errorCode) {
        super(message, cause, errorCode, HttpStatus.FORBIDDEN);
    }
}
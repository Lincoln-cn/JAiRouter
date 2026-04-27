package org.unreal.modelrouter.exception;

import org.springframework.http.HttpStatus;

/**
 * 安全异常基类
 */
public class SecurityException extends RuntimeException {
    
    /**
     * 错误代码
     */
    private final String errorCode;
    
    /**
     * HTTP状态码
     */
    private final HttpStatus httpStatus;
    
    public SecurityException(final String message, final String aErrorCode, final HttpStatus aHttpStatus) {
        super(message);
        this.errorCode = aErrorCode;
        this.httpStatus = aHttpStatus;
    }
    
    public SecurityException(final String message, final Throwable cause, final String aErrorCode, final HttpStatus aHttpStatus) {
        super(message, cause);
        this.errorCode = aErrorCode;
        this.httpStatus = aHttpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
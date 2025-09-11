package org.unreal.modelrouter.exception;

import org.springframework.http.HttpStatus;

/**
 * 下游服务异常
 * 用于区分本地服务异常和下游服务异常
 */
public class DownstreamServiceException extends RuntimeException {
    
    private final HttpStatus statusCode;
    
    public DownstreamServiceException(String message, HttpStatus statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
    
    public DownstreamServiceException(String message, HttpStatus statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
    
    public HttpStatus getStatusCode() {
        return statusCode;
    }
}

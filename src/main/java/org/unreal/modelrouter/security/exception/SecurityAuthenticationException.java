package org.unreal.modelrouter.security.exception;

/**
 * Spring Security认证异常类
 * 继承Spring Security的AuthenticationException，用于认证管理器
 */
public class SecurityAuthenticationException extends org.springframework.security.core.AuthenticationException {
    
    private final String errorCode;
    
    public SecurityAuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public SecurityAuthenticationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
package org.unreal.modelrouter.common.exception;

/**
 * Spring Security认证异常类
 * 继承Spring Security的AuthenticationException，用于认证管理器
 */
public class SecurityAuthenticationException extends org.springframework.security.core.AuthenticationException {
    
    private final String errorCode;
    
    public SecurityAuthenticationException(final String aErrorCode, final String message) {
        super(message);
        this.errorCode = aErrorCode;
    }
    
    public SecurityAuthenticationException(final String aErrorCode, final String message, final Throwable cause) {
        super(message, cause);
        this.errorCode = aErrorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
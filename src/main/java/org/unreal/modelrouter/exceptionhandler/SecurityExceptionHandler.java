package org.unreal.modelrouter.exceptionhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.unreal.modelrouter.dto.SecurityErrorResponse;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.exception.DownstreamServiceException;
import org.unreal.modelrouter.exception.AuthorizationException;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.exception.SecurityException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * 全局安全异常处理器
 * 处理所有安全相关的异常，提供统一的错误响应格式
 */
@RestControllerAdvice
@Order(1) // 确保安全异常处理器优先于通用异常处理器
public class SecurityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        logger.warn("认证失败: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理授权异常
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthorizationException(AuthorizationException ex) {
        logger.warn("授权失败: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理数据脱敏异常
     */
    @ExceptionHandler(SanitizationException.class)
    public ResponseEntity<SecurityErrorResponse> handleSanitizationException(SanitizationException ex) {
        logger.error("数据脱敏异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message("数据处理失败")  // 不暴露具体的脱敏错误信息
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理通用安全异常
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<SecurityErrorResponse> handleSecurityException(SecurityException ex) {
        logger.error("安全异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理下游服务异常
     */
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<SecurityErrorResponse> handleDownstreamServiceException(DownstreamServiceException ex) {
        logger.warn("下游服务异常: {}", ex.getMessage(), ex);
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(ex.getStatusCode().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode("DOWNSTREAM_SERVICE_ERROR")
                .build();
        
        // 对于认证相关的下游错误，使用401状态码
        HttpStatus status = ex.getStatusCode().value() == 401 ? 
            HttpStatus.UNAUTHORIZED : HttpStatus.valueOf(ex.getStatusCode().value());
            
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * 获取当前请求路径
     * 在WebFlux环境中，可以通过ServerRequest获取路径信息
     */
    private String getCurrentPath() {
        // 在实际的WebFlux环境中，可以通过ServerRequest获取路径
        // 这里先返回一个默认值，后续可以通过RequestContextHolder或其他方式获取
        return "/api/security";
    }
}
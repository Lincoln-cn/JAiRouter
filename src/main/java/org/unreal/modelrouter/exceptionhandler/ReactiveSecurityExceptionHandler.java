package org.unreal.modelrouter.exceptionhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.unreal.modelrouter.dto.SecurityErrorResponse;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.exception.exception.AuthorizationException;
import org.unreal.modelrouter.exception.exception.SanitizationException;
import org.unreal.modelrouter.exception.exception.SecurityException;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;

import java.time.LocalDateTime;

/**
 * 响应式安全异常处理器
 * 专门处理WebFlux环境下的安全异常
 */
@Component
@Order(-2) // 确保在默认异常处理器之前执行
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveSecurityExceptionHandler implements WebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveSecurityExceptionHandler.class);
    
    private final ObjectMapper objectMapper;

    public ReactiveSecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!(ex instanceof SecurityException)) {
            return Mono.error(ex); // 不是安全异常，交给其他处理器
        }

        SecurityException securityException = (SecurityException) ex;
        
        // 根据异常类型进行不同的处理
        if (securityException instanceof AuthenticationException) {
            return handleAuthenticationException(exchange, (AuthenticationException) securityException);
        } else if (securityException instanceof AuthorizationException) {
            return handleAuthorizationException(exchange, (AuthorizationException) securityException);
        } else if (securityException instanceof SanitizationException) {
            return handleSanitizationException(exchange, (SanitizationException) securityException);
        } else {
            return handleGenericSecurityException(exchange, securityException);
        }
    }

    /**
     * 处理认证异常
     */
    private Mono<Void> handleAuthenticationException(ServerWebExchange exchange, AuthenticationException ex) {
        logger.warn("认证失败: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(exchange.getRequest().getPath().value())
                .build();

        return writeErrorResponse(exchange, ex.getHttpStatus(), errorResponse);
    }

    /**
     * 处理授权异常
     */
    private Mono<Void> handleAuthorizationException(ServerWebExchange exchange, AuthorizationException ex) {
        logger.warn("授权失败: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(exchange.getRequest().getPath().value())
                .build();

        return writeErrorResponse(exchange, ex.getHttpStatus(), errorResponse);
    }

    /**
     * 处理数据脱敏异常
     */
    private Mono<Void> handleSanitizationException(ServerWebExchange exchange, SanitizationException ex) {
        logger.error("数据脱敏异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message("数据处理失败")  // 不暴露具体的脱敏错误信息
                .errorCode(ex.getErrorCode())
                .path(exchange.getRequest().getPath().value())
                .build();

        return writeErrorResponse(exchange, ex.getHttpStatus(), errorResponse);
    }

    /**
     * 处理通用安全异常
     */
    private Mono<Void> handleGenericSecurityException(ServerWebExchange exchange, SecurityException ex) {
        logger.error("安全异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(exchange.getRequest().getPath().value())
                .build();

        return writeErrorResponse(exchange, ex.getHttpStatus(), errorResponse);
    }

    /**
     * 写入错误响应
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, SecurityErrorResponse errorResponse) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        try {
            String responseBody = objectMapper.writeValueAsString(errorResponse);
            DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
            DataBuffer buffer = bufferFactory.wrap(responseBody.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            logger.error("写入错误响应失败", e);
            return exchange.getResponse().setComplete();
        }
    }
}
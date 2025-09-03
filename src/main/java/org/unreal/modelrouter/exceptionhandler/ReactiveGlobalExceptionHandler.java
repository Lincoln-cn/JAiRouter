package org.unreal.modelrouter.exceptionhandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.exception.exception.AuthorizationException;
import org.unreal.modelrouter.exception.exception.SanitizationException;
import org.unreal.modelrouter.exception.exception.SecurityException;
import org.unreal.modelrouter.exception.exception.SecurityAuthenticationException;
import org.unreal.modelrouter.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * WebFlux 全局异常处理器
 * 替代 @RestControllerAdvice，避免 ReadOnlyHttpHeaders 问题
 */
@Component
@Order(-2) // 高优先级，在默认异常处理器之前
@RequiredArgsConstructor
public class ReactiveGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveGlobalExceptionHandler.class);
    
    private final ErrorTracker errorTracker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        // 注册JavaTimeModule以支持LocalDateTime序列化
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 如果响应已经提交，直接返回
        if (response.isCommitted()) {
            logger.warn("响应已提交，跳过异常处理: {}", ex.getMessage());
            return Mono.empty();
        }

        try {
            // 记录异常到追踪系统
            recordError(ex);
            
            // 根据异常类型设置响应
            if (ex instanceof SecurityException) {
                return handleSecurityException(exchange, (SecurityException) ex);
            } else if (ex instanceof SecurityAuthenticationException) {
                return handleSecurityAuthenticationException(exchange, (SecurityAuthenticationException) ex);
            } else if (ex instanceof AuthenticationException) {
                // 直接处理AuthenticationException异常，返回具体的错误信息
                return handleAuthenticationException(exchange, (AuthenticationException) ex);
            } else {
                RouterResponse<Void> errorResponse;
                HttpStatus status;
                
                if (ex instanceof ServerWebInputException) {
                    logger.error("请求体读取异常: {}", ex.getMessage());
                    errorResponse = RouterResponse.error("请求体无效或缺失: " + ex.getMessage(), "400");
                    status = HttpStatus.BAD_REQUEST;
                } else if (ex instanceof ResponseStatusException) {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    logger.error("响应状态异常: status={}, message={}", rse.getStatusCode(), rse.getMessage());
                    errorResponse = RouterResponse.error("请求处理失败: " + rse.getReason(), String.valueOf(rse.getStatusCode().value()));
                    status = HttpStatus.resolve(rse.getStatusCode().value());
                    if (status == null) {
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                    }
                } else {
                    logger.error("系统异常", ex);
                    String errorMessage = ex.getMessage();
                    if (errorMessage == null) {
                        errorMessage = ex.getClass().getSimpleName();
                    }
                    errorResponse = RouterResponse.error("系统异常: " + errorMessage, "500");
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                }
                
                // 安全地设置响应状态和头
                return setResponse(response, errorResponse, status);
            }
        } catch (Exception e) {
            logger.error("异常处理器本身发生异常", e);
            // 如果异常处理器本身出错，返回最简单的错误响应
            return setSimpleErrorResponse(response);
        }
    }
    
    /**
     * 记录错误信息
     */
    private void recordError(Throwable ex) {
        try {
            Map<String, Object> additionalInfo = new HashMap<>();
            additionalInfo.put("handler", "ReactiveGlobalExceptionHandler");
            
            TracingContext context = TracingContextHolder.getCurrentContext();
            if (context != null && context.isActive()) {
                additionalInfo.put("traceId", context.getTraceId());
                additionalInfo.put("spanId", context.getSpanId());
            }
            
            // 根据异常类型设置不同的响应状态
            if (ex instanceof ServerWebInputException) {
                additionalInfo.put("responseStatus", "400");
            } else if (ex instanceof ResponseStatusException) {
                ResponseStatusException rse = (ResponseStatusException) ex;
                additionalInfo.put("responseStatus", String.valueOf(rse.getStatusCode().value()));
            } else if (ex instanceof SecurityException) {
                additionalInfo.put("responseStatus", String.valueOf(((SecurityException) ex).getHttpStatus().value()));
                additionalInfo.put("errorCode", ((SecurityException) ex).getErrorCode());
            } else if (ex instanceof SecurityAuthenticationException) {
                additionalInfo.put("responseStatus", "401");
                additionalInfo.put("errorCode", ((SecurityAuthenticationException) ex).getErrorCode());
            } else {
                additionalInfo.put("responseStatus", "500");
            }
            
            // 添加异常类型信息
            additionalInfo.put("exceptionType", ex.getClass().getSimpleName());
            
            errorTracker.trackError(ex, "reactive_global_exception_handling", additionalInfo);
        } catch (Exception trackingException) {
            logger.warn("错误追踪失败: {}", trackingException.getMessage());
        }
    }
    
    /**
     * 安全地设置响应
     */
    private Mono<Void> setResponse(ServerHttpResponse response, RouterResponse<Void> errorResponse, HttpStatus status) {
        try {
            // 设置状态码
            response.setStatusCode(status);
            
            // 安全地设置响应头
            if (!response.getHeaders().containsKey("Content-Type")) {
                response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            }
            
            // 序列化响应体
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(jsonResponse.getBytes());
            
            return response.writeWith(Mono.just(buffer));
            
        } catch (Exception e) {
            logger.error("设置错误响应失败", e);
            return setSimpleErrorResponse(response);
        }
    }
    
    /**
     * 处理安全异常
     */
    private Mono<Void> handleSecurityException(ServerWebExchange exchange, SecurityException ex) {
        // 根据异常类型进行不同的处理
        if (ex instanceof AuthenticationException) {
            return handleAuthenticationException(exchange, (AuthenticationException) ex);
        } else if (ex instanceof AuthorizationException) {
            return handleAuthorizationException(exchange, (AuthorizationException) ex);
        } else if (ex instanceof SanitizationException) {
            return handleSanitizationException(exchange, (SanitizationException) ex);
        } else {
            return handleGenericSecurityException(exchange, ex);
        }
    }

    /**
     * 处理认证异常
     */
    private Mono<Void> handleAuthenticationException(ServerWebExchange exchange, AuthenticationException ex) {
        logger.warn("认证失败: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        RouterResponse<Void> errorResponse = RouterResponse.error(ex.getMessage(), ex.getErrorCode());
        return setResponse(exchange.getResponse(), errorResponse, ex.getHttpStatus());
    }

    /**
     * 处理授权异常
     */
    private Mono<Void> handleAuthorizationException(ServerWebExchange exchange, AuthorizationException ex) {
        logger.warn("授权失败: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        RouterResponse<Void> errorResponse = RouterResponse.error(ex.getMessage(), ex.getErrorCode());
        return setResponse(exchange.getResponse(), errorResponse, ex.getHttpStatus());
    }

    /**
     * 处理数据脱敏异常
     */
    private Mono<Void> handleSanitizationException(ServerWebExchange exchange, SanitizationException ex) {
        logger.error("数据脱敏异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        RouterResponse<Void> errorResponse = RouterResponse.error("数据处理失败", ex.getErrorCode());
        return setResponse(exchange.getResponse(), errorResponse, ex.getHttpStatus());
    }

    /**
     * 处理通用安全异常
     */
    private Mono<Void> handleGenericSecurityException(ServerWebExchange exchange, SecurityException ex) {
        logger.error("安全异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        RouterResponse<Void> errorResponse = RouterResponse.error(ex.getMessage(), ex.getErrorCode());
        return setResponse(exchange.getResponse(), errorResponse, ex.getHttpStatus());
    }
    
    /**
     * 处理Spring Security认证异常
     */
    private Mono<Void> handleSecurityAuthenticationException(ServerWebExchange exchange, SecurityAuthenticationException ex) {
        logger.warn("认证失败: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        RouterResponse<Void> errorResponse = RouterResponse.error(ex.getMessage(), ex.getErrorCode());
        return setResponse(exchange.getResponse(), errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * 设置最简单的错误响应
     */
    private Mono<Void> setSimpleErrorResponse(ServerHttpResponse response) {
        try {
            if (!response.isCommitted()) {
                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                String simpleError = "{\"success\":false,\"message\":\"Internal Server Error\",\"code\":\"500\"}";
                DataBuffer buffer = response.bufferFactory().wrap(simpleError.getBytes());
                return response.writeWith(Mono.just(buffer));
            }
        } catch (Exception e) {
            logger.error("设置简单错误响应也失败了", e);
        }
        return Mono.empty();
    }
}
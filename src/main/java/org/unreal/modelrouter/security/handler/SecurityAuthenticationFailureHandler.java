package org.unreal.modelrouter.security.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全认证失败处理器
 * 处理认证失败的情况，返回统一的错误响应
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuthenticationFailureHandler 
        implements ServerAuthenticationFailureHandler, ServerAuthenticationEntryPoint {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 默认构造函数
     */
    public SecurityAuthenticationFailureHandler() {
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public Mono<Void> onAuthenticationFailure(
            org.springframework.security.web.server.WebFilterExchange webFilterExchange,
            AuthenticationException ex) {
        
        log.debug("认证失败: {}", ex.getMessage());
        return handleAuthenticationFailure(webFilterExchange.getExchange(), ex, HttpStatus.UNAUTHORIZED);
    }
    
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        log.debug("认证入口点触发: {}", ex.getMessage());
        return handleAuthenticationFailure(exchange, ex, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * 处理认证失败
     */
    private Mono<Void> handleAuthenticationFailure(
            ServerWebExchange exchange, 
            AuthenticationException ex, 
            HttpStatus status) {
        
        // 设置响应状态和头部
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // 构建错误响应
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "AUTHENTICATION_FAILED");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("path", exchange.getRequest().getPath().value());
        errorResponse.put("status", status.value());
        
        // 如果是自定义的安全异常，添加错误代码
        if (ex.getCause() instanceof org.unreal.modelrouter.security.exception.SecurityException) {
            org.unreal.modelrouter.security.exception.SecurityException secEx = 
                    (org.unreal.modelrouter.security.exception.SecurityException) ex.getCause();
            errorResponse.put("errorCode", secEx.getErrorCode());
        }
        
        try {
            // 序列化错误响应
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(jsonResponse.getBytes());
            
            return exchange.getResponse().writeWith(Mono.just(buffer));
            
        } catch (JsonProcessingException e) {
            log.error("序列化错误响应失败", e);
            
            // 如果序列化失败，返回简单的错误响应
            String simpleResponse = "{\"error\":\"AUTHENTICATION_FAILED\",\"message\":\"" + 
                    ex.getMessage() + "\"}";
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(simpleResponse.getBytes());
            
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }
}
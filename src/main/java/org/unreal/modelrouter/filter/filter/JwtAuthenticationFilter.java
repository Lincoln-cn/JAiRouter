package org.unreal.modelrouter.filter.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.constants.SecurityConstants;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.util.SecurityUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT认证过滤器
 * 负责从请求中提取JWT令牌并进行验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(SecurityConstants.JWT_FILTER_ORDER)
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class JwtAuthenticationFilter implements WebFilter {
    
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_HEADER = "X-JWT-Token";
    
    private final JwtTokenValidator jwtTokenValidator;
    private final SecurityProperties securityProperties;
    private final SecurityAuditService auditService;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // 跳过不需要认证的路径
        if (shouldSkipAuthentication(request)) {
            return chain.filter(exchange);
        }
        
        // 提取JWT令牌
        String token = extractToken(exchange);
        
        if (!StringUtils.hasText(token)) {
            // 没有JWT令牌，继续处理（可能有其他认证方式）
            return chain.filter(exchange);
        }
        
        // 验证JWT令牌
        return jwtTokenValidator.validateToken(token)
            .flatMap(authentication -> {
                // 认证成功，记录审计日志
                recordSuccessfulAuthentication(exchange, authentication);
                
                // 设置安全上下文并继续处理
                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            })
            .onErrorResume(AuthenticationException.class, ex -> {
                // 认证失败，记录审计日志
                recordFailedAuthentication(exchange, ex);
                
                // 返回401未授权错误
                return handleAuthenticationFailure(response, ex);
            })
            .onErrorResume(Exception.class, ex -> {
                // 其他错误，记录日志
                log.error("JWT认证过程中发生未预期的错误", ex);
                recordFailedAuthentication(exchange, new AuthenticationException("认证过程中发生内部错误", "INTERNAL_ERROR"));
                
                // 返回500内部服务器错误
                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                return response.setComplete();
            });
    }
    
    /**
     * 从请求中提取JWT令牌
     */
    private String extractToken(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        // 首先尝试从Authorization头中提取Bearer令牌
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        
        // 然后尝试从自定义JWT头中提取
        String jwtHeader = request.getHeaders().getFirst(JWT_HEADER);
        if (StringUtils.hasText(jwtHeader)) {
            return jwtHeader;
        }
        
        // 最后尝试从查询参数中提取（不推荐，但支持某些场景）
        String tokenParam = request.getQueryParams().getFirst("token");
        if (StringUtils.hasText(tokenParam)) {
            log.warn("从查询参数中提取JWT令牌，这可能存在安全风险: {}", 
                SecurityUtils.extractClientIp(exchange));
            return tokenParam;
        }
        
        return null;
    }
    
    /**
     * 判断是否应该跳过认证
     */
    private boolean shouldSkipAuthentication(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        
        // 跳过健康检查和监控端点
        if (path.startsWith("/actuator/") || 
            path.equals("/health") || 
            path.equals("/metrics") ||
            path.startsWith("/swagger-ui/") ||
            path.startsWith("/v3/api-docs/")) {
            return true;
        }
        
        // 跳过静态资源
        if (path.startsWith("/static/") || 
            path.startsWith("/public/") ||
            path.endsWith(".css") ||
            path.endsWith(".js") ||
            path.endsWith(".ico")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理认证失败
     */
    private Mono<Void> handleAuthenticationFailure(ServerHttpResponse response, AuthenticationException ex) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        
        String errorResponse = String.format(
            "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"code\":\"%s\",\"timestamp\":\"%s\"}",
            ex.getMessage(),
            ex.getErrorCode(),
            LocalDateTime.now()
        );
        
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
    }
    
    /**
     * 记录成功的认证事件
     */
    private void recordSuccessfulAuthentication(ServerWebExchange exchange, Authentication authentication) {
        ServerHttpRequest request = exchange.getRequest();
        try {
            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("authType", "JWT");
            additionalData.put("principal", authentication.getName());
            additionalData.put("authorities", authentication.getAuthorities().toString());
            
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("AUTHENTICATION_SUCCESS")
                .userId(authentication.getName())
                .clientIp(SecurityUtils.extractClientIp(exchange))
                .userAgent(SecurityUtils.extractUserAgent(exchange))
                .timestamp(LocalDateTime.now())
                .resource(request.getURI().getPath())
                .action("JWT_AUTHENTICATION")
                .success(true)
                .additionalData(additionalData)
                .build();
            
            auditService.recordEvent(event).subscribe();
            
        } catch (Exception e) {
            log.warn("记录JWT认证成功审计事件时发生错误", e);
        }
    }
    
    /**
     * 记录失败的认证事件
     */
    private void recordFailedAuthentication(ServerWebExchange exchange, AuthenticationException ex) {
        ServerHttpRequest request = exchange.getRequest();
        try {
            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("authType", "JWT");
            additionalData.put("errorCode", ex.getErrorCode());
            
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("AUTHENTICATION_FAILURE")
                .userId("anonymous")
                .clientIp(SecurityUtils.extractClientIp(exchange))
                .userAgent(SecurityUtils.extractUserAgent(exchange))
                .timestamp(LocalDateTime.now())
                .resource(request.getURI().getPath())
                .action("JWT_AUTHENTICATION")
                .success(false)
                .failureReason(ex.getMessage())
                .additionalData(additionalData)
                .build();
            
            auditService.recordEvent(event).subscribe();
            
        } catch (Exception e) {
            log.warn("记录JWT认证失败审计事件时发生错误", e);
        }
    }
}
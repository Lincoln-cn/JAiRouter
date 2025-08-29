package org.unreal.modelrouter.filter.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.constants.SecurityConstants;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key认证过滤器
 * 负责从请求头中提取API Key并进行验证
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "jairouter.security.api-key.enabled", havingValue = "true", matchIfMissing = true)
public class ApiKeyAuthenticationFilter implements WebFilter {
    
    private final ApiKeyService apiKeyService;
    private final SecurityAuditService auditService;
    private final SecurityProperties securityProperties;
    
    @Autowired
    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, 
                                    SecurityAuditService auditService,
                                    SecurityProperties securityProperties) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // 跳过健康检查和监控端点
        String path = request.getPath().value();
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }
        
        // 提取API Key
        String apiKey = extractApiKey(request);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            recordAuthenticationEvent(request, null, false, "缺少API Key");
            return handleAuthenticationFailure(exchange, "缺少API Key", AuthenticationException.MISSING_API_KEY);
        }
        
        // 验证API Key
        return apiKeyService.validateApiKey(apiKey)
                .flatMap(apiKeyInfo -> {
                    // 验证权限
                    if (!hasRequiredPermissions(apiKeyInfo, request)) {
                        recordAuthenticationEvent(request, apiKeyInfo, false, "权限不足");
                        return handleAuthenticationFailure(exchange, "权限不足", AuthenticationException.INVALID_API_KEY);
                    }
                    
                    // 设置认证上下文
                    setAuthenticationContext(exchange, apiKeyInfo);
                    
                    // 记录成功的认证事件
                    recordAuthenticationEvent(request, apiKeyInfo, true, null);
                    
                    // 更新使用统计
                    return apiKeyService.updateUsageStatistics(apiKeyInfo.getKeyId(), true)
                            .then(chain.filter(exchange))
                            .doOnSuccess(unused -> log.debug("API Key认证成功: {}", apiKeyInfo.getKeyId()))
                            .doOnError(error -> {
                                log.error("请求处理失败: {}", error.getMessage());
                                // 更新失败统计
                                apiKeyService.updateUsageStatistics(apiKeyInfo.getKeyId(), false).subscribe();
                            });
                })
                .onErrorResume(AuthenticationException.class, ex -> {
                    log.warn("API Key认证失败: {}", ex.getMessage());
                    recordAuthenticationEvent(request, null, false, ex.getMessage());
                    return handleAuthenticationFailure(exchange, ex.getMessage(), ex.getErrorCode());
                })
                .onErrorResume(Exception.class, ex -> {
                    log.error("API Key认证过程中发生未知错误", ex);
                    recordAuthenticationEvent(request, null, false, "内部错误");
                    return handleAuthenticationFailure(exchange, "认证服务内部错误", "INTERNAL_ERROR");
                });
    }
    
    /**
     * 从请求头中提取API Key
     */
    private String extractApiKey(ServerHttpRequest request) {
        String headerName = securityProperties.getApiKey().getHeaderName();
        List<String> headerValues = request.getHeaders().get(headerName);
        
        if (headerValues != null && !headerValues.isEmpty()) {
            return headerValues.get(0);
        }
        
        // 也支持从Authorization头中提取（Bearer格式）
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        
        return null;
    }
    
    /**
     * 检查是否为排除的路径
     */
    private boolean isExcludedPath(String path) {
        // 排除健康检查和监控端点
        return path.startsWith("/actuator/") ||
               path.equals("/health") ||
               path.equals("/metrics") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/favicon.ico");
    }
    
    /**
     * 检查API Key是否具有所需权限
     */
    private boolean hasRequiredPermissions(ApiKeyInfo apiKeyInfo, ServerHttpRequest request) {
        List<String> permissions = apiKeyInfo.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            // 如果没有配置权限，默认允许所有操作
            return true;
        }
        
        String method = request.getMethod().name();
        String path = request.getPath().value();
        
        // 根据HTTP方法和路径判断所需权限
        String requiredPermission = determineRequiredPermission(method, path);
        
        return permissions.contains(requiredPermission) || permissions.contains("admin");
    }
    
    /**
     * 根据HTTP方法和路径确定所需权限
     */
    private String determineRequiredPermission(String method, String path) {
        switch (method.toUpperCase()) {
            case "GET":
                return "read";
            case "POST":
            case "PUT":
            case "PATCH":
                return "write";
            case "DELETE":
                return "delete";
            default:
                return "read";
        }
    }
    
    /**
     * 设置认证上下文
     */
    private void setAuthenticationContext(ServerWebExchange exchange, ApiKeyInfo apiKeyInfo) {
        // 将API Key信息存储到请求属性中，供后续处理使用
        exchange.getAttributes().put(SecurityConstants.API_KEY_INFO_ATTRIBUTE, apiKeyInfo);
        exchange.getAttributes().put(SecurityConstants.AUTHENTICATED_USER_ID, apiKeyInfo.getKeyId());
        exchange.getAttributes().put(SecurityConstants.USER_PERMISSIONS, apiKeyInfo.getPermissions());
        
        log.debug("设置认证上下文: keyId={}, permissions={}", 
                apiKeyInfo.getKeyId(), apiKeyInfo.getPermissions());
    }
    
    /**
     * 记录认证事件
     */
    private void recordAuthenticationEvent(ServerHttpRequest request, ApiKeyInfo apiKeyInfo, 
                                         boolean success, String failureReason) {
        try {
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventType("API_KEY_AUTHENTICATION")
                    .userId(apiKeyInfo != null ? apiKeyInfo.getKeyId() : "unknown")
                    .clientIp(getClientIp(request))
                    .userAgent(getUserAgent(request))
                    .timestamp(LocalDateTime.now())
                    .resource(request.getPath().value())
                    .action(request.getMethod().name())
                    .success(success)
                    .failureReason(failureReason)
                    .build();
            
            auditService.recordEvent(event).subscribe(
                    unused -> log.debug("记录认证审计事件成功"),
                    error -> log.error("记录认证审计事件失败", error)
            );
        } catch (Exception e) {
            log.error("创建认证审计事件失败", e);
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null ? 
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
    
    /**
     * 获取用户代理
     */
    private String getUserAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }
    
    /**
     * 处理认证失败
     */
    private Mono<Void> handleAuthenticationFailure(ServerWebExchange exchange, String message, String errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        
        String errorResponse = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                errorCode, message, LocalDateTime.now()
        );
        
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
    }
}
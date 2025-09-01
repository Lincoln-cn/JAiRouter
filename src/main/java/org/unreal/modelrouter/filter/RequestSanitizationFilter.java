package org.unreal.modelrouter.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.exception.exception.SanitizationException;
import org.unreal.modelrouter.sanitization.SanitizationService;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.constants.SecurityConstants;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.security.config.ExcludedPathsConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 请求数据脱敏过滤器
 * 负责对传入的请求数据进行脱敏处理
 */
@Slf4j
@Component
@Order(10) // 在认证过滤器之后执行
@ConditionalOnProperty(name = "jairouter.security.sanitization.request.enabled", havingValue = "true", matchIfMissing = true)
public class RequestSanitizationFilter implements WebFilter {
    
    private final SanitizationService sanitizationService;
    private final SecurityAuditService auditService;
    private final SecurityProperties securityProperties;
    
    @Autowired
    public RequestSanitizationFilter(SanitizationService sanitizationService,
                                   SecurityAuditService auditService,
                                   SecurityProperties securityProperties) {
        this.sanitizationService = sanitizationService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 如果请求脱敏功能未启用，直接跳过
        if (!securityProperties.getSanitization().getRequest().isEnabled()) {
            return chain.filter(exchange);
        }
        
        // 跳过不需要脱敏的路径
        String path = request.getPath().value();
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }
        
        // 检查是否为需要处理的内容类型
        String contentType = getContentType(request);
        if (!shouldSanitizeContentType(contentType)) {
            return chain.filter(exchange);
        }
        
        // 检查用户是否在白名单中
        String userId = getUserId(exchange);
        if (userId != null) {
            return sanitizationService.isUserWhitelisted(userId)
                    .flatMap(isWhitelisted -> {
                        if (isWhitelisted) {
                            log.debug("用户在白名单中，跳过请求脱敏: userId={}", userId);
                            return chain.filter(exchange);
                        }
                        return processRequestSanitization(exchange, chain, contentType, userId);
                    })
                    .onErrorResume(error -> {
                        log.error("检查白名单失败，继续执行脱敏: {}", error.getMessage());
                        return processRequestSanitization(exchange, chain, contentType, userId);
                    });
        }
        
        return processRequestSanitization(exchange, chain, contentType, null);
    }
    
    /**
     * 处理请求脱敏
     */
    private Mono<Void> processRequestSanitization(ServerWebExchange exchange, WebFilterChain chain, 
                                                String contentType, String userId) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 如果没有请求体，直接继续
        if (request.getHeaders().getContentLength() == 0) {
            return chain.filter(exchange);
        }
        
        // 读取请求体并进行脱敏
        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    try {
                        // 读取原始内容
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        
                        String originalContent = new String(bytes, StandardCharsets.UTF_8);
                        
                        // 执行脱敏
                        return sanitizationService.sanitizeRequest(originalContent, contentType, userId)
                                .flatMap(sanitizedContent -> {
                                    // 记录脱敏操作
                                    recordSanitizationEvent(request, userId, contentType, 
                                                           !originalContent.equals(sanitizedContent));
                                    
                                    // 创建新的请求体
                                    DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                                    DataBuffer sanitizedBuffer = bufferFactory.wrap(
                                            sanitizedContent.getBytes(StandardCharsets.UTF_8));
                                    
                                    // 创建装饰后的请求
                                    ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
                                        @Override
                                        public Flux<DataBuffer> getBody() {
                                            return Flux.just(sanitizedBuffer);
                                        }
                                        
                                        @Override
                                        public HttpHeaders getHeaders() {
                                            HttpHeaders headers = new HttpHeaders();
                                            headers.putAll(super.getHeaders());
                                            // 更新Content-Length，避免直接修改只读headers
                                            headers.setContentLength(sanitizedContent.getBytes(StandardCharsets.UTF_8).length);
                                            return headers;
                                        }
                                    };
                                    
                                    // 继续处理链
                                    return chain.filter(exchange.mutate().request(decoratedRequest).build());
                                });
                    } catch (Exception e) {
                        DataBufferUtils.release(dataBuffer);
                        return Mono.error(new SanitizationException("请求内容读取失败", e, 
                                SanitizationException.CONTENT_PROCESSING_FAILED));
                    }
                })
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)))
                .onErrorResume(SanitizationException.class, ex -> {
                    log.error("请求脱敏失败: {}", ex.getMessage(), ex);
                    recordSanitizationEvent(request, userId, contentType, false, ex.getMessage());
                    
                    // 根据配置决定是否继续处理还是返回错误
                    if (securityProperties.getSanitization().getRequest().isFailOnError()) {
                        return handleSanitizationFailure(exchange, ex);
                    } else {
                        log.warn("脱敏失败但配置为继续处理，跳过脱敏: {}", ex.getMessage());
                        return chain.filter(exchange);
                    }
                })
                .onErrorResume(Exception.class, ex -> {
                    // 记录更详细的错误信息
                    String errorMessage = "未知错误: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
                    if (ex.getCause() != null) {
                        errorMessage += ", 原因: " + (ex.getCause().getMessage() != null ? ex.getCause().getMessage() : ex.getCause().getClass().getName());
                    }
                    
                    log.error("请求脱敏过程中发生未知错误: {}", errorMessage, ex);
                    recordSanitizationEvent(request, userId, contentType, false, errorMessage);
                    
                    // 发生未知错误时，根据配置决定处理方式
                    if (securityProperties.getSanitization().getRequest().isFailOnError()) {
                        return handleSanitizationFailure(exchange, 
                                new SanitizationException("请求脱敏过程中发生未知错误", ex, 
                                        SanitizationException.CONTENT_PROCESSING_FAILED));
                    } else {
                        return chain.filter(exchange);
                    }
                });
    }
    
    /**
     * 检查是否为排除的路径
     */
    private boolean isExcludedPath(String path) {
        // 使用统一的排除路径配置
        return ExcludedPathsConfig.isDataMaskExcluded(path);
    }
    
    /**
     * 获取内容类型
     */
    private String getContentType(ServerHttpRequest request) {
        MediaType mediaType = request.getHeaders().getContentType();
        return mediaType != null ? mediaType.toString() : "application/octet-stream";
    }
    
    /**
     * 检查是否应该对该内容类型进行脱敏
     */
    private boolean shouldSanitizeContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        // 主要处理文本类型的内容
        return contentType.startsWith("application/json") ||
               contentType.startsWith("application/xml") ||
               contentType.startsWith("text/") ||
               contentType.startsWith("application/x-www-form-urlencoded");
    }
    
    /**
     * 获取用户ID
     */
    private String getUserId(ServerWebExchange exchange) {
        // 从认证上下文中获取用户ID
        Object userId = exchange.getAttribute(SecurityConstants.AUTHENTICATED_USER_ID);
        return userId != null ? userId.toString() : null;
    }
    
    /**
     * 记录脱敏事件
     */
    private void recordSanitizationEvent(ServerHttpRequest request, String userId, String contentType, 
                                       boolean sanitized) {
        recordSanitizationEvent(request, userId, contentType, sanitized, null);
    }
    
    /**
     * 记录脱敏事件（带错误信息）
     */
    private void recordSanitizationEvent(ServerHttpRequest request, String userId, String contentType, 
                                       boolean sanitized, String errorMessage) {
        if (!securityProperties.getSanitization().getRequest().isLogSanitization()) {
            return;
        }
        
        try {
            SecurityAuditEvent.SecurityAuditEventBuilder eventBuilder = SecurityAuditEvent.builder()
                    .eventType("REQUEST_SANITIZATION")
                    .userId(userId != null ? userId : "anonymous")
                    .clientIp(getClientIp(request))
                    .userAgent(getUserAgent(request))
                    .timestamp(LocalDateTime.now())
                    .resource(request.getPath().value())
                    .action("SANITIZE_REQUEST")
                    .success(sanitized && errorMessage == null);
            
            if (errorMessage != null) {
                eventBuilder.failureReason(errorMessage);
            }
            
            // 添加额外信息
            eventBuilder.additionalData(java.util.Map.of(
                    "contentType", contentType,
                    "sanitized", sanitized,
                    "method", request.getMethod().name()
            ));
            
            SecurityAuditEvent event = eventBuilder.build();
            
            auditService.recordEvent(event).subscribe(
                    unused -> log.debug("记录请求脱敏审计事件成功"),
                    error -> log.error("记录请求脱敏审计事件失败", error)
            );
        } catch (Exception e) {
            log.error("创建请求脱敏审计事件失败", e);
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
     * 处理脱敏失败
     */
    private Mono<Void> handleSanitizationFailure(ServerWebExchange exchange, SanitizationException ex) {
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        
        String errorResponse = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                ex.getErrorCode(), ex.getMessage(), LocalDateTime.now()
        );
        
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes()))
        );
    }
}
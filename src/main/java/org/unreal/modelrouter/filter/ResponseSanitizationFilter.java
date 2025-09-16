package org.unreal.modelrouter.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.sanitization.SanitizationService;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.ExcludedPathsConfig;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 响应数据脱敏过滤器
 * 负责对AI模型返回的响应数据进行脱敏处理
 */
@Slf4j
@Component
@Order(20) // 在请求脱敏过滤器之后执行
@ConditionalOnProperty(name = "jairouter.security.sanitization.response.enabled", havingValue = "true", matchIfMissing = true)
public class ResponseSanitizationFilter implements WebFilter {
    
    private final SanitizationService sanitizationService;
    private final SecurityAuditService auditService;
    private final SecurityProperties securityProperties;
    
    @Autowired
    public ResponseSanitizationFilter(SanitizationService sanitizationService,
                                    SecurityAuditService auditService,
                                    SecurityProperties securityProperties) {
        this.sanitizationService = sanitizationService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 跳过不需要脱敏的路径
        String path = request.getPath().value();
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }
        
        // 创建响应装饰器来拦截响应数据
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                // 检查响应是否已经提交
                if (getDelegate().isCommitted()) {
                    log.warn("响应已提交，跳过脱敏处理");
                    return super.writeWith(body);
                }
                
                // 检查是否需要处理响应内容
                String contentType = getResponseContentType();
                if (!shouldSanitizeContentType(contentType)) {
                    return super.writeWith(body);
                }
                
                // 处理响应体脱敏
                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(dataBuffer -> {
                            try {
                                // 读取原始响应内容
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                final byte[] originalBytes = bytes; // 创建final变量供lambda使用
                                String originalContent = new String(originalBytes, StandardCharsets.UTF_8);
                                
                                // 执行脱敏
                                return sanitizationService.sanitizeResponse(originalContent, contentType)
                                        .flatMap(sanitizedContent -> {
                                            // 记录脱敏操作
                                            recordSanitizationEvent(request, contentType, 
                                                                   !originalContent.equals(sanitizedContent));
                                            
                                            // 检查响应是否已经提交
                                            if (getDelegate().isCommitted()) {
                                                log.warn("响应已提交，无法写入脱敏后的内容");
                                                // 释放缓冲区并返回空
                                                DataBufferUtils.release(dataBuffer);
                                                return Mono.empty();
                                            }
                                            
                                            // 创建新的响应体
                                            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                            DataBuffer sanitizedBuffer = bufferFactory.wrap(
                                                    sanitizedContent.getBytes(StandardCharsets.UTF_8));
                                            
                                            return super.writeWith(Mono.just(sanitizedBuffer));
                                        })
                                        .onErrorResume(SanitizationException.class, ex -> {
                                            log.error("响应脱敏失败: {}", ex.getMessage(), ex);
                                            recordSanitizationEvent(request, contentType, false, ex.getMessage());
                                            
                                            // 根据配置决定是否返回原始内容还是错误
                                            if (securityProperties.getSanitization().getResponse().isFailOnError()) {
                                                return handleSanitizationFailure(ex);
                                            } else {
                                                log.warn("响应脱敏失败但配置为继续处理，返回原始内容: {}", ex.getMessage());
                                                // 检查响应是否已经提交
                                                if (getDelegate().isCommitted()) {
                                                    log.warn("响应已提交，无法写入原始内容");
                                                    DataBufferUtils.release(dataBuffer);
                                                    return Mono.empty();
                                                }
                                                // 重新创建原始内容的DataBuffer
                                                DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                                DataBuffer originalBuffer = bufferFactory.wrap(originalBytes);
                                                return super.writeWith(Mono.just(originalBuffer));
                                            }
                                        })
                                        .onErrorResume(Exception.class, ex -> {
                                            // 记录更详细的错误信息
                                            String errorMessage = "未知错误: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
                                            if (ex.getCause() != null) {
                                                errorMessage += ", 原因: " + (ex.getCause().getMessage() != null ? ex.getCause().getMessage() : ex.getCause().getClass().getName());
                                            }
                                            
                                            log.error("响应脱敏过程中发生未知错误: {}", errorMessage, ex);
                                            recordSanitizationEvent(request, contentType, false, errorMessage);
                                            
                                            // 发生未知错误时，根据配置决定处理方式
                                            if (securityProperties.getSanitization().getResponse().isFailOnError()) {
                                                return handleSanitizationFailure(
                                                        new SanitizationException("响应脱敏过程中发生未知错误", ex, 
                                                                SanitizationException.CONTENT_PROCESSING_FAILED));
                                            } else {
                                                // 检查响应是否已经提交
                                                if (getDelegate().isCommitted()) {
                                                    log.warn("响应已提交，无法写入原始内容");
                                                    DataBufferUtils.release(dataBuffer);
                                                    return Mono.empty();
                                                }
                                                // 返回原始内容
                                                DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                                DataBuffer originalBuffer = bufferFactory.wrap(originalBytes);
                                                return super.writeWith(Mono.just(originalBuffer));
                                            }
                                        });
                            } catch (Exception e) {
                                log.error("响应内容读取失败", e);
                                return Mono.error(new SanitizationException("响应内容读取失败", e, 
                                        SanitizationException.CONTENT_PROCESSING_FAILED));
                            } finally {
                                // 确保DataBuffer被释放
                                DataBufferUtils.release(dataBuffer);
                            }
                        })
                        .switchIfEmpty(Mono.defer(() -> super.writeWith(body)));
            }
            
            /**
             * 获取响应内容类型
             */
            private String getResponseContentType() {
                try {
                    MediaType mediaType = getHeaders().getContentType();
                    return mediaType != null ? mediaType.toString() : "application/octet-stream";
                } catch (Exception e) {
                    log.warn("获取响应内容类型失败: {}", e.getMessage());
                    return "application/octet-stream";
                }
            }
        };
        
        // 使用装饰后的响应继续处理链
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 检查是否为排除的路径
     */
    private boolean isExcludedPath(String path) {
        // 使用统一的排除路径配置
        return ExcludedPathsConfig.isDataMaskExcluded(path);
    }

    /**
     * 检查是否应该对该内容类型进行脱敏
     */
    private boolean shouldSanitizeContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        // 只处理JSON类型的内容
        return contentType.startsWith("application/json");
    }
    
    /**
     * 记录脱敏事件
     */
    private void recordSanitizationEvent(ServerHttpRequest request, String contentType, boolean sanitized) {
        recordSanitizationEvent(request, contentType, sanitized, null);
    }
    
    /**
     * 记录脱敏事件（带错误信息）
     */
    private void recordSanitizationEvent(ServerHttpRequest request, String contentType, 
                                       boolean sanitized, String errorMessage) {
        if (!securityProperties.getSanitization().getResponse().isLogSanitization()) {
            return;
        }
        
        try {
            SecurityAuditEvent.SecurityAuditEventBuilder eventBuilder = SecurityAuditEvent.builder()
                    .eventType("RESPONSE_SANITIZATION")
                    .userId("system") // 响应脱敏是系统行为
                    .clientIp(getClientIp(request))
                    .userAgent(getUserAgent(request))
                    .timestamp(LocalDateTime.now())
                    .resource(request.getPath().value())
                    .action("SANITIZE_RESPONSE")
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
                    unused -> log.debug("记录响应脱敏审计事件成功"),
                    error -> log.error("记录响应脱敏审计事件失败", error)
            );
        } catch (Exception e) {
            log.error("创建响应脱敏审计事件失败", e);
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
    private Mono<Void> handleSanitizationFailure(SanitizationException ex) {
        // 创建错误响应内容
        String errorResponse = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                ex.getErrorCode(), ex.getMessage(), LocalDateTime.now()
        );
        
        // 返回错误，让上层处理
        return Mono.error(ex);
    }
}

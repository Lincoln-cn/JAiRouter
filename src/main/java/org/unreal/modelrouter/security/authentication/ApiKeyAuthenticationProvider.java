package org.unreal.modelrouter.security.authentication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.exception.SecurityAuthenticationException;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;

/**
 * API Key认证提供者
 * 实现Spring Security的AuthenticationProvider接口
 * 处理API Key认证逻辑和事件发布
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {
    
    private final ApiKeyService apiKeyService;
    private final SecurityProperties securityProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityAuditService auditService;
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        log.debug("开始API Key认证处理");
        
        if (!supports(authentication.getClass())) {
            log.debug("不支持的认证类型: {}", authentication.getClass().getSimpleName());
            return null;
        }
        
        ApiKeyAuthentication apiKeyAuth = (ApiKeyAuthentication) authentication;
        String apiKey = (String) apiKeyAuth.getCredentials();
        
        try {
            // 验证API Key
            ApiKeyInfo apiKeyInfo = apiKeyService.validateApiKey(apiKey).block();
            
            if (apiKeyInfo == null) {
                log.debug("API Key验证失败: 无效的API Key");
                publishAuthenticationFailureEvent(apiKey, "无效的API Key");
                throw new SecurityAuthenticationException("INVALID_API_KEY", "无效的API Key");
            }
            
            // 创建已认证的Authentication对象
            ApiKeyAuthentication authenticatedAuth = new ApiKeyAuthentication(
                    apiKeyInfo.getKeyId(),
                    apiKey,
                    apiKeyInfo.getPermissions()
            );
            authenticatedAuth.setAuthenticated(true);
            authenticatedAuth.setDetails(apiKeyInfo);
            
            // 发布认证成功事件
            publishAuthenticationSuccessEvent(apiKeyInfo);
            
            log.debug("API Key认证成功: {}", apiKeyInfo.getKeyId());
            return authenticatedAuth;
            
        } catch (Exception e) {
            log.debug("API Key认证异常: {}", e.getMessage());
            
            // 如果是SecurityAuthenticationException，说明已经发布过事件了，不要重复发布
            if (!(e instanceof SecurityAuthenticationException)) {
                publishAuthenticationFailureEvent(apiKey, e.getMessage());
            }
            
            if (e instanceof AuthenticationException) {
                throw e;
            }
            
            throw new SecurityAuthenticationException(
                    "API_KEY_AUTH_ERROR", 
                    "API Key认证过程中发生错误: " + e.getMessage(),
                    e
            );
        }
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthentication.class.isAssignableFrom(authentication);
    }
    
    /**
     * 发布认证成功事件
     */
    private void publishAuthenticationSuccessEvent(ApiKeyInfo apiKeyInfo) {
        try {
            // 创建审计事件
            SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
                    .eventType("API_KEY_AUTH_SUCCESS")
                    .userId(apiKeyInfo.getKeyId())
                    .resource("API_KEY_AUTH")
                    .action("AUTHENTICATE")
                    .success(true)
                    .build();
            
            // 记录审计日志
            auditService.recordEvent(auditEvent).subscribe(
                    result -> log.debug("认证成功事件已记录"),
                    error -> log.warn("记录认证成功事件失败: {}", error.getMessage())
            );
            
            // 发布Spring事件
            eventPublisher.publishEvent(new ApiKeyAuthenticationSuccessEvent(this, apiKeyInfo));
            
        } catch (Exception e) {
            log.warn("发布认证成功事件失败: {}", e.getMessage());
        }
    }
    
    /**
     * 发布认证失败事件
     */
    private void publishAuthenticationFailureEvent(String apiKey, String reason) {
        try {
            // 创建审计事件（不记录完整的API Key）
            SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
                    .eventType("API_KEY_AUTH_FAILURE")
                    .userId("UNKNOWN")
                    .resource("API_KEY_AUTH")
                    .action("AUTHENTICATE")
                    .success(false)
                    .failureReason(reason)
                    .build();
            
            // 记录审计日志
            auditService.recordEvent(auditEvent).subscribe(
                    result -> log.debug("认证失败事件已记录"),
                    error -> log.warn("记录认证失败事件失败: {}", error.getMessage())
            );
            
            // 发布Spring事件
            eventPublisher.publishEvent(new ApiKeyAuthenticationFailureEvent(this, apiKey, reason));
            
        } catch (Exception e) {
            log.warn("发布认证失败事件失败: {}", e.getMessage());
        }
    }
    
    /**
     * API Key认证成功事件
     */
    public static class ApiKeyAuthenticationSuccessEvent {
        private final Object source;
        private final ApiKeyInfo apiKeyInfo;
        
        public ApiKeyAuthenticationSuccessEvent(Object source, ApiKeyInfo apiKeyInfo) {
            this.source = source;
            this.apiKeyInfo = apiKeyInfo;
        }
        
        public Object getSource() {
            return source;
        }
        
        public ApiKeyInfo getApiKeyInfo() {
            return apiKeyInfo;
        }
    }
    
    /**
     * API Key认证失败事件
     */
    public static class ApiKeyAuthenticationFailureEvent {
        private final Object source;
        private final String apiKey;
        private final String reason;
        
        public ApiKeyAuthenticationFailureEvent(Object source, String apiKey, String reason) {
            this.source = source;
            this.apiKey = apiKey;
            this.reason = reason;
        }
        
        public Object getSource() {
            return source;
        }
        
        public String getApiKey() {
            return apiKey;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
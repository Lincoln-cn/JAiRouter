package org.unreal.modelrouter.security.authentication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.authentication.ApiKeyAuthenticationProvider.ApiKeyAuthenticationFailureEvent;
import org.unreal.modelrouter.security.authentication.ApiKeyAuthenticationProvider.ApiKeyAuthenticationSuccessEvent;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;

/**
 * 认证事件监听器
 * 处理认证成功和失败事件，执行相应的业务逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventListener {
    
    private final SecurityProperties securityProperties;
    
    /**
     * 处理API Key认证成功事件
     */
    @EventListener
    public void handleApiKeyAuthenticationSuccess(ApiKeyAuthenticationSuccessEvent event) {
        log.info("API Key认证成功: keyId={}, description={}", 
                event.apiKeyInfo().getKeyId(),
                event.apiKeyInfo().getDescription());
        
        // 可以在这里添加认证成功后的业务逻辑
        // 例如：更新使用统计、发送通知等
        updateApiKeyUsageStatistics(event.apiKeyInfo());
    }
    
    /**
     * 处理API Key认证失败事件
     */
    @EventListener
    public void handleApiKeyAuthenticationFailure(ApiKeyAuthenticationFailureEvent event) {
        log.warn("API Key认证失败: reason={}", event.reason());
        
        // 可以在这里添加认证失败后的业务逻辑
        // 例如：记录失败次数、触发安全告警等
        handleAuthenticationFailure(event.apiKey(), event.reason());
    }
    
    /**
     * 更新API Key使用统计
     */
    private void updateApiKeyUsageStatistics(org.unreal.modelrouter.security.model.ApiKeyInfo apiKeyInfo) {
        try {
            // 这里可以实现使用统计的更新逻辑
            // 例如：增加请求计数、更新最后使用时间等
            log.debug("更新API Key使用统计: {}", apiKeyInfo.getKeyId());
            
            // 示例：如果启用了审计，可以记录使用情况
            if (securityProperties.getAudit().isEnabled()) {
                log.debug("记录API Key使用情况到审计日志");
            }
            
        } catch (Exception e) {
            log.error("更新API Key使用统计失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理认证失败
     */
    private void handleAuthenticationFailure(String apiKey, String reason) {
        try {
            // 这里可以实现认证失败的处理逻辑
            // 例如：记录失败次数、检查是否需要触发告警等
            log.debug("处理认证失败: reason={}", reason);
            
            // 示例：如果启用了告警，检查是否达到告警阈值
            if (securityProperties.getAudit().isAlertEnabled()) {
                checkAuthenticationFailureThreshold(reason);
            }
            
        } catch (Exception e) {
            log.error("处理认证失败事件异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查认证失败阈值
     */
    private void checkAuthenticationFailureThreshold(String reason) {
        // 这里可以实现阈值检查逻辑
        // 例如：统计最近一分钟的失败次数，如果超过阈值则发送告警
        int threshold = securityProperties.getAudit().getAlertThresholds().getAuthFailuresPerMinute();
        log.debug("检查认证失败阈值: threshold={}, reason={}", threshold, reason);
        
        // 实际实现中，这里应该查询最近的失败记录并进行统计
        // 如果超过阈值，则触发告警机制
    }
}
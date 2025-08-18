package org.unreal.modelrouter.security.config;

import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SanitizationRule;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 安全配置管理服务接口
 * 提供安全配置的动态更新和管理功能
 */
public interface SecurityConfigurationService {
    
    /**
     * 动态更新API Key配置
     * @param apiKeys API Key列表
     * @return 更新操作结果
     */
    Mono<Void> updateApiKeys(List<ApiKeyInfo> apiKeys);
    
    /**
     * 动态更新脱敏规则配置
     * @param rules 脱敏规则列表
     * @return 更新操作结果
     */
    Mono<Void> updateSanitizationRules(List<SanitizationRule> rules);
    
    /**
     * 更新JWT配置
     * @param jwtConfig JWT配置
     * @return 更新操作结果
     */
    Mono<Void> updateJwtConfig(SecurityProperties.JwtConfig jwtConfig);
    
    /**
     * 获取当前安全配置
     * @return 当前安全配置
     */
    Mono<SecurityProperties> getCurrentConfiguration();
    
    /**
     * 验证配置的有效性
     * @param properties 安全配置
     * @return 验证结果
     */
    Mono<Boolean> validateConfiguration(SecurityProperties properties);
    
    /**
     * 备份当前配置
     * @return 备份操作结果
     */
    Mono<String> backupConfiguration();
    
    /**
     * 从备份恢复配置
     * @param backupId 备份ID
     * @return 恢复操作结果
     */
    Mono<Void> restoreConfiguration(String backupId);
    
    /**
     * 重新加载配置
     * @return 重新加载操作结果
     */
    Mono<Void> reloadConfiguration();
    
    /**
     * 获取配置变更历史
     * @param limit 限制数量
     * @return 配置变更历史
     */
    Mono<List<ConfigurationChangeEvent>> getConfigurationHistory(int limit);
    
    /**
     * 配置变更事件
     */
    class ConfigurationChangeEvent {
        private String changeId;
        private String changeType;
        private String userId;
        private java.time.LocalDateTime timestamp;
        private String description;
        private Object oldValue;
        private Object newValue;
        
        // getters and setters
        public String getChangeId() { return changeId; }
        public void setChangeId(String changeId) { this.changeId = changeId; }
        
        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Object getOldValue() { return oldValue; }
        public void setOldValue(Object oldValue) { this.oldValue = oldValue; }
        
        public Object getNewValue() { return newValue; }
        public void setNewValue(Object newValue) { this.newValue = newValue; }
    }
}
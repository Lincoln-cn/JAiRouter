package org.unreal.modelrouter.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 配置版本管理接口
 * 提供清晰的配置生命周期管理
 */
public interface ConfigVersionManager {
    
    /**
     * 初始化配置（仅在首次启动或配置不存在时调用）
     * @param key 配置键
     * @param config 初始配置内容
     * @param description 初始化描述
     * @return 初始版本号（通常为1）
     */
    int initializeConfig(String key, Map<String, Object> config, String description);
    
    /**
     * 更新配置（运行时调用，自动创建新版本）
     * @param key 配置键
     * @param config 新配置内容
     * @param description 变更描述
     * @param userId 操作用户
     * @return 新版本号，如果配置无变化则返回当前版本号
     */
    int updateConfig(String key, Map<String, Object> config, String description, String userId);
    
    /**
     * 获取最新配置
     * @param key 配置键
     * @return 最新版本的配置内容
     */
    Map<String, Object> getLatestConfig(String key);
    
    /**
     * 获取指定版本配置
     * @param key 配置键
     * @param version 版本号
     * @return 指定版本的配置内容
     */
    Map<String, Object> getConfigByVersion(String key, int version);
    
    /**
     * 回滚到指定版本
     * @param key 配置键
     * @param targetVersion 目标版本号
     * @param description 回滚描述
     * @param userId 操作用户
     * @return 新版本号
     */
    int rollbackToVersion(String key, int targetVersion, String description, String userId);
    
    /**
     * 获取配置元数据
     * @param key 配置键
     * @return 配置元数据
     */
    ConfigMetadata getConfigMetadata(String key);
    
    /**
     * 获取配置变更历史
     * @param key 配置键
     * @param limit 限制数量
     * @return 版本历史列表
     */
    List<VersionInfo> getVersionHistory(String key, int limit);
    
    /**
     * 检查配置是否已初始化
     * @param key 配置键
     * @return 是否已初始化
     */
    boolean isConfigInitialized(String key);
    
    /**
     * 清理旧版本（保留指定数量的最新版本）
     * @param key 配置键
     * @param keepVersions 保留的版本数量
     * @return 清理的版本数量
     */
    int cleanupOldVersions(String key, int keepVersions);
    
    /**
     * 配置元数据
     */
    class ConfigMetadata {
        private String configKey;
        private int currentVersion;
        private int initialVersion;
        private LocalDateTime createdAt;
        private LocalDateTime lastModified;
        private String lastModifiedBy;
        private int totalVersions;
        
        // Getters and setters
        public String getConfigKey() { return configKey; }
        public void setConfigKey(String configKey) { this.configKey = configKey; }
        
        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
        
        public int getInitialVersion() { return initialVersion; }
        public void setInitialVersion(int initialVersion) { this.initialVersion = initialVersion; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public LocalDateTime getLastModified() { return lastModified; }
        public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }
        
        public String getLastModifiedBy() { return lastModifiedBy; }
        public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
        
        public int getTotalVersions() { return totalVersions; }
        public void setTotalVersions(int totalVersions) { this.totalVersions = totalVersions; }
    }
    
    /**
     * 版本信息
     */
    class VersionInfo {
        private int version;
        private LocalDateTime createdAt;
        private String createdBy;
        private String description;
        private ChangeType changeType;
        private Map<String, Object> configSnapshot; // 可选，用于快速预览
        
        public enum ChangeType {
            INITIAL,    // 初始化
            UPDATE,     // 更新
            ROLLBACK    // 回滚
        }
        
        // Getters and setters
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public ChangeType getChangeType() { return changeType; }
        public void setChangeType(ChangeType changeType) { this.changeType = changeType; }
        
        public Map<String, Object> getConfigSnapshot() { return configSnapshot; }
        public void setConfigSnapshot(Map<String, Object> configSnapshot) { this.configSnapshot = configSnapshot; }
    }
}
package org.unreal.modelrouter.config.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 配置变更事件
 * 当配置发生变化时发布，用于解耦配置管理和服务注册表
 */
public class ConfigurationChangedEvent extends ApplicationEvent {

    private final String configKey;
    private final Map<String, Object> newConfig;
    private final Integer version;
    private final ChangeType changeType;
    private final String changedBy;
    private final LocalDateTime changeTime;

    public ConfigurationChangedEvent(Object source, String configKey, Map<String, Object> newConfig,
                                     Integer version, ChangeType changeType, String changedBy) {
        super(source);
        this.configKey = configKey;
        this.newConfig = newConfig;
        this.version = version;
        this.changeType = changeType;
        this.changedBy = changedBy;
        this.changeTime = LocalDateTime.now();
    }

    public String getConfigKey() {
        return configKey;
    }

    public Map<String, Object> getNewConfig() {
        return newConfig;
    }

    public Integer getVersion() {
        return version;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        CREATE,      // 创建新配置
        UPDATE,      // 更新配置
        DELETE,      // 删除配置
        ROLLBACK,    // 回滚到指定版本
        APPLY_VERSION // 应用指定版本
    }
}

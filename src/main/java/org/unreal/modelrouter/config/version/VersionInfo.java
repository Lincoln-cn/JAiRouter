package org.unreal.modelrouter.config.version;

import java.time.LocalDateTime;
import java.util.Map;

// 版本信息内部类
public class VersionInfo {

    private int version;
    private LocalDateTime createdAt;
    private String createdBy;
    private String description;
    private ChangeType changeType;
    private Map<String, Object> configSnapshot; // 可选，用于快速预览

    // Getters and setters
    public int getVersion() {
        return version;
    }

    public void setVersion(final int version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(final ChangeType changeType) {
        this.changeType = changeType;
    }

    public Map<String, Object> getConfigSnapshot() {
        return configSnapshot;
    }

    public void setConfigSnapshot(final Map<String, Object> configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    public enum ChangeType {
        INITIAL, // 初始化
        UPDATE,     // 更新
        // ROLLBACK 已废弃
    }
}

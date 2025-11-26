package org.unreal.modelrouter.entity;

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

    public void setVersion(int version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public Map<String, Object> getConfigSnapshot() {
        return configSnapshot;
    }

    public void setConfigSnapshot(Map<String, Object> configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    public enum ChangeType {
        INITIAL, // 初始化
        UPDATE,     // 更新
        // ROLLBACK 已废弃
    }
}

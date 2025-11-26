package org.unreal.modelrouter.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// 配置元数据内部类
public class ConfigMetadata {

    private String configKey;
    private int currentVersion;
    private int initialVersion;
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;
    private String lastModifiedBy;
    private int totalVersions;
    private Set<Integer> existingVersions; // 存在的版本号列表

    // Getters and setters
    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int getInitialVersion() {
        return initialVersion;
    }

    public void setInitialVersion(int initialVersion) {
        this.initialVersion = initialVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public int getTotalVersions() {
        return totalVersions;
    }

    public void setTotalVersions(int totalVersions) {
        this.totalVersions = totalVersions;
    }

    public Set<Integer> getExistingVersions() {
        return existingVersions != null ? existingVersions : new HashSet<>();
    }

    public void setExistingVersions(Set<Integer> existingVersions) {
        this.existingVersions = existingVersions;
    }

    /**
     * 添加版本到存在列表
     */
    public void addVersion(int version) {
        if (existingVersions == null) {
            existingVersions = new HashSet<>();
        }
        existingVersions.add(version);
    }

    /**
     * 从存在列表中移除版本
     */
    public void removeVersion(int version) {
        if (existingVersions != null) {
            existingVersions.remove(version);
        }
    }

    /**
     * 检查版本是否存在
     */
    public boolean hasVersion(int version) {
        return existingVersions != null && existingVersions.contains(version);
    }


    public void clean() {
        configKey = "";
        currentVersion = -99;
        initialVersion = -99;
        createdAt = LocalDateTime.now();
        lastModified = LocalDateTime.now();
        lastModifiedBy = "Merge";
        totalVersions = 0;
        existingVersions = new HashSet<>();
    }
}

package org.unreal.modelrouter.dto;

/**
 * JWT账户配置状态对象
 */
public class JwtAccountConfigStatus {
    private boolean hasPersistedConfig;
    private int currentVersion;
    private int totalVersions;

    // Getters and setters
    public boolean isHasPersistedConfig() {
        return hasPersistedConfig;
    }

    public void setHasPersistedConfig(boolean hasPersistedConfig) {
        this.hasPersistedConfig = hasPersistedConfig;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int getTotalVersions() {
        return totalVersions;
    }

    public void setTotalVersions(int totalVersions) {
        this.totalVersions = totalVersions;
    }
}

package org.unreal.modelrouter.common.dto;

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

    public void setHasPersistedConfig(final boolean hasPersistedConfig) {
        this.hasPersistedConfig = hasPersistedConfig;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(final int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int getTotalVersions() {
        return totalVersions;
    }

    public void setTotalVersions(final int totalVersions) {
        this.totalVersions = totalVersions;
    }
}

package org.unreal.modelrouter.store.persistence.integration;

import java.util.Map;

/**
 * 限流器持久化集成状态 DTO
 *
 * v2.4.6: 强类型状态对象，替代 Map 返回
 *
 * @author JAiRouter Team
 * @since 2.4.6
 */
public class PersistenceIntegrationStatus {

    private boolean initialized;
    private long syncIntervalMs;
    private long recoveryTimeoutMs;
    private int registeredCount;
    private int pendingSyncCount;
    private Map<String, Boolean> lastRecoveryResults;

    public PersistenceIntegrationStatus() {
    }

    public PersistenceIntegrationStatus(final boolean initialized,final long syncIntervalMs,final long recoveryTimeoutMs) {
        this.initialized = initialized;
        this.syncIntervalMs = syncIntervalMs;
        this.recoveryTimeoutMs = recoveryTimeoutMs;
    }

    /* ===================== Getter/Setter ===================== */

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(final boolean initialized) {
        this.initialized = initialized;
    }

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    public void setSyncIntervalMs(final long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }

    public long getRecoveryTimeoutMs() {
        return recoveryTimeoutMs;
    }

    public void setRecoveryTimeoutMs(final long recoveryTimeoutMs) {
        this.recoveryTimeoutMs = recoveryTimeoutMs;
    }

    public int getRegisteredCount() {
        return registeredCount;
    }

    public void setRegisteredCount(final int registeredCount) {
        this.registeredCount = registeredCount;
    }

    public int getPendingSyncCount() {
        return pendingSyncCount;
    }

    public void setPendingSyncCount(final int pendingSyncCount) {
        this.pendingSyncCount = pendingSyncCount;
    }

    public Map<String, Boolean> getLastRecoveryResults() {
        return lastRecoveryResults;
    }

    public void setLastRecoveryResults(final Map<String, Boolean> lastRecoveryResults) {
        this.lastRecoveryResults = lastRecoveryResults;
    }

    /* ===================== Builder ===================== */

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PersistenceIntegrationStatus status = new PersistenceIntegrationStatus();

        public Builder initialized(boolean initialized) {
            status.setInitialized(initialized);
            return this;
        }

        public Builder syncIntervalMs(long syncIntervalMs) {
            status.setSyncIntervalMs(syncIntervalMs);
            return this;
        }

        public Builder recoveryTimeoutMs(long recoveryTimeoutMs) {
            status.setRecoveryTimeoutMs(recoveryTimeoutMs);
            return this;
        }

        public Builder registeredCount(int registeredCount) {
            status.setRegisteredCount(registeredCount);
            return this;
        }

        public Builder pendingSyncCount(int pendingSyncCount) {
            status.setPendingSyncCount(pendingSyncCount);
            return this;
        }

        public Builder lastRecoveryResults(final Map<String, Boolean> results) {
            status.setLastRecoveryResults(results);
            return this;
        }

        public PersistenceIntegrationStatus build() {
            return status;
        }
    }

    /* ===================== toString ===================== */

    @Override
    public String toString() {
        return "PersistenceIntegrationStatus{" +
                "initialized=" + initialized +
                ", syncIntervalMs=" + syncIntervalMs +
                ", recoveryTimeoutMs=" + recoveryTimeoutMs +
                ", registeredCount=" + registeredCount +
                ", pendingSyncCount=" + pendingSyncCount +
                '}';
    }
}
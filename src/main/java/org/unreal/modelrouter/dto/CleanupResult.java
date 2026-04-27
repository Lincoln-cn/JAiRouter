package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

/**
 * 清理操作结果类
 */
public class CleanupResult {
    
    private long cleanedTokens;
    private long cleanedBlacklistEntries;
    private LocalDateTime cleanupStartTime;
    private LocalDateTime cleanupEndTime;
    private long durationMillis;
    private boolean success;
    private String errorMessage;

    public CleanupResult() {
    }

    public CleanupResult(final long cleanedTokens, final long cleanedBlacklistEntries, 
                        final LocalDateTime cleanupStartTime, final LocalDateTime cleanupEndTime, 
                        final long durationMillis, final boolean success,final String errorMessage) {
        this.cleanedTokens = cleanedTokens;
        this.cleanedBlacklistEntries = cleanedBlacklistEntries;
        this.cleanupStartTime = cleanupStartTime;
        this.cleanupEndTime = cleanupEndTime;
        this.durationMillis = durationMillis;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    // Getters and Setters
    public long getCleanedTokens() {
        return cleanedTokens;
    }

    public void setCleanedTokens(final long cleanedTokens) {
        this.cleanedTokens = cleanedTokens;
    }

    public long getCleanedBlacklistEntries() {
        return cleanedBlacklistEntries;
    }

    public void setCleanedBlacklistEntries(final long cleanedBlacklistEntries) {
        this.cleanedBlacklistEntries = cleanedBlacklistEntries;
    }

    public LocalDateTime getCleanupStartTime() {
        return cleanupStartTime;
    }

    public void setCleanupStartTime(final LocalDateTime cleanupStartTime) {
        this.cleanupStartTime = cleanupStartTime;
    }

    public LocalDateTime getCleanupEndTime() {
        return cleanupEndTime;
    }

    public void setCleanupEndTime(final LocalDateTime cleanupEndTime) {
        this.cleanupEndTime = cleanupEndTime;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(final long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "CleanupResult{" +
                "cleanedTokens=" + cleanedTokens +
                ", cleanedBlacklistEntries=" + cleanedBlacklistEntries +
                ", cleanupStartTime=" + cleanupStartTime +
                ", cleanupEndTime=" + cleanupEndTime +
                ", durationMillis=" + durationMillis +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
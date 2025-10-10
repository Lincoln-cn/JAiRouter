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

    public CleanupResult(long cleanedTokens, long cleanedBlacklistEntries, 
                        LocalDateTime cleanupStartTime, LocalDateTime cleanupEndTime, 
                        long durationMillis, boolean success, String errorMessage) {
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

    public void setCleanedTokens(long cleanedTokens) {
        this.cleanedTokens = cleanedTokens;
    }

    public long getCleanedBlacklistEntries() {
        return cleanedBlacklistEntries;
    }

    public void setCleanedBlacklistEntries(long cleanedBlacklistEntries) {
        this.cleanedBlacklistEntries = cleanedBlacklistEntries;
    }

    public LocalDateTime getCleanupStartTime() {
        return cleanupStartTime;
    }

    public void setCleanupStartTime(LocalDateTime cleanupStartTime) {
        this.cleanupStartTime = cleanupStartTime;
    }

    public LocalDateTime getCleanupEndTime() {
        return cleanupEndTime;
    }

    public void setCleanupEndTime(LocalDateTime cleanupEndTime) {
        this.cleanupEndTime = cleanupEndTime;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
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
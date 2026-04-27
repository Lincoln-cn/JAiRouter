package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

/**
 * 清理统计信息类
 */
public class CleanupStats {
    
    private LocalDateTime lastCleanupTime;
    private long totalCleanupRuns;
    private long totalTokensCleaned;
    private long totalBlacklistEntriesCleaned;
    private double averageCleanupDurationSeconds;
    private LocalDateTime nextScheduledCleanup;
    private boolean cleanupEnabled;

    public CleanupStats() {
    }

    public CleanupStats(final LocalDateTime lastCleanupTime,final long totalCleanupRuns, 
                       final long totalTokensCleaned,final long totalBlacklistEntriesCleaned,
                       final double averageCleanupDurationSeconds,final LocalDateTime nextScheduledCleanup,
                       final boolean cleanupEnabled) {
        this.lastCleanupTime = lastCleanupTime;
        this.totalCleanupRuns = totalCleanupRuns;
        this.totalTokensCleaned = totalTokensCleaned;
        this.totalBlacklistEntriesCleaned = totalBlacklistEntriesCleaned;
        this.averageCleanupDurationSeconds = averageCleanupDurationSeconds;
        this.nextScheduledCleanup = nextScheduledCleanup;
        this.cleanupEnabled = cleanupEnabled;
    }

    // Getters and Setters
    public LocalDateTime getLastCleanupTime() {
        return lastCleanupTime;
    }

    public void setLastCleanupTime(final LocalDateTime lastCleanupTime) {
        this.lastCleanupTime = lastCleanupTime;
    }

    public long getTotalCleanupRuns() {
        return totalCleanupRuns;
    }

    public void setTotalCleanupRuns(final long totalCleanupRuns) {
        this.totalCleanupRuns = totalCleanupRuns;
    }

    public long getTotalTokensCleaned() {
        return totalTokensCleaned;
    }

    public void setTotalTokensCleaned(final long totalTokensCleaned) {
        this.totalTokensCleaned = totalTokensCleaned;
    }

    public long getTotalBlacklistEntriesCleaned() {
        return totalBlacklistEntriesCleaned;
    }

    public void setTotalBlacklistEntriesCleaned(final long totalBlacklistEntriesCleaned) {
        this.totalBlacklistEntriesCleaned = totalBlacklistEntriesCleaned;
    }

    public double getAverageCleanupDurationSeconds() {
        return averageCleanupDurationSeconds;
    }

    public void setAverageCleanupDurationSeconds(final double averageCleanupDurationSeconds) {
        this.averageCleanupDurationSeconds = averageCleanupDurationSeconds;
    }

    public LocalDateTime getNextScheduledCleanup() {
        return nextScheduledCleanup;
    }

    public void setNextScheduledCleanup(final LocalDateTime nextScheduledCleanup) {
        this.nextScheduledCleanup = nextScheduledCleanup;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(final boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    @Override
    public String toString() {
        return "CleanupStats{" +
                "lastCleanupTime=" + lastCleanupTime +
                ", totalCleanupRuns=" + totalCleanupRuns +
                ", totalTokensCleaned=" + totalTokensCleaned +
                ", totalBlacklistEntriesCleaned=" + totalBlacklistEntriesCleaned +
                ", averageCleanupDurationSeconds=" + averageCleanupDurationSeconds +
                ", nextScheduledCleanup=" + nextScheduledCleanup +
                ", cleanupEnabled=" + cleanupEnabled +
                '}';
    }
}
package org.unreal.modelrouter.security.service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JWT令牌清理服务接口
 * 提供定时清理过期令牌和黑名单条目的功能
 */
public interface JwtCleanupService {
    
    /**
     * 启动定时清理任务
     */
    void scheduleCleanup();
    
    /**
     * 清理过期的令牌
     * @return 清理结果
     */
    Mono<CleanupResult> cleanupExpiredTokens();
    
    /**
     * 清理过期的黑名单条目
     * @return 清理结果
     */
    Mono<CleanupResult> cleanupExpiredBlacklistEntries();
    
    /**
     * 获取清理统计信息
     * @return 清理统计信息
     */
    Mono<CleanupStats> getCleanupStats();
    
    /**
     * 手动触发完整清理
     * @return 清理结果
     */
    Mono<CleanupResult> performFullCleanup();
    
    /**
     * 清理结果
     */
    class CleanupResult {
        private long removedTokens;
        private long removedBlacklistEntries;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long durationMs;
        private boolean success;
        private String errorMessage;
        private Map<String, Object> details;
        
        public CleanupResult() {
        }
        
        public CleanupResult(final long removedTokens, final long removedBlacklistEntries, 
                           final LocalDateTime startTime, final LocalDateTime endTime,final boolean success) {
            this.removedTokens = removedTokens;
            this.removedBlacklistEntries = removedBlacklistEntries;
            this.startTime = startTime;
            this.endTime = endTime;
            this.success = success;
            if (startTime != null && endTime != null) {
                this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            }
        }
        
        // Getters and Setters
        public long getRemovedTokens() { return removedTokens; }
        public void setRemovedTokens(final long removedTokens) { this.removedTokens = removedTokens; }
        
        public long getRemovedBlacklistEntries() { return removedBlacklistEntries; }
        public void setRemovedBlacklistEntries(final long removedBlacklistEntries) { this.removedBlacklistEntries = removedBlacklistEntries; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(final LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(final LocalDateTime endTime) { this.endTime = endTime; }
        
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(final long durationMs) { this.durationMs = durationMs; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(final boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(final String errorMessage) { this.errorMessage = errorMessage; }
        
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(final Map<String, Object> details) { this.details = details; }
        
        public long getTotalRemoved() {
            return removedTokens + removedBlacklistEntries;
        }
    }
    
    /**
     * 清理统计信息
     */
    class CleanupStats {
        private LocalDateTime lastCleanupTime;
        private long totalCleanupsPerformed;
        private long totalTokensRemoved;
        private long totalBlacklistEntriesRemoved;
        private double averageCleanupDurationMs;
        private long failedCleanups;
        private LocalDateTime nextScheduledCleanup;
        private boolean cleanupEnabled;
        private String cleanupSchedule;
        private Map<String, Object> performanceMetrics;
        
        // Getters and Setters
        public LocalDateTime getLastCleanupTime() { return lastCleanupTime; }
        public void setLastCleanupTime(final LocalDateTime lastCleanupTime) { this.lastCleanupTime = lastCleanupTime; }
        
        public long getTotalCleanupsPerformed() { return totalCleanupsPerformed; }
        public void setTotalCleanupsPerformed(final long totalCleanupsPerformed) { this.totalCleanupsPerformed = totalCleanupsPerformed; }
        
        public long getTotalTokensRemoved() { return totalTokensRemoved; }
        public void setTotalTokensRemoved(final long totalTokensRemoved) { this.totalTokensRemoved = totalTokensRemoved; }
        
        public long getTotalBlacklistEntriesRemoved() { return totalBlacklistEntriesRemoved; }
        public void setTotalBlacklistEntriesRemoved(final long totalBlacklistEntriesRemoved) { this.totalBlacklistEntriesRemoved = totalBlacklistEntriesRemoved; }
        
        public double getAverageCleanupDurationMs() { return averageCleanupDurationMs; }
        public void setAverageCleanupDurationMs(final double averageCleanupDurationMs) { this.averageCleanupDurationMs = averageCleanupDurationMs; }
        
        public long getFailedCleanups() { return failedCleanups; }
        public void setFailedCleanups(final long failedCleanups) { this.failedCleanups = failedCleanups; }
        
        public LocalDateTime getNextScheduledCleanup() { return nextScheduledCleanup; }
        public void setNextScheduledCleanup(final LocalDateTime nextScheduledCleanup) { this.nextScheduledCleanup = nextScheduledCleanup; }
        
        public boolean isCleanupEnabled() { return cleanupEnabled; }
        public void setCleanupEnabled(final boolean cleanupEnabled) { this.cleanupEnabled = cleanupEnabled; }
        
        public String getCleanupSchedule() { return cleanupSchedule; }
        public void setCleanupSchedule(final String cleanupSchedule) { this.cleanupSchedule = cleanupSchedule; }
        
        public Map<String, Object> getPerformanceMetrics() { return performanceMetrics; }
        public void setPerformanceMetrics(final Map<String, Object> performanceMetrics) { this.performanceMetrics = performanceMetrics; }
    }
}
package org.unreal.modelrouter.auth.security.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.service.JwtBlacklistService;
import org.unreal.modelrouter.auth.security.service.JwtCleanupService;
import org.unreal.modelrouter.auth.security.service.JwtPersistenceService;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT令牌清理服务实现
 * 基于Spring Scheduler实现定时清理过期令牌和黑名单条目
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class JwtCleanupServiceImpl implements JwtCleanupService {
    
    private final JwtPersistenceService jwtPersistenceService;
    private final JwtBlacklistService jwtBlacklistService;
    private final StoreManager storeManager;
    private final MeterRegistry meterRegistry;
    
    // 配置参数
    @Value("${jairouter.security.jwt.persistence.cleanup.retention-days:30}")
    private int retentionDays;
    
    @Value("${jairouter.security.jwt.persistence.cleanup.batch-size:1000}")
    private int batchSize;
    
    @Value("${jairouter.security.jwt.persistence.cleanup.schedule:0 0 2 * * ?}")
    private String cleanupSchedule;
    
    // 统计信息存储键
    private static final String CLEANUP_STATS_KEY = "jwt_cleanup_stats";
    
    // 性能监控指标
    private Timer cleanupTimer;
    private Counter tokenCleanupCounter;
    private Counter blacklistCleanupCounter;
    private Counter cleanupFailureCounter;
    
    // 内存统计
    private final AtomicLong totalCleanupsPerformed = new AtomicLong(0);
    private final AtomicLong totalTokensRemoved = new AtomicLong(0);
    private final AtomicLong totalBlacklistEntriesRemoved = new AtomicLong(0);
    private final AtomicLong failedCleanups = new AtomicLong(0);
    private volatile LocalDateTime lastCleanupTime;
    
    @PostConstruct
    public void init() {
        // 初始化性能监控指标
        this.cleanupTimer = Timer.builder("jwt.cleanup.duration")
            .description("JWT cleanup operation duration")
            .register(meterRegistry);
            
        this.tokenCleanupCounter = Counter.builder("jwt.cleanup.tokens.removed")
            .description("Number of tokens removed during cleanup")
            .register(meterRegistry);
            
        this.blacklistCleanupCounter = Counter.builder("jwt.cleanup.blacklist.removed")
            .description("Number of blacklist entries removed during cleanup")
            .register(meterRegistry);
            
        this.cleanupFailureCounter = Counter.builder("jwt.cleanup.failures")
            .description("Number of failed cleanup operations")
            .register(meterRegistry);
        
        // 加载历史统计信息
        loadCleanupStats();
        
        log.info("JWT cleanup service initialized with schedule: {}, retention days: {}, batch size: {}", 
                cleanupSchedule, retentionDays, batchSize);
    }
    
    @Override
    public void scheduleCleanup() {
        log.info("JWT cleanup service scheduling is handled by @Scheduled annotation");
    }
    
    /**
     * 定时清理任务 - 每天凌晨2点执行
     */
    @Scheduled(cron = "${jairouter.security.jwt.persistence.cleanup.schedule:0 0 2 * * ?}")
    public void performScheduledCleanup() {
        LocalDateTime scheduledTime = LocalDateTime.now();
        log.info("Starting scheduled JWT cleanup task at {} (schedule: {})", scheduledTime, cleanupSchedule);
        
        // 记录调度信息
        log.info("Cleanup configuration: retention={}days, batchSize={}, totalCleanupsPerformed={}", 
                retentionDays, batchSize, totalCleanupsPerformed.get());
        
        performFullCleanup()
            .doOnSuccess(result -> {
                if (result.isSuccess()) {
                    log.info("Scheduled cleanup completed successfully at {}. " +
                            "Removed {} tokens and {} blacklist entries in {}ms. " +
                            "Total cleanups performed: {}, Total items removed: {}",
                            LocalDateTime.now(),
                            result.getRemovedTokens(), result.getRemovedBlacklistEntries(), result.getDurationMs(),
                            totalCleanupsPerformed.get(), totalTokensRemoved.get() + totalBlacklistEntriesRemoved.get());
                    
                    // 记录成功率统计
                    long totalCleanups = totalCleanupsPerformed.get();
                    long failedCount = failedCleanups.get();
                    double successRate = totalCleanups > 0 ? (double) (totalCleanups - failedCount) / totalCleanups * 100 : 100.0;
                    log.info("Cleanup success rate: {:.1f}% ({} successful out of {} total)", 
                            successRate, totalCleanups - failedCount, totalCleanups);
                    
                } else {
                    log.warn("Scheduled cleanup completed with errors at {}. " +
                            "Removed {} tokens and {} blacklist entries in {}ms. Error: {}",
                            LocalDateTime.now(),
                            result.getRemovedTokens(), result.getRemovedBlacklistEntries(), result.getDurationMs(),
                            result.getErrorMessage());
                }
                
                // 记录详细的清理结果
                if (result.getDetails() != null && !result.getDetails().isEmpty()) {
                    log.debug("Cleanup details: {}", result.getDetails());
                    
                    // 记录性能指标
                    Map<String, Object> details = result.getDetails();
                    if (details.containsKey("tokenCleanupDuration") && details.containsKey("blacklistCleanupDuration")) {
                        long tokenDuration = ((Number) details.get("tokenCleanupDuration")).longValue();
                        long blacklistDuration = ((Number) details.get("blacklistCleanupDuration")).longValue();
                        log.info("Performance breakdown: Token cleanup={}ms, Blacklist cleanup={}ms",
                                tokenDuration, blacklistDuration);
                    }
                }
                
                // 计算下次清理时间（简化实现）
                LocalDateTime nextCleanup = scheduledTime.plusDays(1).withHour(2).withMinute(0).withSecond(0);
                log.info("Next scheduled cleanup: {}", nextCleanup);
            })
            .doOnError(error -> {
                log.error("Scheduled cleanup failed completely at {}: {} ({})", 
                         LocalDateTime.now(), error.getMessage(), error.getClass().getSimpleName(), error);
                
                cleanupFailureCounter.increment();
                failedCleanups.incrementAndGet();
                
                // 记录失败的清理统计
                try {
                    saveCleanupStats();
                    log.debug("Cleanup failure statistics saved");
                } catch (Exception e) {
                    log.warn("Failed to save cleanup stats after error: {}", e.getMessage());
                }
                
                // 记录故障排除信息
                log.error("Troubleshooting info: Check storage connectivity, verify configuration, " +
                         "review retention settings (current: {}days)", retentionDays);
            })
            .subscribe();
    }
    @Override
    public Mono<CleanupResult> cleanupExpiredTokens() {
        return Mono.fromCallable(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting expired tokens cleanup at {}", startTime);
            
            try {
                // 获取清理前的令牌数量用于计算清理数量
                Long beforeCount = jwtPersistenceService.countActiveTokens().block();
                if (beforeCount == null) {
                    beforeCount = 0L;
                }
                
                // 使用持久化服务清理过期令牌
                jwtPersistenceService.removeExpiredTokens().block();
                
                // 获取清理后的令牌数量
                Long afterCount = jwtPersistenceService.countActiveTokens().block();
                if (afterCount == null) {
                    afterCount = 0L;
                }
                
                long removedCount = Math.max(0, beforeCount - afterCount);
                
                LocalDateTime endTime = LocalDateTime.now();
                long duration = java.time.Duration.between(startTime, endTime).toMillis();
                
                CleanupResult result = new CleanupResult(removedCount, 0, startTime, endTime, true);
                
                // 添加详细信息
                Map<String, Object> details = new HashMap<>();
                details.put("cleanupType", "tokens");
                details.put("beforeCount", beforeCount);
                details.put("afterCount", afterCount);
                details.put("removedCount", removedCount);
                details.put("duration", duration);
                details.put("startTime", startTime.toString());
                details.put("endTime", endTime.toString());
                result.setDetails(details);
                
                // 更新统计信息
                tokenCleanupCounter.increment(removedCount);
                totalTokensRemoved.addAndGet(removedCount);
                
                if (removedCount > 0) {
                    log.info("Expired tokens cleanup completed successfully. " +
                            "Removed {} tokens in {}ms. Total tokens removed: {}", 
                            removedCount, duration, totalTokensRemoved.get());
                } else {
                    log.debug("Expired tokens cleanup completed. No expired tokens found. Duration: {}ms", duration);
                }
                
                return result;
            } catch (Exception e) {
                LocalDateTime endTime = LocalDateTime.now();
                long duration = java.time.Duration.between(startTime, endTime).toMillis();
                CleanupResult result = new CleanupResult(0, 0, startTime, endTime, false);
                result.setErrorMessage(e.getMessage());
                
                // 添加错误详细信息
                Map<String, Object> details = new HashMap<>();
                details.put("cleanupType", "tokens");
                details.put("error", e.getMessage());
                details.put("errorClass", e.getClass().getSimpleName());
                details.put("duration", duration);
                details.put("startTime", startTime.toString());
                details.put("endTime", endTime.toString());
                result.setDetails(details);
                
                cleanupFailureCounter.increment();
                failedCleanups.incrementAndGet();
                log.error("Failed to cleanup expired tokens after {}ms: {}", 
                         duration, e.getMessage(), e);
                return result;
            }
        })
        .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(1))
            .filter(throwable -> !(throwable instanceof IllegalArgumentException))
            .doBeforeRetry(retrySignal -> {
                long attempt = retrySignal.totalRetries() + 1;
                Throwable failure = retrySignal.failure();
                log.warn("Retrying token cleanup, attempt {}/3: {} ({})", 
                        attempt, failure.getMessage(), failure.getClass().getSimpleName());
                
                // 记录重试统计
                cleanupFailureCounter.increment();
            })
            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                log.error("Token cleanup retry exhausted after {} attempts. Final error: {}", 
                         retrySignal.totalRetries(), retrySignal.failure().getMessage());
                return retrySignal.failure();
            })
        );
    }
    
    @Override
    public Mono<CleanupResult> cleanupExpiredBlacklistEntries() {
        return Mono.fromCallable(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting expired blacklist entries cleanup at {}", startTime);
        try {
                // 清理过期的黑名单条目
                Long removedCount = jwtBlacklistService.cleanupExpiredEntriesWithCount().block();
                if (removedCount == null) {
                    removedCount = 0L;
                }
                
                LocalDateTime endTime = LocalDateTime.now();
                long duration = java.time.Duration.between(startTime, endTime).toMillis();
                
                CleanupResult result = new CleanupResult(0, removedCount, startTime, endTime, true);
                
                // 添加详细信息
                Map<String, Object> details = new HashMap<>();
                details.put("cleanupType", "blacklist");
                details.put("removedCount", removedCount);
                details.put("duration", duration);
                details.put("startTime", startTime.toString());
                details.put("endTime", endTime.toString());
                result.setDetails(details);
                
                // 更新统计信息
                blacklistCleanupCounter.increment(removedCount);
                totalBlacklistEntriesRemoved.addAndGet(removedCount);
                
                if (removedCount > 0) {
                    log.info("Expired blacklist entries cleanup completed successfully. " +
                            "Removed {} entries in {}ms. Total blacklist entries removed: {}", 
                            removedCount, duration, totalBlacklistEntriesRemoved.get());
                } else {
                    log.debug("Expired blacklist entries cleanup completed. No expired entries found. Duration: {}ms", duration);
                }
                
                return result;
                    } catch (Exception e) {
                LocalDateTime endTime = LocalDateTime.now();
                long duration = java.time.Duration.between(startTime, endTime).toMillis();
                CleanupResult result = new CleanupResult(0, 0, startTime, endTime, false);
                result.setErrorMessage(e.getMessage());
                // 添加错误详细信息
                Map<String, Object> details = new HashMap<>();
                details.put("cleanupType", "blacklist");
                details.put("error", e.getMessage());
                details.put("errorClass", e.getClass().getSimpleName());
                details.put("duration", duration);
                details.put("startTime", startTime.toString());
                details.put("endTime", endTime.toString());
                result.setDetails(details);
                
                cleanupFailureCounter.increment();
                failedCleanups.incrementAndGet();
                
                log.error("Failed to cleanup expired blacklist entries after {}ms: {}", 
                         duration, e.getMessage(), e);
                return result;
            }
        })
        .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(1))
            .filter(throwable -> !(throwable instanceof IllegalArgumentException))
            .doBeforeRetry(retrySignal -> {
                long attempt = retrySignal.totalRetries() + 1;
                Throwable failure = retrySignal.failure();
                log.warn("Retrying blacklist cleanup, attempt {}/3: {} ({})", 
                        attempt, failure.getMessage(), failure.getClass().getSimpleName());
                
                // 记录重试统计
                cleanupFailureCounter.increment();
            })
            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                log.error("Blacklist cleanup retry exhausted after {} attempts. Final error: {}", 
                         retrySignal.totalRetries(), retrySignal.failure().getMessage());
                return retrySignal.failure();
            })
        );
    }
    
    @Override
    public Mono<CleanupResult> performFullCleanup() {
        return Mono.fromCallable(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting full JWT cleanup operation at {}", startTime);
            
            CleanupResult tokenResult = null;
            CleanupResult blacklistResult = null;
        try {
                // 清理过期令牌
                log.info("Phase 1: Cleaning up expired tokens...");
                tokenResult = cleanupExpiredTokens().block();
                if (tokenResult == null) {
                    tokenResult = new CleanupResult(0, 0, startTime, LocalDateTime.now(), false);
                    tokenResult.setErrorMessage("Token cleanup returned null result");
                    log.warn("Token cleanup returned null result");
                }
                
                // 清理过期黑名单条目
                log.info("Phase 2: Cleaning up expired blacklist entries...");
                blacklistResult = cleanupExpiredBlacklistEntries().block();
                if (blacklistResult == null) {
                    blacklistResult = new CleanupResult(0, 0, startTime, LocalDateTime.now(), false);
                    blacklistResult.setErrorMessage("Blacklist cleanup returned null result");
                    log.warn("Blacklist cleanup returned null result");
                }
                
                LocalDateTime endTime = LocalDateTime.now();
                long totalDuration = java.time.Duration.between(startTime, endTime).toMillis();
                // 合并结果
                CleanupResult combinedResult = new CleanupResult(
                    tokenResult.getRemovedTokens(),
                    blacklistResult.getRemovedBlacklistEntries(),
                    startTime,
                    endTime,
                    tokenResult.isSuccess() && blacklistResult.isSuccess()
                );
                
                // 设置详细信息
                Map<String, Object> details = new HashMap<>();
                details.put("tokenCleanupSuccess", tokenResult.isSuccess());
                details.put("blacklistCleanupSuccess", blacklistResult.isSuccess());
                details.put("tokenCleanupDuration", tokenResult.getDurationMs());
                details.put("blacklistCleanupDuration", blacklistResult.getDurationMs());
                details.put("totalDuration", totalDuration);
                details.put("phases", 2);
                details.put("retentionDays", retentionDays);
                details.put("batchSize", batchSize);
                
                // 添加子任务详细信息
                if (tokenResult.getDetails() != null) {
                    details.put("tokenDetails", tokenResult.getDetails());
                }
                if (blacklistResult.getDetails() != null) {
                    details.put("blacklistDetails", blacklistResult.getDetails());
                }
                
                if (!tokenResult.isSuccess()) {
                    details.put("tokenCleanupError", tokenResult.getErrorMessage());
                }
                if (!blacklistResult.isSuccess()) {
                    details.put("blacklistCleanupError", blacklistResult.getErrorMessage());
                }
                
                combinedResult.setDetails(details);
                
                // 更新统计信息
                totalCleanupsPerformed.incrementAndGet();
                lastCleanupTime = endTime;
                
                if (!combinedResult.isSuccess()) {
                    failedCleanups.incrementAndGet();
                    String errorMsg = String.format("Partial cleanup failure - Token: %s, Blacklist: %s", 
                                    tokenResult.isSuccess() ? "SUCCESS" : "FAILED",
                                    blacklistResult.isSuccess() ? "SUCCESS" : "FAILED");
                    combinedResult.setErrorMessage(errorMsg);
                    
                    log.warn("Full cleanup completed with partial failures: {}", errorMsg);
            } else {
                    log.info("Full cleanup completed successfully!");
            }
            
                // 保存统计信息
                try {
                    saveCleanupStats();
                    log.debug("Cleanup statistics saved successfully");
                } catch (Exception statsError) {
                    log.warn("Failed to save cleanup statistics: {}", statsError.getMessage());
                }
                
                // 记录计时器
                sample.stop(cleanupTimer);
                
                // 详细的完成日志
                log.info("Full cleanup summary: Success={}, Tokens removed={}, Blacklist entries removed={}, " +
                        "Total duration={}ms, Token phase={}ms, Blacklist phase={}ms, " +
                        "Total cleanups performed={}, Total items removed={}",
                        combinedResult.isSuccess(), 
                        combinedResult.getRemovedTokens(), 
                        combinedResult.getRemovedBlacklistEntries(), 
                        totalDuration,
                        tokenResult.getDurationMs(),
                        blacklistResult.getDurationMs(),
                        totalCleanupsPerformed.get(),
                        totalTokensRemoved.get() + totalBlacklistEntriesRemoved.get());
                
                return combinedResult;
        } catch (Exception e) {
                LocalDateTime endTime = LocalDateTime.now();
                long totalDuration = java.time.Duration.between(startTime, endTime).toMillis();
                CleanupResult result = new CleanupResult(0, 0, startTime, endTime, false);
                result.setErrorMessage(e.getMessage());
                
                // 添加错误详细信息
                Map<String, Object> details = new HashMap<>();
                details.put("error", e.getMessage());
                details.put("errorClass", e.getClass().getSimpleName());
                details.put("totalDuration", totalDuration);
                details.put("tokenPhaseCompleted", tokenResult != null);
                details.put("blacklistPhaseCompleted", blacklistResult != null);
                
                if (tokenResult != null) {
                    details.put("tokenCleanupSuccess", tokenResult.isSuccess());
                    details.put("tokenCleanupDuration", tokenResult.getDurationMs());
                    details.put("tokensRemoved", tokenResult.getRemovedTokens());
        }
                if (blacklistResult != null) {
                    details.put("blacklistCleanupSuccess", blacklistResult.isSuccess());
                    details.put("blacklistCleanupDuration", blacklistResult.getDurationMs());
                    details.put("blacklistEntriesRemoved", blacklistResult.getRemovedBlacklistEntries());
    }
                
                result.setDetails(details);
                
                failedCleanups.incrementAndGet();
                cleanupFailureCounter.increment();
                sample.stop(cleanupTimer);
                
                log.error("Full cleanup operation failed after {}ms: {} ({})", 
                         totalDuration, e.getMessage(), e.getClass().getSimpleName(), e);
                
                // 尝试保存失败统计
                try {
                    saveCleanupStats();
                } catch (Exception statsError) {
                    log.warn("Failed to save cleanup statistics after error: {}", statsError.getMessage());
}                
                return result;
            }
        });
    }
    
    @Override
    public Mono<CleanupStats> getCleanupStats() {
        return Mono.fromCallable(() -> {
            CleanupStats stats = new CleanupStats();
            
            stats.setLastCleanupTime(lastCleanupTime);
            stats.setTotalCleanupsPerformed(totalCleanupsPerformed.get());
            stats.setTotalTokensRemoved(totalTokensRemoved.get());
            stats.setTotalBlacklistEntriesRemoved(totalBlacklistEntriesRemoved.get());
            stats.setFailedCleanups(failedCleanups.get());
            stats.setCleanupEnabled(true);
            stats.setCleanupSchedule(cleanupSchedule);
            
            // 计算平均清理时间
            long totalCleanups = totalCleanupsPerformed.get();
            if (totalCleanups > 0) {
                double avgDuration = cleanupTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) / totalCleanups;
                stats.setAverageCleanupDurationMs(avgDuration);
            } else {
                stats.setAverageCleanupDurationMs(0.0);
            }
            
            // 计算下次清理时间（简化实现，实际应该基于cron表达式计算）
            if (lastCleanupTime != null) {
                stats.setNextScheduledCleanup(lastCleanupTime.plusDays(1).withHour(2).withMinute(0).withSecond(0));
            } else {
                stats.setNextScheduledCleanup(LocalDateTime.now().plusDays(1).withHour(2).withMinute(0).withSecond(0));
            }
            
            // 详细的性能指标
            Map<String, Object> performanceMetrics = new HashMap<>();
            
            // 基本配置
            performanceMetrics.put("retentionDays", retentionDays);
            performanceMetrics.put("batchSize", batchSize);
            performanceMetrics.put("cleanupSchedule", cleanupSchedule);
            
            // 时间统计
            double totalCleanupTimeMs = cleanupTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
            performanceMetrics.put("totalCleanupTimeMs", totalCleanupTimeMs);
            performanceMetrics.put("averageCleanupTimeMs", stats.getAverageCleanupDurationMs());
            
            // 成功率统计
            double successRate = totalCleanups > 0 ? 
                (double) (totalCleanups - failedCleanups.get()) / totalCleanups * 100 : 100.0;
            performanceMetrics.put("successRate", successRate);
            performanceMetrics.put("failureRate", 100.0 - successRate);
            
            // 清理效率统计
            long totalItemsRemoved = totalTokensRemoved.get() + totalBlacklistEntriesRemoved.get();
            performanceMetrics.put("totalItemsRemoved", totalItemsRemoved);
            performanceMetrics.put("averageItemsPerCleanup", totalCleanups > 0 ? 
                (double) totalItemsRemoved / totalCleanups : 0.0);
            
            // 时间效率统计
            if (totalCleanupTimeMs > 0 && totalItemsRemoved > 0) {
                performanceMetrics.put("itemsPerSecond", totalItemsRemoved / (totalCleanupTimeMs / 1000.0));
                performanceMetrics.put("millisecondsPerItem", totalCleanupTimeMs / totalItemsRemoved);
            } else {
                performanceMetrics.put("itemsPerSecond", 0.0);
                performanceMetrics.put("millisecondsPerItem", 0.0);
            }
            
            // 分类统计
            performanceMetrics.put("tokenCleanupRatio", totalItemsRemoved > 0 ? 
                (double) totalTokensRemoved.get() / totalItemsRemoved * 100 : 0.0);
            performanceMetrics.put("blacklistCleanupRatio", totalItemsRemoved > 0 ? 
                (double) totalBlacklistEntriesRemoved.get() / totalItemsRemoved * 100 : 0.0);
            
            // 健康状态指标
            performanceMetrics.put("isHealthy", successRate >= 90.0); // 90%以上成功率认为健康
            performanceMetrics.put("lastCleanupAge", lastCleanupTime != null ? 
                java.time.Duration.between(lastCleanupTime, LocalDateTime.now()).toHours() : -1);
            
            // Micrometer指标统计
            performanceMetrics.put("tokenCleanupCount", tokenCleanupCounter.count());
            performanceMetrics.put("blacklistCleanupCount", blacklistCleanupCounter.count());
            performanceMetrics.put("cleanupFailureCount", cleanupFailureCounter.count());
            
            // 系统状态
            performanceMetrics.put("systemTime", System.currentTimeMillis());
            performanceMetrics.put("uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
            
            stats.setPerformanceMetrics(performanceMetrics);
            
            log.debug("Generated cleanup stats: cleanups={}, items={}, success={:.1f}%, avg={}ms", 
                     totalCleanups, totalItemsRemoved, successRate, stats.getAverageCleanupDurationMs());
            
            return stats;
        })
        .doOnError(error -> log.error("Failed to generate cleanup stats: {}", error.getMessage(), error));
    }
    
    /**
     * 加载清理统计信息
     */
    private void loadCleanupStats() {
        try {
            Map<String, Object> statsData = storeManager.getConfig(CLEANUP_STATS_KEY);
            if (statsData != null) {
                // 基本统计数据
                Object totalCleanupsObj = statsData.get("totalCleanupsPerformed");
                if (totalCleanupsObj instanceof Number) {
                    totalCleanupsPerformed.set(((Number) totalCleanupsObj).longValue());
                }
                
                Object totalTokensObj = statsData.get("totalTokensRemoved");
                if (totalTokensObj instanceof Number) {
                    totalTokensRemoved.set(((Number) totalTokensObj).longValue());
                }
                
                Object totalBlacklistObj = statsData.get("totalBlacklistEntriesRemoved");
                if (totalBlacklistObj instanceof Number) {
                    totalBlacklistEntriesRemoved.set(((Number) totalBlacklistObj).longValue());
                }
                
                Object failedCleanupsObj = statsData.get("failedCleanups");
                if (failedCleanupsObj instanceof Number) {
                    failedCleanups.set(((Number) failedCleanupsObj).longValue());
                }
                
                // 时间信息
                Object lastCleanupObj = statsData.get("lastCleanupTime");
                if (lastCleanupObj instanceof String) {
                    try {
                        lastCleanupTime = LocalDateTime.parse((String) lastCleanupObj);
                    } catch (Exception e) {
                        log.warn("Failed to parse last cleanup time: {}", lastCleanupObj);
                    }
                }
                
                // 验证数据完整性
                long totalCleanups = totalCleanupsPerformed.get();
                long totalItems = totalTokensRemoved.get() + totalBlacklistEntriesRemoved.get();
                long failures = failedCleanups.get();
                
                if (totalCleanups < 0 || totalItems < 0 || failures < 0 || failures > totalCleanups) {
                    log.warn("Loaded cleanup stats appear to be corrupted, resetting to defaults");
                    resetCleanupStats();
                } else {
                    double successRate = totalCleanups > 0 ? 
                        (double) (totalCleanups - failures) / totalCleanups * 100 : 100.0;
                    
                    log.info("Loaded cleanup stats: cleanups={}, tokens={}, blacklist={}, failed={}, success={:.1f}%",
                            totalCleanups, totalTokensRemoved.get(), 
                            totalBlacklistEntriesRemoved.get(), failures, successRate);
                    
                    if (lastCleanupTime != null) {
                        long hoursAgo = java.time.Duration.between(lastCleanupTime, LocalDateTime.now()).toHours();
                        log.info("⏰ Last cleanup was {} hours ago at {}", hoursAgo, lastCleanupTime);
                    }
                }
                
                // 检查统计版本（用于未来的数据迁移）
                Object versionObj = statsData.get("statsVersion");
                if (versionObj instanceof String) {
                    String version = (String) versionObj;
                    log.debug("📋 Cleanup stats version: {}", version);
                }
                
            } else {
                log.info("📊 No existing cleanup stats found, starting with clean slate");
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to load cleanup stats: {} ({}), starting with defaults", 
                    e.getMessage(), e.getClass().getSimpleName());
            resetCleanupStats();
        }
    }
    
    /**
     * 重置清理统计信息到默认值
     */
    private void resetCleanupStats() {
        totalCleanupsPerformed.set(0);
        totalTokensRemoved.set(0);
        totalBlacklistEntriesRemoved.set(0);
        failedCleanups.set(0);
        lastCleanupTime = null;
        
        log.info("🔄 Reset cleanup stats to defaults");
    }
    
    /**
     * 保存清理统计信息
     */
    private void saveCleanupStats() {
        try {
            Map<String, Object> statsData = new HashMap<>();
            
            // 基本统计
            statsData.put("totalCleanupsPerformed", totalCleanupsPerformed.get());
            statsData.put("totalTokensRemoved", totalTokensRemoved.get());
            statsData.put("totalBlacklistEntriesRemoved", totalBlacklistEntriesRemoved.get());
            statsData.put("failedCleanups", failedCleanups.get());
            statsData.put("lastCleanupTime", lastCleanupTime != null ? lastCleanupTime.toString() : null);
            
            // 配置信息
            statsData.put("retentionDays", retentionDays);
            statsData.put("batchSize", batchSize);
            statsData.put("cleanupSchedule", cleanupSchedule);
            
            // 性能统计
            long totalCleanups = totalCleanupsPerformed.get();
            if (totalCleanups > 0) {
                double avgDuration = cleanupTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) / totalCleanups;
                statsData.put("averageCleanupDurationMs", avgDuration);
                
                double successRate = (double) (totalCleanups - failedCleanups.get()) / totalCleanups * 100;
                statsData.put("successRate", successRate);
            } else {
                statsData.put("averageCleanupDurationMs", 0.0);
                statsData.put("successRate", 100.0);
            }
            
            // 计数器统计
            statsData.put("tokenCleanupCount", tokenCleanupCounter.count());
            statsData.put("blacklistCleanupCount", blacklistCleanupCounter.count());
            statsData.put("cleanupFailureCount", cleanupFailureCounter.count());
            
            // 时间戳
            statsData.put("updatedAt", LocalDateTime.now());
            statsData.put("systemTime", System.currentTimeMillis());
            
            // 版本信息（用于统计数据迁移）
            statsData.put("statsVersion", "1.0");
            
            storeManager.saveConfig(CLEANUP_STATS_KEY, statsData);
            
            log.debug("Saved cleanup stats to storage: cleanups={}, items={}, failures={}", 
                     totalCleanups, totalTokensRemoved.get() + totalBlacklistEntriesRemoved.get(), 
                     failedCleanups.get());
                     
        } catch (Exception e) {
            log.warn("Failed to save cleanup stats: {} ({})", e.getMessage(), e.getClass().getSimpleName());
        }
    }
}

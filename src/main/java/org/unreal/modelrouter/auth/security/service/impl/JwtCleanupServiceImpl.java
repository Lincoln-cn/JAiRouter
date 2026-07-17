package org.unreal.modelrouter.auth.security.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
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

    @Value("${jairouter.security.jwt.persistence.cleanup.retention-days:30}")
    private int retentionDays;

    @Value("${jairouter.security.jwt.persistence.cleanup.batch-size:1000}")
    private int batchSize;

    @Value("${jairouter.security.jwt.persistence.cleanup.schedule:0 0 2 * * ?}")
    private String cleanupSchedule;

    private JwtCleanupMetrics metrics;
    private JwtCleanupStatsManager statsManager;

    @PostConstruct
    public void init() {
        this.metrics = new JwtCleanupMetrics(meterRegistry);
        this.statsManager = new JwtCleanupStatsManager(storeManager, metrics, retentionDays, batchSize, cleanupSchedule);
        statsManager.loadStats();

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
        log.info("Cleanup configuration: retention={}days, batchSize={}, totalCleanupsPerformed={}",
                retentionDays, batchSize, statsManager.getTotalCleanupsPerformed().get());

        performFullCleanup()
            .doOnSuccess(result -> logCleanupResult(result, scheduledTime))
            .doOnError(error -> logCleanupError(error))
            .subscribe();
    }

    /**
     * 记录清理结果
     */
    private void logCleanupResult(final CleanupResult result, final LocalDateTime scheduledTime) {
        if (result.isSuccess()) {
            log.info("Scheduled cleanup completed successfully at {}. Removed {} tokens and {} blacklist entries in {}ms",
                    LocalDateTime.now(), result.getRemovedTokens(), result.getRemovedBlacklistEntries(), result.getDurationMs());
            log.info("Cleanup success rate: {:.1f}% ({} successful out of {} total)",
                    statsManager.getSuccessRate(),
                    statsManager.getTotalCleanupsPerformed().get() - statsManager.getFailedCleanups().get(),
                    statsManager.getTotalCleanupsPerformed().get());
        } else {
            log.warn("Scheduled cleanup completed with errors at {}. Error: {}",
                    LocalDateTime.now(), result.getErrorMessage());
        }

        LocalDateTime nextCleanup = scheduledTime.plusDays(1).withHour(2).withMinute(0).withSecond(0);
        log.info("Next scheduled cleanup: {}", nextCleanup);
    }

    /**
     * 记录清理错误
     */
    private void logCleanupError(final Throwable error) {
        log.error("Scheduled cleanup failed completely at {}: {} ({})",
                LocalDateTime.now(), error.getMessage(), error.getClass().getSimpleName(), error);
        metrics.recordFailure();
        statsManager.saveStats();
        log.error("Troubleshooting info: Check storage connectivity, verify configuration, review retention settings (current: {}days)", retentionDays);
    }

    @Override
    public Mono<CleanupResult> cleanupExpiredTokens() {
        return Mono.fromCallable(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting expired tokens cleanup at {}", startTime);

            try {
                Long beforeCount = jwtPersistenceService.countActiveTokens().block();
                if (beforeCount == null) beforeCount = 0L;

                jwtPersistenceService.removeExpiredTokens().block();

                Long afterCount = jwtPersistenceService.countActiveTokens().block();
                if (afterCount == null) afterCount = 0L;

                long removedCount = Math.max(0, beforeCount - afterCount);
                LocalDateTime endTime = LocalDateTime.now();
                long duration = java.time.Duration.between(startTime, endTime).toMillis();

                CleanupResult result = new CleanupResult(removedCount, 0, startTime, endTime, true);
                result.setDetails(buildTokenDetails(beforeCount, afterCount, removedCount, duration, startTime, endTime));

                metrics.recordTokenCleanup(removedCount);

                if (removedCount > 0) {
                    log.info("Expired tokens cleanup completed. Removed {} tokens in {}ms", removedCount, duration);
                } else {
                    log.debug("Expired tokens cleanup completed. No expired tokens found. Duration: {}ms", duration);
                }

                return result;
            } catch (Exception e) {
                return handleTokenCleanupError(e, startTime);
            }
        }).retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(1))
            .filter(throwable -> !(throwable instanceof IllegalArgumentException))
            .doBeforeRetry(retrySignal -> {
                log.warn("Retrying token cleanup, attempt {}/3: {}",
                        retrySignal.totalRetries() + 1, retrySignal.failure().getMessage());
                metrics.recordFailure();
            }));
    }

    /**
     * 处理令牌清理错误
     */
    private CleanupResult handleTokenCleanupError(final Exception e, final LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long duration = java.time.Duration.between(startTime, endTime).toMillis();
        CleanupResult result = new CleanupResult(0, 0, startTime, endTime, false);
        result.setErrorMessage(e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("cleanupType", "tokens");
        details.put("error", e.getMessage());
        details.put("errorClass", e.getClass().getSimpleName());
        details.put("duration", duration);
        result.setDetails(details);

        metrics.recordFailure();
        statsManager.recordFailure();
        log.error("Failed to cleanup expired tokens after {}ms: {}", duration, e.getMessage(), e);
        return result;
    }

    @Override
    public Mono<CleanupResult> cleanupExpiredBlacklistEntries() {
        return Mono.fromCallable(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting expired blacklist entries cleanup at {}", startTime);

            try {
                Long removedCount = jwtBlacklistService.cleanupExpiredEntriesWithCount().block();
                if (removedCount == null) removedCount = 0L;

                LocalDateTime endTime = LocalDateTime.now();
                long duration = java.time.Duration.between(startTime, endTime).toMillis();

                CleanupResult result = new CleanupResult(0, removedCount, startTime, endTime, true);
                result.setDetails(buildBlacklistDetails(removedCount, duration, startTime, endTime));

                metrics.recordBlacklistCleanup(removedCount);

                if (removedCount > 0) {
                    log.info("Expired blacklist entries cleanup completed. Removed {} entries in {}ms", removedCount, duration);
                } else {
                    log.debug("Expired blacklist entries cleanup completed. No expired entries found. Duration: {}ms", duration);
                }

                return result;
            } catch (Exception e) {
                return handleBlacklistCleanupError(e, startTime);
            }
        }).retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(1))
            .filter(throwable -> !(throwable instanceof IllegalArgumentException))
            .doBeforeRetry(retrySignal -> {
                log.warn("Retrying blacklist cleanup, attempt {}/3: {}",
                        retrySignal.totalRetries() + 1, retrySignal.failure().getMessage());
                metrics.recordFailure();
            }));
    }

    /**
     * 处理黑名单清理错误
     */
    private CleanupResult handleBlacklistCleanupError(final Exception e, final LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long duration = java.time.Duration.between(startTime, endTime).toMillis();
        CleanupResult result = new CleanupResult(0, 0, startTime, endTime, false);
        result.setErrorMessage(e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("cleanupType", "blacklist");
        details.put("error", e.getMessage());
        details.put("errorClass", e.getClass().getSimpleName());
        details.put("duration", duration);
        result.setDetails(details);

        metrics.recordFailure();
        statsManager.recordFailure();
        log.error("Failed to cleanup expired blacklist entries after {}ms: {}", duration, e.getMessage(), e);
        return result;
    }

    @Override
    public Mono<CleanupResult> performFullCleanup() {
        return Mono.fromCallable(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting full JWT cleanup operation at {}", startTime);

            CleanupResult tokenResult = null;
            CleanupResult blacklistResult = null;

            try {
                log.info("Phase 1: Cleaning up expired tokens...");
                tokenResult = cleanupExpiredTokens().block();
                if (tokenResult == null) {
                    tokenResult = new CleanupResult(0, 0, startTime, LocalDateTime.now(), false);
                    tokenResult.setErrorMessage("Token cleanup returned null result");
                }

                log.info("Phase 2: Cleaning up expired blacklist entries...");
                blacklistResult = cleanupExpiredBlacklistEntries().block();
                if (blacklistResult == null) {
                    blacklistResult = new CleanupResult(0, 0, startTime, LocalDateTime.now(), false);
                    blacklistResult.setErrorMessage("Blacklist cleanup returned null result");
                }

                return buildCombinedResult(tokenResult, blacklistResult, startTime);
            } catch (Exception e) {
                return handleFullCleanupError(e, tokenResult, blacklistResult, startTime);
            }
        });
    }

    /**
     * 构建合并结果
     */
    private CleanupResult buildCombinedResult(final CleanupResult tokenResult, final CleanupResult blacklistResult,
                                               final LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long totalDuration = java.time.Duration.between(startTime, endTime).toMillis();

        CleanupResult combinedResult = new CleanupResult(
                tokenResult.getRemovedTokens(),
                blacklistResult.getRemovedBlacklistEntries(),
                startTime, endTime,
                tokenResult.isSuccess() && blacklistResult.isSuccess()
        );

        Map<String, Object> details = new HashMap<>();
        details.put("tokenCleanupSuccess", tokenResult.isSuccess());
        details.put("blacklistCleanupSuccess", blacklistResult.isSuccess());
        details.put("tokenCleanupDuration", tokenResult.getDurationMs());
        details.put("blacklistCleanupDuration", blacklistResult.getDurationMs());
        details.put("totalDuration", totalDuration);
        details.put("retentionDays", retentionDays);
        details.put("batchSize", batchSize);
        combinedResult.setDetails(details);

        statsManager.updateCleanupComplete(tokenResult.getRemovedTokens(), blacklistResult.getRemovedBlacklistEntries());

        if (!combinedResult.isSuccess()) {
            combinedResult.setErrorMessage(String.format("Partial cleanup failure - Token: %s, Blacklist: %s",
                    tokenResult.isSuccess() ? "SUCCESS" : "FAILED",
                    blacklistResult.isSuccess() ? "SUCCESS" : "FAILED"));
            log.warn("Full cleanup completed with partial failures: {}", combinedResult.getErrorMessage());
        } else {
            log.info("Full cleanup completed successfully!");
        }

        statsManager.saveStats();
        logFullCleanupSummary(combinedResult, totalDuration, tokenResult, blacklistResult);

        return combinedResult;
    }

    /**
     * 记录完整清理摘要
     */
    private void logFullCleanupSummary(final CleanupResult result, final long totalDuration,
                                        final CleanupResult tokenResult, final CleanupResult blacklistResult) {
        log.info("Full cleanup summary: Success={}, Tokens removed={}, Blacklist entries removed={}, Total duration={}ms",
                result.isSuccess(), result.getRemovedTokens(), result.getRemovedBlacklistEntries(), totalDuration);
    }

    /**
     * 处理完整清理错误
     */
    private CleanupResult handleFullCleanupError(final Exception e, final CleanupResult tokenResult,
                                                  final CleanupResult blacklistResult, final LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long totalDuration = java.time.Duration.between(startTime, endTime).toMillis();

        CleanupResult result = new CleanupResult(0, 0, startTime, endTime, false);
        result.setErrorMessage(e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("error", e.getMessage());
        details.put("errorClass", e.getClass().getSimpleName());
        details.put("totalDuration", totalDuration);
        details.put("tokenPhaseCompleted", tokenResult != null);
        details.put("blacklistPhaseCompleted", blacklistResult != null);
        result.setDetails(details);

        statsManager.recordFailure();
        metrics.recordFailure();
        statsManager.saveStats();

        log.error("Full cleanup operation failed after {}ms: {}", totalDuration, e.getMessage(), e);
        return result;
    }

    @Override
    public Mono<CleanupStats> getCleanupStats() {
        return Mono.fromCallable(() -> {
            CleanupStats stats = new CleanupStats();

            stats.setLastCleanupTime(statsManager.getLastCleanupTime());
            stats.setTotalCleanupsPerformed(statsManager.getTotalCleanupsPerformed().get());
            stats.setTotalTokensRemoved(statsManager.getTotalTokensRemoved().get());
            stats.setTotalBlacklistEntriesRemoved(statsManager.getTotalBlacklistEntriesRemoved().get());
            stats.setFailedCleanups(statsManager.getFailedCleanups().get());
            stats.setCleanupEnabled(true);
            stats.setCleanupSchedule(cleanupSchedule);
            stats.setAverageCleanupDurationMs(statsManager.getAverageCleanupDurationMs());
            stats.setNextScheduledCleanup(statsManager.getNextScheduledCleanup());

            stats.setPerformanceMetrics(buildPerformanceMetrics(stats));

            return stats;
        }).doOnError(error -> log.error("Failed to generate cleanup stats: {}", error.getMessage(), error));
    }

    /**
     * 构建性能指标Map
     */
    private Map<String, Object> buildPerformanceMetrics(final CleanupStats stats) {
        Map<String, Object> perfMetrics = new HashMap<>();

        long totalCleanups = stats.getTotalCleanupsPerformed();
        long totalItemsRemoved = stats.getTotalTokensRemoved() + stats.getTotalBlacklistEntriesRemoved();
        double successRate = statsManager.getSuccessRate();

        perfMetrics.put("retentionDays", retentionDays);
        perfMetrics.put("batchSize", batchSize);
        perfMetrics.put("successRate", successRate);
        perfMetrics.put("failureRate", 100.0 - successRate);
        perfMetrics.put("totalItemsRemoved", totalItemsRemoved);
        perfMetrics.put("averageItemsPerCleanup", totalCleanups > 0 ? (double) totalItemsRemoved / totalCleanups : 0.0);
        perfMetrics.put("isHealthy", successRate >= 90.0);
        perfMetrics.put("tokenCleanupCount", this.metrics.getTokenCleanupCount());
        perfMetrics.put("blacklistCleanupCount", this.metrics.getBlacklistCleanupCount());
        perfMetrics.put("cleanupFailureCount", this.metrics.getFailureCount());

        return perfMetrics;
    }

    /**
     * 构建令牌清理详情
     */
    private Map<String, Object> buildTokenDetails(final long beforeCount, final long afterCount,
                                                   final long removedCount, final long duration,
                                                   final LocalDateTime startTime, final LocalDateTime endTime) {
        Map<String, Object> details = new HashMap<>();
        details.put("cleanupType", "tokens");
        details.put("beforeCount", beforeCount);
        details.put("afterCount", afterCount);
        details.put("removedCount", removedCount);
        details.put("duration", duration);
        details.put("startTime", startTime.toString());
        details.put("endTime", endTime.toString());
        return details;
    }

    /**
     * 构建黑名单清理详情
     */
    private Map<String, Object> buildBlacklistDetails(final long removedCount, final long duration,
                                                       final LocalDateTime startTime, final LocalDateTime endTime) {
        Map<String, Object> details = new HashMap<>();
        details.put("cleanupType", "blacklist");
        details.put("removedCount", removedCount);
        details.put("duration", duration);
        details.put("startTime", startTime.toString());
        details.put("endTime", endTime.toString());
        return details;
    }
}

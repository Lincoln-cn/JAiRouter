package org.unreal.modelrouter.auth.security.service.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT清理统计信息管理器
 * 负责加载、保存和管理清理统计信息
 */
@Slf4j
@RequiredArgsConstructor
public class JwtCleanupStatsManager {

    private static final String CLEANUP_STATS_KEY = "jwt_cleanup_stats";

    private final StoreManager storeManager;
    private final JwtCleanupMetrics metrics;
    private final int retentionDays;
    private final int batchSize;
    private final String cleanupSchedule;

    @Getter
    private final AtomicLong totalCleanupsPerformed = new AtomicLong(0);
    @Getter
    private final AtomicLong totalTokensRemoved = new AtomicLong(0);
    @Getter
    private final AtomicLong totalBlacklistEntriesRemoved = new AtomicLong(0);
    @Getter
    private final AtomicLong failedCleanups = new AtomicLong(0);
    @Getter
    private volatile LocalDateTime lastCleanupTime;

    /**
     * 加载清理统计信息
     */
    public void loadStats() {
        try {
            Map<String, Object> statsData = storeManager.getConfig(CLEANUP_STATS_KEY);
            if (statsData != null) {
                loadStatsFromMap(statsData);
            } else {
                log.info("📊 No existing cleanup stats found, starting with clean slate");
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to load cleanup stats: {} ({}), starting with defaults",
                    e.getMessage(), e.getClass().getSimpleName());
            resetStats();
        }
    }

    /**
     * 从Map加载统计数据
     */
    private void loadStatsFromMap(final Map<String, Object> statsData) {
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

        Object lastCleanupObj = statsData.get("lastCleanupTime");
        if (lastCleanupObj instanceof String) {
            try {
                lastCleanupTime = LocalDateTime.parse((String) lastCleanupObj);
            } catch (Exception e) {
                log.warn("Failed to parse last cleanup time: {}", lastCleanupObj);
            }
        }

        validateAndLogLoadedStats();
    }

    /**
     * 验证并记录加载的统计数据
     */
    private void validateAndLogLoadedStats() {
        long totalCleanups = totalCleanupsPerformed.get();
        long totalItems = totalTokensRemoved.get() + totalBlacklistEntriesRemoved.get();
        long failures = failedCleanups.get();

        if (totalCleanups < 0 || totalItems < 0 || failures < 0 || failures > totalCleanups) {
            log.warn("Loaded cleanup stats appear to be corrupted, resetting to defaults");
            resetStats();
        } else {
            double successRate = totalCleanups > 0
                    ? (double) (totalCleanups - failures) / totalCleanups * 100 : 100.0;

            log.info("Loaded cleanup stats: cleanups={}, tokens={}, blacklist={}, failed={}, success={:.1f}%",
                    totalCleanups, totalTokensRemoved.get(),
                    totalBlacklistEntriesRemoved.get(), failures, successRate);

            if (lastCleanupTime != null) {
                long hoursAgo = java.time.Duration.between(lastCleanupTime, LocalDateTime.now()).toHours();
                log.info("⏰ Last cleanup was {} hours ago at {}", hoursAgo, lastCleanupTime);
            }
        }
    }

    /**
     * 重置统计信息到默认值
     */
    public void resetStats() {
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
    public void saveStats() {
        try {
            Map<String, Object> statsData = buildStatsMap();
            storeManager.saveConfig(CLEANUP_STATS_KEY, statsData);

            log.debug("Saved cleanup stats to storage: cleanups={}, items={}, failures={}",
                    totalCleanupsPerformed.get(),
                    totalTokensRemoved.get() + totalBlacklistEntriesRemoved.get(),
                    failedCleanups.get());
        } catch (Exception e) {
            log.warn("Failed to save cleanup stats: {} ({})", e.getMessage(), e.getClass().getSimpleName());
        }
    }

    /**
     * 构建统计数据Map
     */
    private Map<String, Object> buildStatsMap() {
        Map<String, Object> statsData = new HashMap<>();

        statsData.put("totalCleanupsPerformed", totalCleanupsPerformed.get());
        statsData.put("totalTokensRemoved", totalTokensRemoved.get());
        statsData.put("totalBlacklistEntriesRemoved", totalBlacklistEntriesRemoved.get());
        statsData.put("failedCleanups", failedCleanups.get());
        statsData.put("lastCleanupTime", lastCleanupTime != null ? lastCleanupTime.toString() : null);

        statsData.put("retentionDays", retentionDays);
        statsData.put("batchSize", batchSize);
        statsData.put("cleanupSchedule", cleanupSchedule);

        long totalCleanups = totalCleanupsPerformed.get();
        if (totalCleanups > 0) {
            double avgDuration = metrics.getTotalCleanupTimeMs() / totalCleanups;
            statsData.put("averageCleanupDurationMs", avgDuration);

            double successRate = (double) (totalCleanups - failedCleanups.get()) / totalCleanups * 100;
            statsData.put("successRate", successRate);
        } else {
            statsData.put("averageCleanupDurationMs", 0.0);
            statsData.put("successRate", 100.0);
        }

        statsData.put("tokenCleanupCount", metrics.getTokenCleanupCount());
        statsData.put("blacklistCleanupCount", metrics.getBlacklistCleanupCount());
        statsData.put("cleanupFailureCount", metrics.getFailureCount());

        statsData.put("updatedAt", LocalDateTime.now());
        statsData.put("systemTime", System.currentTimeMillis());
        statsData.put("statsVersion", "1.0");

        return statsData;
    }

    /**
     * 更新清理完成统计
     */
    public void updateCleanupComplete(final long tokensRemoved, final long blacklistRemoved) {
        totalTokensRemoved.addAndGet(tokensRemoved);
        totalBlacklistEntriesRemoved.addAndGet(blacklistRemoved);
        totalCleanupsPerformed.incrementAndGet();
        lastCleanupTime = LocalDateTime.now();
    }

    /**
     * 记录清理失败
     */
    public void recordFailure() {
        failedCleanups.incrementAndGet();
        metrics.recordFailure();
    }

    /**
     * 计算成功率
     */
    public double getSuccessRate() {
        long totalCleanups = totalCleanupsPerformed.get();
        if (totalCleanups == 0) {
            return 100.0;
        }
        return (double) (totalCleanups - failedCleanups.get()) / totalCleanups * 100;
    }

    /**
     * 计算平均清理时间
     */
    public double getAverageCleanupDurationMs() {
        long totalCleanups = totalCleanupsPerformed.get();
        if (totalCleanups == 0) {
            return 0.0;
        }
        return metrics.getTotalCleanupTimeMs() / totalCleanups;
    }

    /**
     * 获取下次清理时间
     */
    public LocalDateTime getNextScheduledCleanup() {
        if (lastCleanupTime != null) {
            return lastCleanupTime.plusDays(1).withHour(2).withMinute(0).withSecond(0);
        }
        return LocalDateTime.now().plusDays(1).withHour(2).withMinute(0).withSecond(0);
    }
}

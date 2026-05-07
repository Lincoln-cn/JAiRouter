package org.unreal.modelrouter.persistence.store.persistence.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.persistence.store.persistence.adapter.RateLimiterStatePersistenceAdapter;

import java.time.Duration;
import java.util.Map;

/**
 * 限流器状态持久化集成组件
 *
 * v2.4.6: 将 RateLimitManager 与状态持久化系统集成
 *
 * 功能:
 * 1. 启动时恢复限流器状态
 * 2. 定期同步限流器状态到持久化层
 * 3. 提供手动同步/恢复接口
 *
 * @author JAiRouter Team
 * @since 2.4.6
 */
@Component
public class RateLimiterPersistenceIntegration {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterPersistenceIntegration.class);

    /**
     * 同步间隔（毫秒） - 默认 30秒
     */
    private static final long SYNC_INTERVAL_MS = 30000;

    /**
     * 恢复超时 - 默认 10秒
     */
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private RateLimitManager rateLimitManager;

    @Autowired
    private RateLimiterStatePersistenceAdapter persistenceAdapter;

    @Autowired
    private ModelRouterProperties properties;

    private volatile boolean initialized = false;

    /**
     * 应用启动后初始化
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Initializing rate limiter persistence integration");
        recoverRateLimiterStates();
        initialized = true;
        logger.info("Rate limiter persistence integration initialized successfully");
    }

    /**
     * 启动时恢复限流器状态
     */
    private void recoverRateLimiterStates() {
        logger.info("Recovering rate limiter states from persistence layer");

        try {
            persistenceAdapter.restoreAllRateLimiterStates()
                    .timeout(RECOVERY_TIMEOUT)
                    .subscribe(results -> {
                        int successCount = results.values().stream()
                                .mapToInt(b -> b ? 1 : 0)
                                .sum();
                        logger.info("Rate limiter states recovered: {} successful, {} failed",
                                successCount, results.size() - successCount);
                    }, error -> {
                        logger.warn("Rate limiter state recovery failed: {}", error.getMessage());
                    });
        } catch (Exception e) {
            logger.error("Exception during rate limiter recovery: {}", e.getMessage(), e);
        }
    }

    /**
     * 定期同步限流器状态
     *
     * 每30秒执行一次，确保状态一致性
     */
    @Scheduled(fixedRate = SYNC_INTERVAL_MS)
    public void scheduledSyncRateLimiterStates() {
        if (!initialized) {
            return;
        }

        logger.debug("Starting scheduled rate limiter state sync");

        try {
            persistenceAdapter.syncPendingStates()
                    .subscribe(count -> {
                        if (count > 0) {
                            logger.debug("Scheduled sync completed: {} rate limiter states", count);
                        }
                    }, error -> {
                        logger.warn("Scheduled sync failed: {}", error.getMessage());
                    });
        } catch (Exception e) {
            logger.error("Exception during scheduled sync: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发同步
     *
     * @return 同步的限流器数量
     */
    public int triggerManualSync() {
        if (!initialized) {
            logger.warn("Persistence integration not initialized");
            return 0;
        }

        try {
            Integer result = persistenceAdapter.syncPendingStates().block();
            return result != null ? result : 0;
        } catch (Exception e) {
            logger.error("Manual sync failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 手动触发恢复
     *
     * @return 恢复结果
     */
    public Map<String, Boolean> triggerManualRecovery() {
        if (!initialized) {
            logger.warn("Persistence integration not initialized");
            return Map.of();
        }

        try {
            Map<String, Boolean> result = persistenceAdapter.restoreAllRateLimiterStates()
                    .timeout(RECOVERY_TIMEOUT)
                    .block();
            return result != null ? result : Map.of();
        } catch (Exception e) {
            logger.error("Manual recovery failed: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * 获取集成状态
     *
     * @return 状态信息
     */
    public PersistenceIntegrationStatus getIntegrationStatus() {
        PersistenceIntegrationStatus.Builder builder = PersistenceIntegrationStatus.builder()
                .initialized(initialized)
                .syncIntervalMs(SYNC_INTERVAL_MS)
                .recoveryTimeoutMs(RECOVERY_TIMEOUT.toMillis());

        if (initialized && persistenceAdapter != null) {
            try {
                Map<String, Object> stats = persistenceAdapter.getStats().block();
                if (stats != null) {
                    Object registeredCount = stats.get("registeredCount");
                    Object pendingSyncCount = stats.get("pendingSyncCount");

                    builder.registeredCount(registeredCount != null ? (Integer) registeredCount : 0);
                    builder.pendingSyncCount(pendingSyncCount != null ? (Integer) pendingSyncCount : 0);
                }
            } catch (Exception e) {
                logger.debug("Failed to get adapter stats: {}", e.getMessage());
                builder.registeredCount(0);
                builder.pendingSyncCount(0);
            }
        }

        return builder.build();
    }

    /**
     * 获取限流器管理器状态
     *
     * @return RateLimitManager 状态
     */
    public Map<String, Object> getRateLimiterManagerStatus() {
        if (!initialized) {
            return Map.of("error", "not initialized");
        }
        return rateLimitManager.getAllRateLimiterStatus();
    }
}
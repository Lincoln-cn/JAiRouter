package org.unreal.modelrouter.monitor.service;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Token 用量记录的过期清理任务。
 *
 * <p>在此之前 {@link TokenUsageService#deleteOldUsageRecords(LocalDateTime)}
 * 没有任何调用方，导致 {@code token_usage} 表只增不减（#984）。本类把该方法接入定时任务，
 * 形态与 {@code ApiCallHistoryCleanupScheduler} 保持一致。</p>
 *
 * <p>默认保留 90 天，可用 {@code jairouter.monitoring.token-usage.retention-days} 调整；
 * 清理时间默认每天 03:00，刻意避开调用历史清理（02:00），避免两张大表同时扫。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.monitoring.token-usage.cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
public class TokenUsageCleanupScheduler {

    private final TokenUsageService tokenUsageService;
    private final TokenUsageRetentionProperties properties;

    /**
     * 按保留天数清理过期记录。
     *
     * <p>清理失败只记日志不抛出：定时任务的异常没有接收方，抛出只会污染调度线程的日志。</p>
     */
    @Scheduled(cron = "${jairouter.monitoring.token-usage.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanup() {
        try {
            final int retentionDays = properties.getRetentionDays();
            if (retentionDays <= 0) {
                log.warn("Token usage retention days is {}, skip cleanup", retentionDays);
                return;
            }
            final LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            log.info("Starting token usage cleanup, cutoff: {} (retention: {} days)", cutoff, retentionDays);
            final int deleted = tokenUsageService.deleteOldUsageRecords(cutoff);
            log.info("Token usage cleanup completed: deleted {} records (retention: {} days)",
                    deleted, retentionDays);
        } catch (Exception e) {
            log.error("Token usage cleanup failed: {}", e.getMessage(), e);
        }
    }
}

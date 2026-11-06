package org.unreal.modelrouter.router.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 语义缓存命中审计的过期清理.
 *
 * <p>审计表只增不减会持续膨胀；清理与 {@code ApiCallHistoryCleanupScheduler}
 * （02:00）、{@code TokenUsageCleanupScheduler}（03:00）错开，放在 04:20。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.semantic-cache.enabled", havingValue = "true", matchIfMissing = true)
public class SemanticCacheAuditCleanupScheduler {

    private final SemanticCacheAuditService auditService;

    /**
     * 每天清理一次过期的审计记录.
     */
    @Scheduled(cron = "${jairouter.semantic-cache.audit-cleanup-cron:0 20 4 * * ?}")
    public void cleanup() {
        try {
            final int removed = auditService.cleanupExpired();
            if (removed > 0) {
                log.info("清理语义缓存命中审计 {} 条", removed);
            }
        } catch (final RuntimeException e) {
            log.warn("清理语义缓存命中审计失败（不影响服务）", e);
        }
    }
}

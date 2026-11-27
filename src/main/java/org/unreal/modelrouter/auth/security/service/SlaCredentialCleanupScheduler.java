package org.unreal.modelrouter.auth.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回收 SLA 凭据表里的过期行（issue #1218）。
 *
 * <p>{@code sla_refresh_tokens} 与 {@code sla_bootstrap_tokens} 此前只增不减：
 * 吊销只把 {@code revoked_at} 置位、消费只把 {@code used_at} 置位，行本身永久留存。
 * 长期运行后这两张表会持续膨胀，账号被删除后残留的行也不再有任何读取方。</p>
 *
 * <p>保留期内的行仍然保留，方便审计比对与问题回溯；只删除「过期或已吊销且早于保留期」的行。</p>
 */
@Component
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true", matchIfMissing = true)
public class SlaCredentialCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaCredentialCleanupScheduler.class);

    private final SlaAccessService slaAccessService;

    /** 过期/已吊销行的保留天数，默认 30 天。 */
    @Value("${jairouter.security.sla.retention-days:30}")
    private int retentionDays;

    public SlaCredentialCleanupScheduler(SlaAccessService slaAccessService) {
        this.slaAccessService = slaAccessService;
    }

    /** 每小时的第 40 分钟执行一次，与可逆脱敏映射清理错开。 */
    @Scheduled(cron = "${jairouter.security.sla.cleanup-cron:0 40 * * * ?}")
    public void cleanup() {
        try {
            final SlaAccessService.CleanupResult removed =
                    slaAccessService.cleanupExpiredCredentials(retentionDays);
            if (removed.refreshTokens() > 0 || removed.bootstrapTokens() > 0) {
                log.info("清理过期的 SLA 凭据：刷新凭据 {} 条，接入令牌 {} 条",
                        removed.refreshTokens(), removed.bootstrapTokens());
            }
        } catch (RuntimeException e) {
            log.warn("清理过期 SLA 凭据失败: {}", e.toString());
        }
    }
}

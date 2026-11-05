package org.unreal.modelrouter.pii;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理 {@code pii_token_vault} 里的过期映射。
 *
 * <p>占位符只在 TTL 内有效，过期的密文留着既没用又是数据留存风险，所以定时删掉。
 * 与用量清理错开时间点，避免两张表同时扫。</p>
 */
@Component
@ConditionalOnProperty(name = "jairouter.pii.reversible.enabled", havingValue = "true")
public class PiiVaultCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(PiiVaultCleanupScheduler.class);

    private final ReversiblePiiService reversiblePiiService;

    public PiiVaultCleanupScheduler(ReversiblePiiService reversiblePiiService) {
        this.reversiblePiiService = reversiblePiiService;
    }

    /** 每小时的第 20 分钟执行一次。 */
    @Scheduled(cron = "${jairouter.pii.reversible.cleanup-cron:0 20 * * * ?}")
    public void cleanup() {
        try {
            final int removed = reversiblePiiService.cleanupExpired();
            if (removed > 0) {
                log.info("清理过期的可逆脱敏映射 {} 条", removed);
            }
        } catch (RuntimeException e) {
            log.warn("清理过期可逆脱敏映射失败: {}", e.toString());
        }
    }
}

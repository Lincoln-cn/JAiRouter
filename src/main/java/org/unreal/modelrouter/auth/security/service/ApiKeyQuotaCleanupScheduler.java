package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * API Key 每日使用量历史数据清理调度器
 * 定期清理超过 30 天的 dailyUsage 记录，防止内存膨胀
 *
 * @since v2.7.6
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.quota.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ApiKeyQuotaCleanupScheduler {

    private final ApiKeyQuotaService quotaService;

    @Autowired
    public ApiKeyQuotaCleanupScheduler(ApiKeyQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /**
     * 每天凌晨 3:00 执行清理
     * 清理所有 API Key 中超过 30 天的 dailyUsage 和 dailyTokenUsage 历史记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredDailyUsage() {
        log.debug("开始执行 API Key 每日使用记录清理任务");
        try {
            int cleaned = quotaService.cleanupExpiredDailyUsage();
            log.info("API Key 每日使用记录清理完成，共清理 {} 条过期记录", cleaned);
        } catch (Exception e) {
            log.error("API Key 每日使用记录清理失败", e);
        }
    }
}

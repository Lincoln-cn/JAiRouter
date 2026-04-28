package org.unreal.modelrouter.auth.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * API Key 过期清理调度器
 * 定期检查并清理或禁用过期的密钥
 *
 * 清理策略：
 * - 自动禁用已过期的密钥（默认行为）
 * - 可配置为自动删除过期密钥
 * - 可配置过期后保留天数（在这段时间内密钥仍可查看但不能使用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.api-key.expiration-cleanup.enabled", havingValue = "true", matchIfMissing = false)
public class ApiKeyExpirationScheduler {

    @Autowired(required = false)
    private ApiKeyService apiKeyService;

    /**
     * 每小时检查一次过期密钥
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 3600000毫秒
    public void checkExpiredKeys() {
        if (apiKeyService == null) {
            log.debug("API Key 服务未启用，跳过过期密钥检查");
            return;
        }

        log.debug("开始执行过期密钥检查任务");

        apiKeyService.cleanupExpiredKeys()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("过期密钥清理完成，共处理 {} 个密钥", count);
                            } else {
                                log.debug("没有发现需要清理的过期密钥");
                            }
                        },
                        error -> log.error("过期密钥清理任务执行失败", error)
                );
    }

    /**
     * 每天凌晨4点执行一次完整的过期密钥清理和统计
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void dailyExpirationCleanup() {
        if (apiKeyService == null) {
            log.debug("API Key 服务未启用，跳过每日过期密钥清理");
            return;
        }

        log.info("开始执行每日过期密钥清理任务");

        // 获取过期密钥统计
        apiKeyService.getExpirationStats()
                .subscribe(
                        stats -> {
                            log.info("过期密钥统计 - 总密钥数: {}, 已过期: {}, 今日过期: {}, 已禁用: {}",
                                    stats.getTotalKeys(),
                                    stats.getExpiredKeys(),
                                    stats.getExpiringToday(),
                                    stats.getDisabledKeys());
                        },
                        error -> log.error("获取过期密钥统计失败", error)
                );

        // 执行清理
        apiKeyService.cleanupExpiredKeys()
                .subscribe(
                        count -> log.info("每日过期密钥清理完成，共处理 {} 个密钥", count),
                        error -> log.error("每日过期密钥清理任务执行失败", error)
                );
    }
}
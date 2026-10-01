package org.unreal.modelrouter.auth.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.scheduling.ScheduledTaskLock;

import java.time.Duration;

/**
 * API Key 密钥轮换调度器
 * 定期检查并自动轮换需要更新的密钥
 *
 * 轮换策略：
 * - 如果密钥设置了 rotationPeriodDays > 0，且距离上次轮换已超过周期天数，则自动轮换
 * - 轮换会生成新的 keyValue，旧的密钥值将失效
 * - 轮换后更新 lastRotatedAt 时间戳
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "jairouter.security.api-key.rotation.enabled",
        havingValue = "true", matchIfMissing = false)
public class ApiKeyRotationScheduler {

    @Autowired(required = false)
    private ApiKeyService apiKeyService;

    private final ScheduledTaskLock taskLock;

    /**
     * 每小时检查一次需要轮换的密钥
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 3600000毫秒
    public void checkRotationNeeded() {
        if (apiKeyService == null) {
            log.debug("API Key 服务未启用，跳过密钥轮换检查");
            return;
        }

        // 轮换不是幂等的：两个副本同时轮换同一把 key 会让先发出的新 keyValue 立刻失效。
        // 多副本下只让一个副本执行本周期。
        if (!taskLock.tryAcquire("apikey.rotation", Duration.ofHours(1))) {
            return;
        }

        log.debug("开始执行密钥轮换检查任务");

        apiKeyService.rotateExpiredKeys()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("密钥轮换完成，共轮换了 {} 个密钥", count);
                            } else {
                                log.debug("没有发现需要轮换的密钥");
                            }
                        },
                        error -> log.error("密钥轮换任务执行失败", error)
                );
    }

    /**
     * 每天凌晨3点执行一次完整的轮换状态检查和统计
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyRotationStatusCheck() {
        if (apiKeyService == null) {
            log.debug("API Key 服务未启用，跳过每日轮换状态检查");
            return;
        }

        log.info("开始执行每日密钥轮换状态检查任务");

        // 获取需要轮换的密钥统计
        apiKeyService.getRotationStats()
                .subscribe(
                        stats -> {
                            log.info("密钥轮换统计 - 总密钥数: {}, 设置了轮换周期: {}, 需要轮换: {}, 今日已轮换: {}",
                                    stats.getTotalKeys(),
                                    stats.getKeysWithRotation(),
                                    stats.getKeysNeedingRotation(),
                                    stats.getRotatedToday());
                        },
                        error -> log.error("获取密钥轮换统计失败", error)
                );

        // 执行轮换
        apiKeyService.rotateExpiredKeys()
                .subscribe(
                        count -> log.info("每日密钥轮换检查完成，共轮换了 {} 个密钥", count),
                        error -> log.error("每日密钥轮换检查任务执行失败", error)
                );
    }
}
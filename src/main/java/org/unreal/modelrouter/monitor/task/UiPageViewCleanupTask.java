package org.unreal.modelrouter.monitor.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.service.UiAccessService;

/**
 * 页面访问记录的保留期清理（B4，2026-11-18）。
 *
 * <p>页面浏览是高频流水：不做清理的话，一个中等规模的部署每天会产生数万行。
 * 默认保留 90 天（与「最近 90 天」的统计窗口上限对齐），可用
 * {@code jairouter.monitoring.ui-access.retention-days} 调整，设为 0 或负数即停止清理。</p>
 *
 * <p>与 {@code AuditLogCleanupTask} 分开：那张表按风险等级分级保留，这张表只有一个保留期。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.monitoring.ui-access.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class UiPageViewCleanupTask {

    private final UiAccessService uiAccessService;

    /** 保留天数，默认 90 天。 */
    private final int retentionDays;

    public UiPageViewCleanupTask(
            final UiAccessService uiAccessService,
            @Value("${jairouter.monitoring.ui-access.retention-days:90}") final int retentionDays) {
        this.uiAccessService = uiAccessService;
        this.retentionDays = retentionDays;
    }

    /** 每天 02:30 清理（避开审计清理的 02:00，减少同一时刻的数据库压力）。 */
    @Scheduled(cron = "${jairouter.monitoring.ui-access.cleanup-schedule:0 30 2 * * *}")
    public void cleanup() {
        uiAccessService.purge(retentionDays)
                .doOnNext(removed -> log.info("页面访问记录清理完成：保留 {} 天，删除 {} 行", retentionDays, removed))
                .doOnError(error -> log.warn("页面访问记录清理失败: {}", error.toString()))
                .subscribe();
    }
}

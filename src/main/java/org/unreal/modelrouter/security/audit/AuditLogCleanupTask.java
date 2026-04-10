package org.unreal.modelrouter.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.config.properties.AuditConfig;

import java.time.LocalDateTime;

/**
 * 审计日志定时清理任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogCleanupTask {

    private final ExtendedSecurityAuditService auditService;
    private final AuditConfig auditConfig;

    /**
     * 定时清理过期审计日志
     * 默认每天凌晨2点执行
     */
    @Scheduled(cron = "${jairouter.security.audit.cleanup-schedule:0 0 2 * * *}")
    public void cleanupExpiredAuditLogs() {
        log.info("开始清理过期审计日志, 保留策略: {} 天", auditConfig.getRetentionDays());

        try {
            // 按风险等级分别清理
            cleanupByRiskLevel("LOW", auditConfig.getRetentionDaysByRiskLevel("LOW"));
            cleanupByRiskLevel("MEDIUM", auditConfig.getRetentionDaysByRiskLevel("MEDIUM"));
            
            // HIGH和CRITICAL级别的日志保留更长时间
            int highRiskRetention = auditConfig.getRetentionDaysByRiskLevel("HIGH");
            if (highRiskRetention > 0) {
                cleanupByRiskLevel("HIGH", highRiskRetention);
            }
            
            // CRITICAL级别日志默认永久保留或按配置
            int criticalRetention = auditConfig.getRetentionDaysByRiskLevel("CRITICAL");
            if (criticalRetention > 0) {
                cleanupByRiskLevel("CRITICAL", criticalRetention);
            }

            log.info("审计日志清理完成");

        } catch (Exception e) {
            log.error("审计日志清理失败", e);
        }
    }

    /**
     * 按风险等级清理
     */
    private void cleanupByRiskLevel(String riskLevel, int retentionDays) {
        if (retentionDays <= 0) {
            log.debug("风险等级 {} 的日志永久保留", riskLevel);
            return;
        }

        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        log.debug("清理风险等级 {} 的日志, 截止时间: {}", riskLevel, cutoffTime);

        auditService.cleanupExpiredLogs(retentionDays)
                .doOnSuccess(count -> log.info("风险等级 {} 清理完成, 删除 {} 条记录", riskLevel, count))
                .doOnError(e -> log.error("风险等级 {} 清理失败", riskLevel, e))
                .subscribe();
    }

    /**
     * 每小时检查存储空间
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkStorageSize() {
        long maxSizeMb = auditConfig.getMaxStorageSizeMb();
        if (maxSizeMb <= 0) {
            return;  // 未配置限制
        }

        // TODO: 实现存储空间检查逻辑
        // 如果超过限制，触发紧急清理或告警
        log.debug("检查审计日志存储空间, 最大限制: {} MB", maxSizeMb);
    }

    /**
     * 生成审计统计报告
     */
    @Scheduled(cron = "0 0 8 * * *")  // 每天早上8点
    public void generateDailyReport() {
        log.info("生成每日审计报告");

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(1);

        auditService.generateSecurityReport(startTime, endTime)
                .doOnSuccess(report -> {
                    log.info("每日审计报告生成完成: JWT操作={}, API Key操作={}, 认证失败={}, 可疑活动={}",
                            report.getTotalJwtOperations(),
                            report.getTotalApiKeyOperations(),
                            report.getFailedAuthentications(),
                            report.getSuspiciousActivities());

                    // 如果有高风险事件，记录告警
                    if (!report.getAlerts().isEmpty()) {
                        log.warn("过去24小时检测到 {} 个安全告警", report.getAlerts().size());
                    }
                })
                .doOnError(e -> log.error("生成每日审计报告失败", e))
                .subscribe();
    }

    /**
     * 检查并触发安全告警
     */
    @Scheduled(cron = "0 */5 * * * *")  // 每5分钟检查一次
    public void checkSecurityAlerts() {
        if (!auditConfig.getAlert().isEnabled()) {
            return;
        }

        log.debug("执行安全告警检查");

        // 检查认证失败阈值
        auditService.shouldTriggerAlert(
                "AUTHENTICATION_FAILED",
                auditConfig.getAlert().getAuthFailureWindowMinutes(),
                auditConfig.getAlert().getAuthFailureThreshold()
        ).doOnNext(shouldAlert -> {
            if (shouldAlert) {
                log.warn("检测到认证失败异常，可能存在安全风险");
            }
        }).subscribe();

        // 检查可疑活动
        auditService.shouldTriggerAlert(
                "SUSPICIOUS_ACTIVITY",
                10,  // 10分钟窗口
                3    // 3次可疑活动
        ).doOnNext(shouldAlert -> {
            if (shouldAlert) {
                log.warn("检测到可疑活动增加");
            }
        }).subscribe();
    }
}
package org.unreal.modelrouter.security.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.service.H2JwtBlacklistService;

/**
 * 安全数据清理调度器
 * 定期清理过期的审计日志和黑名单记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityDataCleanupScheduler {
    
    private final SecurityAuditService auditService;
    private final H2JwtBlacklistService blacklistService;
    
    @Value("${jairouter.security.audit.retention-days:30}")
    private int auditRetentionDays;
    
    /**
     * 清理过期的审计日志
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "${jairouter.security.cleanup.audit.schedule:0 0 2 * * ?}")
    public void cleanupExpiredAuditLogs() {
        log.info("开始清理过期的审计日志，保留天数: {}", auditRetentionDays);
        
        auditService.cleanupExpiredLogs(auditRetentionDays)
                .doOnSuccess(count -> log.info("审计日志清理完成，删除了 {} 条记录", count))
                .doOnError(e -> log.error("审计日志清理失败: {}", e.getMessage(), e))
                .subscribe();
    }
    
    /**
     * 清理过期的黑名单记录
     * 每小时执行一次
     */
    @Scheduled(cron = "${jairouter.security.cleanup.blacklist.schedule:0 0 * * * ?}")
    public void cleanupExpiredBlacklistTokens() {
        log.info("开始清理过期的黑名单记录");
        
        blacklistService.cleanupExpiredTokens()
                .doOnSuccess(count -> log.info("黑名单清理完成，删除了 {} 条记录", count))
                .doOnError(e -> log.error("黑名单清理失败: {}", e.getMessage(), e))
                .subscribe();
    }
}

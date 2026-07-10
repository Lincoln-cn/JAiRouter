package org.unreal.modelrouter.monitor.callhistory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;

/**
 * API 调用历史清理调度器
 * 定期清理过期的调用历史记录
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.call-history.enabled", havingValue = "true", matchIfMissing = true)
public class ApiCallHistoryCleanupScheduler {

    private final ApiCallHistoryService service;
    private final CallHistoryProperties properties;

    /**
     * 定期清理过期数据
     * 默认每天凌晨 2 点执行
     */
    @Scheduled(cron = "${jairouter.call-history.cleanup-cron:0 0 2 * * ?}")
    public void cleanup() {
        try {
            log.info("Starting API call history cleanup...");
            int deleted = service.cleanupByRetentionDays();
            log.info("API call history cleanup completed: deleted {} records (retention: {} days)",
                    deleted, properties.getRetentionDays());
        } catch (Exception e) {
            log.error("API call history cleanup failed: {}", e.getMessage(), e);
        }
    }
}

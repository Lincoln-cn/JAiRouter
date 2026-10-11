package org.unreal.modelrouter.monitor.monitoring.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.scheduling.ScheduledTaskLock;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 安全告警中「窗口聚合型」检查的定时调度（#276）。
 *
 * <p>四类检查里，认证失败波动与可疑 IP 有自然触发事件（由认证失败路径调用，
 * 见 {@code ApiKeyAuditEventListener}）；而「脱敏操作异常增长」（60 分钟窗口）与
 * 「JWT 异常活动」（10 分钟窗口）是<b>跨主体聚合的窗口计数</b>，没有自然事件：
 * 按请求调用时每次只 +1，永远到不了 100 / 20 的阈值 —— 因此必须定时检查。</p>
 *
 * <p>告警的出口是外部可观测副作用（指标 + WARN 日志，见 {@link SecurityAlertService} 的出口契约），
 * 多副本下只让一个副本检查并触发，与 {@code AuditLogCleanupTask.checkSecurityAlerts} 同一口径。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAlertScheduler {

    /** 排他键：与其它定时任务（审计清理、每日报告等）互不影响 */
    public static final String WINDOW_CHECK_LOCK_KEY = "security.alerts.window-checks";

    private final SecurityAlertService securityAlertService;
    private final ScheduledTaskLock taskLock;

    /**
     * 窗口聚合型检查：脱敏异常增长 + JWT 异常活动。
     *
     * <p>默认每 5 分钟一次（与两条检查的 60 / 10 分钟窗口匹配）。</p>
     */
    @Scheduled(fixedDelayString = "${jairouter.security.alerts.window-check-interval-ms:300000}")
    public void checkWindowAggregatedAlerts() {
        if (!taskLock.tryAcquire(WINDOW_CHECK_LOCK_KEY, Duration.ofMinutes(5))) {
            return;
        }

        securityAlertService.checkSanitizationAnomalyAlert()
                .then(securityAlertService.checkJwtAnomalyAlert())
                .onErrorResume(e -> {
                    // 定时任务不得因单次检查失败中断；失败只记日志
                    log.warn("窗口聚合安全告警检查失败: {}", e.getMessage(), e);
                    return Mono.empty();
                })
                .subscribe();
    }
}

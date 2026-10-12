package org.unreal.modelrouter.monitor.monitoring.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.scheduling.ScheduledTaskLock;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 安全告警服务
 *
 * <p><b>出口契约（#287 定案）</b>：告警的唯一出口是 <b>Micrometer 指标 + WARN 日志</b>
 * （指标 {@code jairouter.security.violations}，由 {@link SecurityMetrics#recordSecurityViolation} 记录），
 * 外部通知由 Prometheus Alertmanager / 日志平台承担。因此这里<b>不再发布仓内事件</b>——
 * 此前发布的 {@code SecurityAlertEvent} 全仓没有订阅方，只是一圈没有接收方的同步调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jairouter.security.monitoring.alerts", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SecurityAlertService {
    
    private final SecurityAuditService auditService;
    private final SecurityMetrics securityMetrics;
    private final SecurityAlertProperties properties;
    /** 冷却跨副本生效（#300）：告警是外部可观测副作用，同一类型在冷却窗口内只让一个副本触发 */
    private final ScheduledTaskLock taskLock;
    
    /** JWT 令牌相关事件类型（与 AuditEventType 枚举保持一致，勿用字符串字面量） */
    private static final List<AuditEventType> JWT_EVENT_TYPES = List.of(
            AuditEventType.JWT_TOKEN_ISSUED,
            AuditEventType.JWT_TOKEN_REFRESHED,
            AuditEventType.JWT_TOKEN_REVOKED,
            AuditEventType.JWT_TOKEN_VALIDATED,
            AuditEventType.JWT_TOKEN_EXPIRED
    );

    /** 冷却锁 key 前缀（按告警类型区分） */
    private static final String COOLDOWN_LOCK_PREFIX = "security-alert-cooldown:";
    
    /** 阈值与窗口统一来自配置面（#288），实现内不再保留类内常量 */
    private SecurityAlertProperties.Thresholds thresholds() {
        return properties.getThresholds();
    }
    
    /**
     * 检查认证失败告警
     */
    public Mono<Void> checkAuthenticationFailureAlert(
            final String clientIp, final String failureReason) {
        return auditService.shouldTriggerAlert(
                AuditEventType.AUTHENTICATION_FAILED.name(), thresholds().getAuthenticationFailureWindowMinutes(),
                thresholds().getAuthenticationFailureCount())
                .flatMap(shouldAlert -> {
                    if (shouldAlert) {
                        return triggerAlert(
                                "AUTH_FAILURE_SPIKE",
                                "认证失败次数异常",
                                String.format("在过去%d分钟内检测到%d次以上的认证失败", 
                                        thresholds().getAuthenticationFailureWindowMinutes(), thresholds().getAuthenticationFailureCount()),
                                "HIGH",
                                createAuthFailureAlertData(clientIp, failureReason)
                        );
                    }
                    return Mono.empty();
                })
                .then();
    }
    
    /**
     * 检查可疑IP活动告警
     */
    public Mono<Void> checkSuspiciousIpAlert(final String clientIp) {
        return auditService.countEvents(
                LocalDateTime.now().minusMinutes(thresholds().getSuspiciousIpWindowMinutes()),
                LocalDateTime.now(),
                List.of(AuditEventType.AUTHENTICATION_FAILED),
                clientIp,
                null
        )
                .flatMap(failureCount -> {
                    if (failureCount >= thresholds().getSuspiciousIpCount()) {
                        return triggerAlert(
                                "SUSPICIOUS_IP_ACTIVITY",
                                "可疑IP活动检测",
                                String.format("IP地址 %s 在过去%d分钟内产生了%d次认证失败", 
                                        maskIp(clientIp), thresholds().getSuspiciousIpWindowMinutes(), failureCount),
                                "HIGH",
                                createSuspiciousIpAlertData(clientIp, failureCount)
                        );
                    }
                    return Mono.empty();
                })
                .then();
    }
    
    /**
     * 检查数据脱敏异常告警
     */
    public Mono<Void> checkSanitizationAnomalyAlert() {
        return auditService.countEvents(
                LocalDateTime.now().minusMinutes(thresholds().getSanitizationWindowMinutes()),
                LocalDateTime.now(),
                List.of(AuditEventType.DATA_SANITIZATION),
                null,
                null
        )
                .flatMap(sanitizationCount -> {
                    if (sanitizationCount >= thresholds().getSanitizationCount()) {
                        return triggerAlert(
                                "SANITIZATION_ANOMALY",
                                "数据脱敏操作异常增长",
                                String.format("在过去%d分钟内检测到%d次脱敏操作，可能存在异常", 
                                        thresholds().getSanitizationWindowMinutes(), sanitizationCount),
                                "MEDIUM",
                                createSanitizationAnomalyAlertData(sanitizationCount)
                        );
                    }
                    return Mono.empty();
                })
                .then();
    }
    
    /**
     * 检查JWT令牌异常告警
     */
    public Mono<Void> checkJwtAnomalyAlert() {
        return auditService.countEvents(
                LocalDateTime.now().minusMinutes(thresholds().getJwtFailureWindowMinutes()),
                LocalDateTime.now(),
                JWT_EVENT_TYPES,
                null,
                false
        )
                .flatMap(jwtFailureCount -> {
                    if (jwtFailureCount >= thresholds().getJwtFailureCount()) {
                        return triggerAlert(
                                "JWT_ANOMALY",
                                "JWT令牌异常活动",
                                String.format("在过去%d分钟内检测到%d次JWT相关失败",
                                        thresholds().getJwtFailureWindowMinutes(), jwtFailureCount),
                                "MEDIUM",
                                createJwtAnomalyAlertData(jwtFailureCount)
                        );
                    }
                    return Mono.empty();
                })
                .then();
    }
    
    /**
     * 触发安全告警
     */
    private Mono<Void> triggerAlert(final String alertType, final String title, final String description, 
                                   final String severity, final Map<String, Object> alertData) {
        // 冷却（#300）：跨副本生效 —— 同一告警类型在冷却窗口内只让一个副本触发，
        // 否则指标、WARN 日志与 SECURITY_ALERT 审计事件会被 N 个副本各写一遍（概览的告警数随之虚高 N 倍）。
        // 锁由 ScheduledTaskLock 提供：Redis 未配置/不可用时 fail-open（等价于单副本语义，与引入前一致）。
        // 注意其租约取传入周期的 90%，因此实际抑制窗口略短于配置的 cooldown-minutes。
        if (!taskLock.tryAcquire(COOLDOWN_LOCK_PREFIX + alertType,
                Duration.ofMinutes(thresholds().getCooldownMinutes()))) {
            log.debug("告警 {} 在冷却窗口内（本副本先前已触发或其他副本正在处理），跳过", alertType);
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            // 出口 = 指标 + 日志（#287 契约）：明细随日志进日志平台，计数进 Prometheus
            securityMetrics.recordSecurityViolation(alertType, severity);
            log.warn("安全告警触发: type={}, title={}, severity={}, description={}, data={}",
                    alertType, title, severity, description, alertData);

        })
                // 另落一条 SECURITY_ALERT 审计事件：告警本身成为可查询记录（#291 的「近 24h 告警数」即由此计数）
                .then(auditService.recordEvent(buildAlertEvent(alertType, title, description, severity, alertData)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 近 24 小时触发的安全告警条数（数据源 = 告警落下的 {@code SECURITY_ALERT} 审计事件）。
     *
     * <p>在存储侧计数，因此不受「一次取回多少条」限制，多副本看到的也是同一个数。</p>
     */
    public Mono<Long> countAlertsLast24Hours() {
        return auditService.countEvents(LocalDateTime.now().minusHours(24), LocalDateTime.now(),
                List.of(AuditEventType.SECURITY_ALERT), null, null);
    }

    private static SecurityAuditEvent buildAlertEvent(final String alertType, final String title,
                                                      final String description, final String severity,
                                                      final Map<String, Object> alertData) {
        final Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", description);
        data.put("severity", severity);
        if (alertData != null) {
            data.putAll(alertData);
        }

        return SecurityAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(AuditEventType.SECURITY_ALERT.name())
                .action(alertType)
                .success(true)
                .timestamp(LocalDateTime.now())
                .additionalData(data)
                .build();
    }
    
    /**
     * 创建认证失败告警数据
     */
    private Map<String, Object> createAuthFailureAlertData(final String clientIp, final String failureReason) {
        Map<String, Object> data = new HashMap<>();
        data.put("clientIp", maskIp(clientIp));
        data.put("failureReason", failureReason);
        data.put("timeWindow", thresholds().getAuthenticationFailureWindowMinutes() + " minutes");
        data.put("threshold", thresholds().getAuthenticationFailureCount());
        return data;
    }
    
    /**
     * 创建可疑IP告警数据
     */
    private Map<String, Object> createSuspiciousIpAlertData(final String clientIp, final long failureCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("clientIp", maskIp(clientIp));
        data.put("failureCount", failureCount);
        data.put("timeWindow", thresholds().getSuspiciousIpWindowMinutes() + " minutes");
        data.put("threshold", thresholds().getSuspiciousIpCount());
        return data;
    }
    
    /**
     * 创建脱敏异常告警数据
     */
    private Map<String, Object> createSanitizationAnomalyAlertData(final long sanitizationCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("sanitizationCount", sanitizationCount);
        data.put("timeWindow", thresholds().getSanitizationWindowMinutes() + " minutes");
        data.put("threshold", thresholds().getSanitizationCount());
        return data;
    }
    
    /**
     * 创建JWT异常告警数据
     */
    private Map<String, Object> createJwtAnomalyAlertData(final long jwtFailureCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("jwtFailureCount", jwtFailureCount);
        data.put("timeWindow", thresholds().getJwtFailureWindowMinutes() + " minutes");
        data.put("threshold", thresholds().getJwtFailureCount());
        return data;
    }
    
    /**
     * 掩码IP地址（保护隐私）
     */
    private String maskIp(final String ip) {
        if (ip == null) {
            return "unknown";
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + parts[3];
        }
        
        // 对于IPv6或其他格式，简单处理
        if (ip.length() > 8) {
            return ip.substring(0, 4) + "****" + ip.substring(ip.length() - 4);
        }
        
        return "****";
    }
}
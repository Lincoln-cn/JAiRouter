package org.unreal.modelrouter.security.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.model.SecurityAlertEvent;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全告警服务
 * 实现安全事件的实时告警机制
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAlertService {
    
    private final SecurityAuditService auditService;
    private final SecurityMetrics securityMetrics;
    private final ApplicationEventPublisher eventPublisher;
    
    // 告警阈值配置
    private static final int AUTH_FAILURE_THRESHOLD = 5; // 5分钟内认证失败次数
    private static final int AUTH_FAILURE_WINDOW_MINUTES = 5;
    
    private static final int SUSPICIOUS_IP_THRESHOLD = 10; // 10分钟内同一IP的失败次数
    private static final int SUSPICIOUS_IP_WINDOW_MINUTES = 10;
    
    private static final int SANITIZATION_ANOMALY_THRESHOLD = 100; // 1小时内脱敏操作异常增长
    private static final int SANITIZATION_ANOMALY_WINDOW_MINUTES = 60;
    
    // 告警状态跟踪
    private final ConcurrentHashMap<String, LocalDateTime> lastAlertTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> alertCounts = new ConcurrentHashMap<>();
    
    // 告警冷却时间（分钟）
    private static final int ALERT_COOLDOWN_MINUTES = 15;
    
    /**
     * 检查认证失败告警
     */
    public Mono<Void> checkAuthenticationFailureAlert(String clientIp, String failureReason) {
        return auditService.shouldTriggerAlert("AUTHENTICATION_FAILURE", AUTH_FAILURE_WINDOW_MINUTES, AUTH_FAILURE_THRESHOLD)
                .flatMap(shouldAlert -> {
                    if (shouldAlert) {
                        return triggerAlert(
                                "AUTH_FAILURE_SPIKE",
                                "认证失败次数异常",
                                String.format("在过去%d分钟内检测到%d次以上的认证失败", 
                                        AUTH_FAILURE_WINDOW_MINUTES, AUTH_FAILURE_THRESHOLD),
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
    public Mono<Void> checkSuspiciousIpAlert(String clientIp) {
        String alertKey = "SUSPICIOUS_IP_" + clientIp;
        
        return auditService.queryEvents(
                LocalDateTime.now().minusMinutes(SUSPICIOUS_IP_WINDOW_MINUTES),
                LocalDateTime.now(),
                "AUTHENTICATION_FAILURE",
                null,
                100
        )
                .filter(event -> clientIp.equals(event.getClientIp()))
                .count()
                .flatMap(failureCount -> {
                    if (failureCount >= SUSPICIOUS_IP_THRESHOLD) {
                        return triggerAlert(
                                "SUSPICIOUS_IP_ACTIVITY",
                                "可疑IP活动检测",
                                String.format("IP地址 %s 在过去%d分钟内产生了%d次认证失败", 
                                        maskIp(clientIp), SUSPICIOUS_IP_WINDOW_MINUTES, failureCount),
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
        return auditService.queryEvents(
                LocalDateTime.now().minusMinutes(SANITIZATION_ANOMALY_WINDOW_MINUTES),
                LocalDateTime.now(),
                "DATA_SANITIZATION",
                null,
                1000
        )
                .count()
                .flatMap(sanitizationCount -> {
                    if (sanitizationCount >= SANITIZATION_ANOMALY_THRESHOLD) {
                        return triggerAlert(
                                "SANITIZATION_ANOMALY",
                                "数据脱敏操作异常增长",
                                String.format("在过去%d分钟内检测到%d次脱敏操作，可能存在异常", 
                                        SANITIZATION_ANOMALY_WINDOW_MINUTES, sanitizationCount),
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
        return auditService.queryEvents(
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                null,
                null,
                100
        )
                .filter(event -> event.getEventType().contains("JWT") && !event.isSuccess())
                .count()
                .flatMap(jwtFailureCount -> {
                    if (jwtFailureCount >= 20) { // 10分钟内20次JWT失败
                        return triggerAlert(
                                "JWT_ANOMALY",
                                "JWT令牌异常活动",
                                String.format("在过去10分钟内检测到%d次JWT相关失败", jwtFailureCount),
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
    private Mono<Void> triggerAlert(String alertType, String title, String description, 
                                   String severity, Map<String, Object> alertData) {
        String alertKey = alertType;
        LocalDateTime now = LocalDateTime.now();
        
        // 检查告警冷却时间
        LocalDateTime lastAlertTime = lastAlertTimes.get(alertKey);
        if (lastAlertTime != null && 
            lastAlertTime.plusMinutes(ALERT_COOLDOWN_MINUTES).isAfter(now)) {
            log.debug("告警 {} 在冷却期内，跳过触发", alertType);
            return Mono.empty();
        }
        
        return Mono.fromRunnable(() -> {
            // 更新告警状态
            lastAlertTimes.put(alertKey, now);
            alertCounts.computeIfAbsent(alertKey, k -> new AtomicLong(0)).incrementAndGet();
            
            // 创建告警事件
            SecurityAlertEvent alertEvent = SecurityAlertEvent.builder()
                    .alertId(java.util.UUID.randomUUID().toString())
                    .alertType(alertType)
                    .title(title)
                    .description(description)
                    .severity(severity)
                    .timestamp(now)
                    .alertData(alertData)
                    .build();
            
            // 发布告警事件
            eventPublisher.publishEvent(alertEvent);
            
            // 记录安全违规指标
            securityMetrics.recordSecurityViolation(alertType, severity);
            
            // 记录告警日志
            log.warn("安全告警触发: type={}, title={}, severity={}, description={}", 
                    alertType, title, severity, description);
            
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    /**
     * 创建认证失败告警数据
     */
    private Map<String, Object> createAuthFailureAlertData(String clientIp, String failureReason) {
        Map<String, Object> data = new HashMap<>();
        data.put("clientIp", maskIp(clientIp));
        data.put("failureReason", failureReason);
        data.put("timeWindow", AUTH_FAILURE_WINDOW_MINUTES + " minutes");
        data.put("threshold", AUTH_FAILURE_THRESHOLD);
        return data;
    }
    
    /**
     * 创建可疑IP告警数据
     */
    private Map<String, Object> createSuspiciousIpAlertData(String clientIp, long failureCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("clientIp", maskIp(clientIp));
        data.put("failureCount", failureCount);
        data.put("timeWindow", SUSPICIOUS_IP_WINDOW_MINUTES + " minutes");
        data.put("threshold", SUSPICIOUS_IP_THRESHOLD);
        return data;
    }
    
    /**
     * 创建脱敏异常告警数据
     */
    private Map<String, Object> createSanitizationAnomalyAlertData(long sanitizationCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("sanitizationCount", sanitizationCount);
        data.put("timeWindow", SANITIZATION_ANOMALY_WINDOW_MINUTES + " minutes");
        data.put("threshold", SANITIZATION_ANOMALY_THRESHOLD);
        return data;
    }
    
    /**
     * 创建JWT异常告警数据
     */
    private Map<String, Object> createJwtAnomalyAlertData(long jwtFailureCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("jwtFailureCount", jwtFailureCount);
        data.put("timeWindow", "10 minutes");
        data.put("threshold", 20);
        return data;
    }
    
    /**
     * 掩码IP地址（保护隐私）
     */
    private String maskIp(String ip) {
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
    
    /**
     * 获取告警统计信息
     */
    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 告警计数统计
        Map<String, Long> alertCounts = new HashMap<>();
        this.alertCounts.forEach((key, value) -> alertCounts.put(key, value.get()));
        stats.put("alertCounts", alertCounts);
        
        // 最近告警时间
        Map<String, LocalDateTime> lastAlerts = new HashMap<>(lastAlertTimes);
        stats.put("lastAlertTimes", lastAlerts);
        
        // 活跃告警数（过去24小时内的告警）
        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        long activeAlerts = lastAlertTimes.values().stream()
                .filter(time -> time.isAfter(yesterday))
                .count();
        stats.put("activeAlertsLast24Hours", activeAlerts);
        
        return stats;
    }
    
    /**
     * 重置告警统计
     */
    public void resetAlertStatistics() {
        alertCounts.clear();
        lastAlertTimes.clear();
        log.info("告警统计已重置");
    }
}
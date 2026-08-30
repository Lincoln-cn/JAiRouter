package org.unreal.modelrouter.auth.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.dto.SecurityAlert;
import org.unreal.modelrouter.common.dto.SecurityReport;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityAuditEventRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全报告生成器
 * 负责生成安全报告、统计信息、清理过期日志和告警判断
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityReportGenerator {

    private final SecurityAuditEventRepository auditRepository;
    private final AuditEntityMapper entityMapper;

    // JWT令牌相关事件类型
    private static final List<AuditEventType> JWT_EVENT_TYPES = Arrays.asList(
            AuditEventType.JWT_TOKEN_ISSUED,
            AuditEventType.JWT_TOKEN_REFRESHED,
            AuditEventType.JWT_TOKEN_REVOKED,
            AuditEventType.JWT_TOKEN_VALIDATED,
            AuditEventType.JWT_TOKEN_EXPIRED
    );

    // API Key相关事件类型
    private static final List<AuditEventType> API_KEY_EVENT_TYPES = Arrays.asList(
            AuditEventType.API_KEY_CREATED,
            AuditEventType.API_KEY_USED,
            AuditEventType.API_KEY_REVOKED,
            AuditEventType.API_KEY_EXPIRED,
            AuditEventType.API_KEY_UPDATED
    );

    public Mono<SecurityReport> generateSecurityReport(final LocalDateTime from, final LocalDateTime to) {
        return Mono.fromCallable(() -> {
            // 按事件类型统计
            List<Object[]> eventTypeStats = auditRepository.countByEventType(from, to);
            Map<String, Long> operationsByType = new HashMap<>();
            long jwtOps = 0;
            long apiKeyOps = 0;
            for (Object[] stat : eventTypeStats) {
                AuditEventType type = (AuditEventType) stat[0];
                Long count = (Long) stat[1];
                operationsByType.put(type.name(), count);
                if (JWT_EVENT_TYPES.contains(type)) {
                    jwtOps += count;
                }
                if (API_KEY_EVENT_TYPES.contains(type)) {
                    apiKeyOps += count;
                }
            }

            // 按用户统计
            List<Object[]> userStats = auditRepository.countByUserId(from, to, 10);
            Map<String, Long> operationsByUser = new HashMap<>();
            for (Object[] stat : userStats) {
                String userId = (String) stat[0];
                Long count = (Long) stat[1];
                operationsByUser.put(userId, count);
            }

            // Top IP地址
            List<Object[]> ipStats = auditRepository.countByClientIp(from, to, 10);
            List<String> topIpAddresses = new ArrayList<>();
            for (Object[] stat : ipStats) {
                topIpAddresses.add((String) stat[0]);
            }

            // 失败认证和可疑活动
            long failedAuth = auditRepository.countFailedEventsInTimeWindow(
                    Arrays.asList(AuditEventType.AUTHENTICATION_FAILED, AuditEventType.AUTHORIZATION_FAILED),
                    from, to
            );
            long suspiciousActivities = auditRepository.countEventsInTimeWindow(
                    AuditEventType.SUSPICIOUS_ACTIVITY, from, to
            );

            // 高风险事件作为告警
            List<SecurityAuditEventEntity> highRiskEvents = auditRepository.findHighRiskEvents(
                    Arrays.asList(RiskLevel.HIGH, RiskLevel.CRITICAL),
                    from
            );
            List<SecurityAlert> alerts = highRiskEvents.stream()
                    .map(e -> new SecurityAlert(
                            e.getEventType().name(),
                            e.getDetails(),
                            e.getUserId(),
                            e.getClientIp(),
                            e.getTimestamp()
                    ))
                    .collect(Collectors.toList());

            return new SecurityReport(
                    from, to,
                    jwtOps, apiKeyOps,
                    failedAuth, suspiciousActivities,
                    operationsByType, operationsByUser,
                    topIpAddresses, alerts
            );
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Map<String, Object>> getSecurityStatistics(final LocalDateTime startTime, final LocalDateTime endTime) {
        return Mono.fromCallable(() -> {
            Map<String, Object> statistics = new HashMap<>();

            // 总事件数
            long totalEvents = auditRepository.countByTimeRange(startTime, endTime);
            statistics.put("totalEvents", totalEvents);

            // 按事件类型统计
            List<Object[]> eventTypeStats = auditRepository.countByEventType(startTime, endTime);
            Map<String, Long> eventTypeMap = new HashMap<>();
            for (Object[] stat : eventTypeStats) {
                eventTypeMap.put(((AuditEventType) stat[0]).name(), (Long) stat[1]);
            }
            statistics.put("eventTypeStatistics", eventTypeMap);

            // 成功/失败统计
            long successCount = auditRepository.countSuccessEvents(startTime, endTime);
            long failureCount = auditRepository.countFailedEvents(startTime, endTime);
            statistics.put("successCount", successCount);
            statistics.put("failureCount", failureCount);

            // 按分类统计
            List<Object[]> categoryStats = auditRepository.countByEventCategory(startTime, endTime);
            Map<String, Long> categoryMap = new HashMap<>();
            for (Object[] stat : categoryStats) {
                categoryMap.put((String) stat[0], (Long) stat[1]);
            }
            statistics.put("categoryStatistics", categoryMap);

            return statistics;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Long> cleanupExpiredLogs(final int retentionDays) {
        return Mono.fromCallable(() -> {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            // 只删除低风险和中风险的过期日志，保留高风险和严重风险的日志
            int deletedCount = auditRepository.deleteLowRiskEventsBefore(
                    cutoffTime,
                    Arrays.asList(RiskLevel.LOW, RiskLevel.MEDIUM)
            );
            log.info("清理过期审计日志完成: deletedCount={}, retentionDays={}", deletedCount, retentionDays);
            return (long) deletedCount;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> shouldTriggerAlert(final String eventType, final int timeWindowMinutes, final int threshold) {
        return Mono.fromCallable(() -> {
            LocalDateTime windowEnd = LocalDateTime.now();
            LocalDateTime windowStart = windowEnd.minusMinutes(timeWindowMinutes);
            AuditEventType type = entityMapper.parseEventType(eventType);
            long count = auditRepository.countEventsInTimeWindow(type, windowStart, windowEnd);
            boolean shouldAlert = count >= threshold;
            if (shouldAlert) {
                log.warn("安全告警触发: eventType={}, timeWindow={}分钟, threshold={}, actualCount={}",
                        eventType, timeWindowMinutes, threshold, count);
            }
            return shouldAlert;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
}

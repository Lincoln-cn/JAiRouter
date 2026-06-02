package org.unreal.modelrouter.auth.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.common.dto.AuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventQuery;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.dto.SecurityAlert;
import org.unreal.modelrouter.common.dto.SecurityReport;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityAuditEventRepository;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 扩展安全审计服务实现（JPA版本）
 * 实现真实的审计日志存储和查询功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtendedSecurityAuditServiceImpl implements ExtendedSecurityAuditService {

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

    // 安全事件类型
    private static final List<AuditEventType> SECURITY_EVENT_TYPES = Arrays.asList(
            AuditEventType.SECURITY_ALERT,
            AuditEventType.SUSPICIOUS_ACTIVITY,
            AuditEventType.AUTHENTICATION_FAILED,
            AuditEventType.AUTHORIZATION_FAILED
    );

    // ========== JWT令牌审计方法 ==========

    @Override
    @Transactional
    public Mono<Void> auditTokenIssued(final String userId, final String tokenId, final String ipAddress, final String userAgent) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.JWT_TOKEN_ISSUED)
                    .userId(userId)
                    .resourceId(tokenId)
                    .clientIp(ipAddress)
                    .userAgent(userAgent)
                    .action("ISSUE")
                    .details("JWT令牌颁发成功")
                    .success(true)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("JWT令牌颁发审计记录: userId={}, tokenId={}", userId, tokenId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditTokenRefreshed(final String userId, final String oldTokenId, final String newTokenId, final String ipAddress) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("oldTokenId", oldTokenId);
            metadata.put("newTokenId", newTokenId);

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.JWT_TOKEN_REFRESHED)
                    .userId(userId)
                    .resourceId(newTokenId)
                    .clientIp(ipAddress)
                    .action("REFRESH")
                    .details("JWT令牌刷新成功")
                    .success(true)
                    .metadata(entityMapper.toJson(metadata))
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("JWT令牌刷新审计记录: userId={}", userId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditTokenRevoked(final String userId, final String tokenId, final String reason, final String revokedBy) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("revokedBy", revokedBy);
            metadata.put("reason", reason);

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.JWT_TOKEN_REVOKED)
                    .userId(userId)
                    .resourceId(tokenId)
                    .action("REVOKE")
                    .details("JWT令牌撤销: " + reason)
                    .success(true)
                    .metadata(entityMapper.toJson(metadata))
                    .riskLevel(RiskLevel.MEDIUM)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("JWT令牌撤销审计记录: userId={}, tokenId={}, reason={}", userId, tokenId, reason);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditTokenValidated(final String userId, final String tokenId, final boolean isValid, final String ipAddress) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.JWT_TOKEN_VALIDATED)
                    .userId(userId)
                    .resourceId(tokenId)
                    .clientIp(ipAddress)
                    .action("VALIDATE")
                    .details(isValid ? "JWT令牌验证成功" : "JWT令牌验证失败")
                    .success(isValid)
                    .failureReason(isValid ? null : "令牌无效或已过期")
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.debug("JWT令牌验证审计记录: userId={}, valid={}", userId, isValid);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ========== API Key审计方法 ==========

    @Override
    @Transactional
    public Mono<Void> auditApiKeyCreated(final String keyId, final String createdBy, final String ipAddress) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.API_KEY_CREATED)
                    .userId(createdBy)
                    .resourceId(keyId)
                    .clientIp(ipAddress)
                    .action("CREATE")
                    .details("API Key创建成功")
                    .success(true)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("API Key创建审计记录: keyId={}, createdBy={}", keyId, createdBy);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditApiKeyUsed(final String keyId, final String endpoint, final String ipAddress, final boolean success) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("endpoint", endpoint);

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.API_KEY_USED)
                    .resourceId(keyId)
                    .clientIp(ipAddress)
                    .resource(endpoint)
                    .action("USE")
                    .details(success ? "API Key使用成功" : "API Key使用失败")
                    .success(success)
                    .failureReason(success ? null : "认证失败或权限不足")
                    .metadata(entityMapper.toJson(metadata))
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.debug("API Key使用审计记录: keyId={}, endpoint={}, success={}", keyId, endpoint, success);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditApiKeyRevoked(final String keyId, final String reason, final String revokedBy) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("revokedBy", revokedBy);
            metadata.put("reason", reason);

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.API_KEY_REVOKED)
                    .userId(revokedBy)
                    .resourceId(keyId)
                    .action("REVOKE")
                    .details("API Key撤销: " + reason)
                    .success(true)
                    .metadata(entityMapper.toJson(metadata))
                    .riskLevel(RiskLevel.MEDIUM)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("API Key撤销审计记录: keyId={}, reason={}", keyId, reason);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditApiKeyExpired(final String keyId) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.API_KEY_EXPIRED)
                    .resourceId(keyId)
                    .action("EXPIRE")
                    .details("API Key已过期")
                    .success(true)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("API Key过期审计记录: keyId={}", keyId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ========== 安全事件审计方法 ==========

    @Override
    @Transactional
    public Mono<Void> auditSecurityEvent(final String eventType, final String details, final String userId, final String ipAddress) {
        return Mono.fromRunnable(() -> {
            AuditEventType type = entityMapper.parseEventType(eventType);
            RiskLevel riskLevel = entityMapper.determineRiskLevel(type, false);

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(type)
                    .userId(userId)
                    .clientIp(ipAddress)
                    .action("SECURITY_EVENT")
                    .details(details)
                    .success(false)
                    .riskLevel(riskLevel)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.warn("安全事件审计记录: type={}, userId={}, details={}", eventType, userId, details);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditSuspiciousActivity(final String activity, final String userId, final String ipAddress, final String details) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("activity", activity);
            metadata.put("details", details);

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.SUSPICIOUS_ACTIVITY)
                    .userId(userId)
                    .clientIp(ipAddress)
                    .action("SUSPICIOUS")
                    .details(activity)
                    .success(false)
                    .metadata(entityMapper.toJson(metadata))
                    .riskLevel(RiskLevel.HIGH)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.warn("可疑活动审计记录: activity={}, userId={}, ip={}", activity, userId, ipAddress);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ========== 查询方法 ==========

    @Override
    public Flux<AuditEvent> findAuditEvents(final AuditEventQuery query) {
        return Mono.fromCallable(() -> {
            PageRequest pageRequest = PageRequest.of(
                    query.getPage(),
                    Math.min(query.getSize(), 100),
                    Sort.by(Sort.Direction.fromString(query.getSortDirection()), query.getSortBy())
            );

            Page<SecurityAuditEventEntity> page = auditRepository.findByConditions(
                    query.getStartTime(),
                    query.getEndTime(),
                    query.getEventTypes(),
                    query.getUserId(),
                    query.getResourceId(),
                    query.getIpAddress(),
                    query.getSuccess(),
                    null,
                    null,
                    pageRequest
            );

            return page.getContent().stream()
                    .map(entityMapper::entityToDto)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Long> countAuditEvents(final AuditEventQuery query) {
        return Mono.fromCallable(() -> {
            return auditRepository.countByConditions(
                    query.getStartTime(),
                    query.getEndTime(),
                    query.getEventTypes(),
                    query.getUserId(),
                    query.getResourceId(),
                    query.getIpAddress(),
                    query.getSuccess(),
                    null,
                    null
            );
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
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

    @Override
    @Transactional
    public Mono<Void> recordAuditEvent(final AuditEvent auditEvent) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = entityMapper.dtoToEntity(auditEvent);
            auditRepository.save(entity);
            log.debug("审计事件记录: type={}, userId={}", auditEvent.getType(), auditEvent.getUserId());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> batchRecordAuditEvents(final List<AuditEvent> auditEvents) {
        return Mono.fromRunnable(() -> {
            List<SecurityAuditEventEntity> entities = auditEvents.stream()
                    .map(entityMapper::dtoToEntity)
                    .collect(Collectors.toList());
            auditRepository.saveAll(entities);
            log.info("批量审计事件记录: count={}", auditEvents.size());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Flux<AuditEvent> getUserAuditEvents(final String userId, final LocalDateTime startTime, final LocalDateTime endTime, final int limit) {
        return Mono.fromCallable(() -> {
            List<SecurityAuditEventEntity> entities = auditRepository.findByTimeRange(startTime, endTime);
            return entities.stream()
                    .filter(e -> userId.equals(e.getUserId()))
                    .limit(limit)
                    .map(entityMapper::entityToDto)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<AuditEvent> getIpAuditEvents(final String ipAddress, final LocalDateTime startTime, final LocalDateTime endTime, final int limit) {
        return Mono.fromCallable(() -> {
            List<SecurityAuditEventEntity> entities = auditRepository.findByTimeRange(startTime, endTime);
            return entities.stream()
                    .filter(e -> ipAddress.equals(e.getClientIp()))
                    .limit(limit)
                    .map(entityMapper::entityToDto)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    // ========== SecurityAuditService方法 ==========

    @Override
    @Transactional
    public Mono<Void> recordEvent(final SecurityAuditEvent event) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = entityMapper.securityEventToEntity(event);
            auditRepository.save(entity);
            log.debug("安全审计事件记录: eventType={}", event.getEventType());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> recordAuthenticationEvent(final String userId, final String clientIp, final String userAgent,
                                               final boolean success, final String failureReason) {
        return Mono.fromRunnable(() -> {
            AuditEventType eventType = success 
            ? AuditEventType.JWT_TOKEN_ISSUED : AuditEventType.AUTHENTICATION_FAILED;

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .userId(userId)
                    .clientIp(clientIp)
                    .userAgent(userAgent)
                    .action("AUTHENTICATE")
                    .details(success ? "认证成功" : "认证失败")
                    .success(success)
                    .failureReason(failureReason)
                    .riskLevel(success ? RiskLevel.LOW : RiskLevel.MEDIUM)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> recordSanitizationEvent(final String userId, final String contentType, final String ruleId, final int matchCount) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("contentType", contentType);
            metadata.put("ruleId", ruleId);
            metadata.put("matchCount", matchCount);

            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.SYSTEM_MAINTENANCE)
                    .userId(userId)
                    .action("SANITIZE")
                    .details("数据脱敏处理")
                    .success(true)
                    .metadata(entityMapper.toJson(metadata))
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Flux<SecurityAuditEvent> queryEvents(final LocalDateTime startTime, final LocalDateTime endTime,
                                               final String eventType, final String userId, final int limit) {
        return Mono.fromCallable(() -> {
            List<SecurityAuditEventEntity> entities = auditRepository.findByTimeRange(startTime, endTime);
            return entities.stream()
                    .filter(e -> eventType == null || eventType.equals(e.getEventType().name()))
                    .filter(e -> userId == null || userId.equals(e.getUserId()))
                    .limit(limit)
                    .map(entityMapper::entityToSecurityEvent)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    @Override
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

    @Override
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

    @Override
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
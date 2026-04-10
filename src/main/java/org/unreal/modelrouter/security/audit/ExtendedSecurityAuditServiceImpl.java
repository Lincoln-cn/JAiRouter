package org.unreal.modelrouter.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.jpa.entity.SecurityAuditEventEntity.RiskLevel;
import org.unreal.modelrouter.jpa.repository.SecurityAuditEventRepository;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
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
    private final ObjectMapper objectMapper;

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
    public Mono<Void> auditTokenIssued(String userId, String tokenId, String ipAddress, String userAgent) {
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
    public Mono<Void> auditTokenRefreshed(String userId, String oldTokenId, String newTokenId, String ipAddress) {
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
                    .metadata(toJson(metadata))
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("JWT令牌刷新审计记录: userId={}", userId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditTokenRevoked(String userId, String tokenId, String reason, String revokedBy) {
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
                    .metadata(toJson(metadata))
                    .riskLevel(RiskLevel.MEDIUM)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("JWT令牌撤销审计记录: userId={}, tokenId={}, reason={}", userId, tokenId, reason);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditTokenValidated(String userId, String tokenId, boolean isValid, String ipAddress) {
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
    public Mono<Void> auditApiKeyCreated(String keyId, String createdBy, String ipAddress) {
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
    public Mono<Void> auditApiKeyUsed(String keyId, String endpoint, String ipAddress, boolean success) {
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
                    .metadata(toJson(metadata))
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.debug("API Key使用审计记录: keyId={}, endpoint={}, success={}", keyId, endpoint, success);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditApiKeyRevoked(String keyId, String reason, String revokedBy) {
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
                    .metadata(toJson(metadata))
                    .riskLevel(RiskLevel.MEDIUM)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.info("API Key撤销审计记录: keyId={}, reason={}", keyId, reason);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> auditApiKeyExpired(String keyId) {
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
    public Mono<Void> auditSecurityEvent(String eventType, String details, String userId, String ipAddress) {
        return Mono.fromRunnable(() -> {
            AuditEventType type = parseEventType(eventType);
            RiskLevel riskLevel = determineRiskLevel(type, false);

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
    public Mono<Void> auditSuspiciousActivity(String activity, String userId, String ipAddress, String details) {
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
                    .metadata(toJson(metadata))
                    .riskLevel(RiskLevel.HIGH)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
            log.warn("可疑活动审计记录: activity={}, userId={}, ip={}", activity, userId, ipAddress);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ========== 查询方法 ==========

    @Override
    public Flux<AuditEvent> findAuditEvents(AuditEventQuery query) {
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
                    .map(this::entityToDto)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Long> countAuditEvents(AuditEventQuery query) {
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
    public Mono<SecurityReport> generateSecurityReport(LocalDateTime from, LocalDateTime to) {
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
    public Mono<Void> recordAuditEvent(AuditEvent auditEvent) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = dtoToEntity(auditEvent);
            auditRepository.save(entity);
            log.debug("审计事件记录: type={}, userId={}", auditEvent.getType(), auditEvent.getUserId());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> batchRecordAuditEvents(List<AuditEvent> auditEvents) {
        return Mono.fromRunnable(() -> {
            List<SecurityAuditEventEntity> entities = auditEvents.stream()
                    .map(this::dtoToEntity)
                    .collect(Collectors.toList());
            auditRepository.saveAll(entities);
            log.info("批量审计事件记录: count={}", auditEvents.size());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Flux<AuditEvent> getUserAuditEvents(String userId, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return Mono.fromCallable(() -> {
            List<SecurityAuditEventEntity> entities = auditRepository.findByTimeRange(startTime, endTime);
            return entities.stream()
                    .filter(e -> userId.equals(e.getUserId()))
                    .limit(limit)
                    .map(this::entityToDto)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<AuditEvent> getIpAuditEvents(String ipAddress, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return Mono.fromCallable(() -> {
            List<SecurityAuditEventEntity> entities = auditRepository.findByTimeRange(startTime, endTime);
            return entities.stream()
                    .filter(e -> ipAddress.equals(e.getClientIp()))
                    .limit(limit)
                    .map(this::entityToDto)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    // ========== SecurityAuditService方法 ==========

    @Override
    @Transactional
    public Mono<Void> recordEvent(SecurityAuditEvent event) {
        return Mono.fromRunnable(() -> {
            SecurityAuditEventEntity entity = securityEventToEntity(event);
            auditRepository.save(entity);
            log.debug("安全审计事件记录: eventType={}", event.getEventType());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @Transactional
    public Mono<Void> recordAuthenticationEvent(String userId, String clientIp, String userAgent,
                                               boolean success, String failureReason) {
        return Mono.fromRunnable(() -> {
            AuditEventType eventType = success ? 
                    AuditEventType.JWT_TOKEN_ISSUED : AuditEventType.AUTHENTICATION_FAILED;

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
    public Mono<Void> recordSanitizationEvent(String userId, String contentType, String ruleId, int matchCount) {
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
                    .metadata(toJson(metadata))
                    .timestamp(LocalDateTime.now())
                    .build();
            auditRepository.save(entity);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Flux<SecurityAuditEvent> queryEvents(LocalDateTime startTime, LocalDateTime endTime,
                                               String eventType, String userId, int limit) {
        return Mono.fromCallable(() -> {
            List<SecurityAuditEventEntity> entities = auditRepository.findByTimeRange(startTime, endTime);
            return entities.stream()
                    .filter(e -> eventType == null || eventType.equals(e.getEventType().name()))
                    .filter(e -> userId == null || userId.equals(e.getUserId()))
                    .limit(limit)
                    .map(this::entityToSecurityEvent)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Map<String, Object>> getSecurityStatistics(LocalDateTime startTime, LocalDateTime endTime) {
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
    public Mono<Long> cleanupExpiredLogs(int retentionDays) {
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
    public Mono<Boolean> shouldTriggerAlert(String eventType, int timeWindowMinutes, int threshold) {
        return Mono.fromCallable(() -> {
            LocalDateTime windowEnd = LocalDateTime.now();
            LocalDateTime windowStart = windowEnd.minusMinutes(timeWindowMinutes);
            AuditEventType type = parseEventType(eventType);
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

    // ========== 辅助方法 ==========

    private AuditEvent entityToDto(SecurityAuditEventEntity entity) {
        AuditEvent dto = new AuditEvent();
        dto.setId(entity.getEventId());
        dto.setType(entity.getEventType());
        dto.setUserId(entity.getUserId());
        dto.setResourceId(entity.getResourceId());
        dto.setAction(entity.getAction());
        dto.setDetails(entity.getDetails());
        dto.setIpAddress(entity.getClientIp());
        dto.setUserAgent(entity.getUserAgent());
        dto.setSuccess(entity.getSuccess());
        dto.setTimestamp(entity.getTimestamp());
        dto.setMetadata(parseJson(entity.getMetadata()));
        return dto;
    }

    private SecurityAuditEventEntity dtoToEntity(AuditEvent dto) {
        return SecurityAuditEventEntity.builder()
                .eventId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString())
                .eventType(dto.getType())
                .userId(dto.getUserId())
                .resourceId(dto.getResourceId())
                .clientIp(dto.getIpAddress())
                .userAgent(dto.getUserAgent())
                .action(dto.getAction())
                .details(dto.getDetails())
                .success(dto.isSuccess())
                .metadata(toJson(dto.getMetadata()))
                .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now())
                .build();
    }

    private SecurityAuditEvent entityToSecurityEvent(SecurityAuditEventEntity entity) {
        return SecurityAuditEvent.builder()
                .eventId(entity.getEventId())
                .eventType(entity.getEventType().name())
                .userId(entity.getUserId())
                .clientIp(entity.getClientIp())
                .userAgent(entity.getUserAgent())
                .timestamp(entity.getTimestamp())
                .resource(entity.getResource())
                .action(entity.getAction())
                .success(entity.getSuccess())
                .failureReason(entity.getFailureReason())
                .additionalData(parseJson(entity.getMetadata()))
                .requestId(entity.getRequestId())
                .sessionId(entity.getSessionId())
                .build();
    }

    private SecurityAuditEventEntity securityEventToEntity(SecurityAuditEvent event) {
        return SecurityAuditEventEntity.builder()
                .eventId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString())
                .eventType(parseEventType(event.getEventType()))
                .userId(event.getUserId())
                .clientIp(event.getClientIp())
                .userAgent(event.getUserAgent())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
                .resource(event.getResource())
                .action(event.getAction())
                .success(event.isSuccess())
                .failureReason(event.getFailureReason())
                .metadata(toJson(event.getAdditionalData()))
                .requestId(event.getRequestId())
                .sessionId(event.getSessionId())
                .build();
    }

    private AuditEventType parseEventType(String eventType) {
        try {
            return AuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            log.warn("无法解析事件类型: {}, 使用默认值", eventType);
            return AuditEventType.SYSTEM_MAINTENANCE;
        }
    }

    private RiskLevel determineRiskLevel(AuditEventType type, Boolean success) {
        if (type == null) return RiskLevel.LOW;
        if (success != null && !success) {
            if (type == AuditEventType.SECURITY_ALERT) return RiskLevel.CRITICAL;
            if (type == AuditEventType.SUSPICIOUS_ACTIVITY) return RiskLevel.HIGH;
            if (type == AuditEventType.AUTHORIZATION_FAILED) return RiskLevel.MEDIUM;
            return RiskLevel.LOW;
        }
        if (type == AuditEventType.JWT_TOKEN_REVOKED || type == AuditEventType.API_KEY_REVOKED) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return null;
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("JSON解析失败", e);
            return new HashMap<>();
        }
    }
}
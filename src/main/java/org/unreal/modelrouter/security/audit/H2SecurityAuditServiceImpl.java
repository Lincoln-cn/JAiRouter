package org.unreal.modelrouter.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.store.entity.SecurityAuditEntity;
import org.unreal.modelrouter.store.repository.SecurityAuditRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 基于 H2 数据库的安全审计服务实现
 * 使用 SecurityAuditRepository 进行持久化存储
 */
@Slf4j
@Service("h2SecurityAuditService")
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "store.type", havingValue = "h2", matchIfMissing = true)
public class H2SecurityAuditServiceImpl implements SecurityAuditService {
    
    private final SecurityAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    
    // 内存缓存用于快速告警检查
    private final Map<String, ConcurrentLinkedQueue<LocalDateTime>> timeWindowEvents = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> recordEvent(SecurityAuditEvent event) {
        return Mono.defer(() -> {
            try {
                // 设置事件ID和时间戳
                if (event.getEventId() == null) {
                    event.setEventId(UUID.randomUUID().toString());
                }
                if (event.getTimestamp() == null) {
                    event.setTimestamp(LocalDateTime.now());
                }
                
                // 转换为实体并保存到数据库
                SecurityAuditEntity entity = convertToEntity(event);
                
                return auditRepository.save(entity)
                    .doOnSuccess(saved -> {
                        // 更新时间窗口缓存用于告警
                        timeWindowEvents.computeIfAbsent(event.getEventType(), k -> new ConcurrentLinkedQueue<>())
                                       .offer(event.getTimestamp());
                        
                        // 记录到日志系统
                        logAuditEvent(event);
                        
                        log.debug("安全审计事件已记录到H2数据库: eventId={}, eventType={}, userId={}", 
                                 event.getEventId(), event.getEventType(), event.getUserId());
                    })
                    .doOnError(e -> log.error("记录安全审计事件到H2数据库失败: {}", e.getMessage(), e))
                    .then();
                    
            } catch (Exception e) {
                log.error("记录安全审计事件失败", e);
                return Mono.error(e);
            }
        });
    }
    
    @Override
    public Mono<Void> recordAuthenticationEvent(String userId, String clientIp, String userAgent, 
                                              boolean success, String failureReason) {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType(success ? "AUTHENTICATION_SUCCESS" : "AUTHENTICATION_FAILURE")
                .userId(userId)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .action("AUTHENTICATE")
                .success(success)
                .failureReason(failureReason)
                .additionalData(createAuthenticationMetadata(success, failureReason))
                .build();
                
        return recordEvent(event);
    }
    
    @Override
    public Mono<Void> recordSanitizationEvent(String userId, String contentType, String ruleId, int matchCount) {
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("contentType", contentType);
        additionalData.put("ruleId", ruleId);
        additionalData.put("matchCount", matchCount);
        
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType("DATA_SANITIZATION")
                .userId(userId)
                .action("SANITIZE")
                .success(true)
                .additionalData(additionalData)
                .build();
                
        return recordEvent(event);
    }
    
    @Override
    public Flux<SecurityAuditEvent> queryEvents(LocalDateTime startTime, LocalDateTime endTime, 
                                               String eventType, String userId, int limit) {
        Flux<SecurityAuditEntity> query;
        
        if (eventType != null && userId != null) {
            query = auditRepository.findByEventTypeAndUserIdAndTimestampBetween(
                eventType, userId, startTime, endTime);
        } else if (eventType != null) {
            query = auditRepository.findByEventTypeAndTimestampBetween(
                eventType, startTime, endTime);
        } else if (userId != null) {
            query = auditRepository.findByUserIdAndTimestampBetween(
                userId, startTime, endTime);
        } else {
            query = auditRepository.findByTimestampBetween(startTime, endTime);
        }
        
        return query
            .sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp())) // 按时间倒序
            .take(limit)
            .map(this::convertFromEntity);
    }
    
    @Override
    public Mono<Map<String, Object>> getSecurityStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        return auditRepository.findByTimestampBetween(startTime, endTime)
                .collectList()
                .map(entities -> {
                    Map<String, Object> statistics = new HashMap<>();
                    
                    // 总事件数
                    statistics.put("totalEvents", entities.size());
                    
                    // 按事件类型统计
                    Map<String, Long> eventTypeStats = new HashMap<>();
                    Map<String, Long> successFailureStats = new HashMap<>();
                    
                    for (SecurityAuditEntity entity : entities) {
                        eventTypeStats.merge(entity.getEventType(), 1L, Long::sum);
                        
                        String successKey = entity.getSuccess() ? "success" : "failure";
                        successFailureStats.merge(successKey, 1L, Long::sum);
                    }
                    
                    statistics.put("eventTypeStatistics", eventTypeStats);
                    statistics.put("successFailureStatistics", successFailureStats);
                    
                    // 认证统计
                    long authSuccessCount = entities.stream()
                            .filter(e -> "AUTHENTICATION_SUCCESS".equals(e.getEventType()))
                            .count();
                    long authFailureCount = entities.stream()
                            .filter(e -> "AUTHENTICATION_FAILURE".equals(e.getEventType()))
                            .count();
                    
                    Map<String, Object> authStats = new HashMap<>();
                    authStats.put("successCount", authSuccessCount);
                    authStats.put("failureCount", authFailureCount);
                    authStats.put("totalCount", authSuccessCount + authFailureCount);
                    
                    if (authSuccessCount + authFailureCount > 0) {
                        double successRate = (double) authSuccessCount / (authSuccessCount + authFailureCount) * 100;
                        authStats.put("successRate", Math.round(successRate * 100.0) / 100.0);
                    }
                    
                    statistics.put("authenticationStatistics", authStats);
                    
                    // 脱敏统计
                    long sanitizationCount = entities.stream()
                            .filter(e -> "DATA_SANITIZATION".equals(e.getEventType()))
                            .count();
                    statistics.put("sanitizationCount", sanitizationCount);
                    
                    // JWT令牌操作统计
                    long jwtIssuedCount = entities.stream()
                            .filter(e -> "JWT_TOKEN_ISSUED".equals(e.getEventType()))
                            .count();
                    long jwtRefreshedCount = entities.stream()
                            .filter(e -> "JWT_TOKEN_REFRESHED".equals(e.getEventType()))
                            .count();
                    long jwtRevokedCount = entities.stream()
                            .filter(e -> "JWT_TOKEN_REVOKED".equals(e.getEventType()))
                            .count();
                    long jwtValidatedCount = entities.stream()
                            .filter(e -> "JWT_TOKEN_VALIDATED".equals(e.getEventType()))
                            .count();
                    
                    Map<String, Object> jwtStats = new HashMap<>();
                    jwtStats.put("issuedCount", jwtIssuedCount);
                    jwtStats.put("refreshedCount", jwtRefreshedCount);
                    jwtStats.put("revokedCount", jwtRevokedCount);
                    jwtStats.put("validatedCount", jwtValidatedCount);
                    jwtStats.put("totalOperations", jwtIssuedCount + jwtRefreshedCount + jwtRevokedCount + jwtValidatedCount);
                    
                    statistics.put("jwtTokenStatistics", jwtStats);
                    
                    // API Key操作统计
                    long apiKeyCreatedCount = entities.stream()
                            .filter(e -> "API_KEY_CREATED".equals(e.getEventType()))
                            .count();
                    long apiKeyUsedCount = entities.stream()
                            .filter(e -> "API_KEY_USED".equals(e.getEventType()))
                            .count();
                    long apiKeyRevokedCount = entities.stream()
                            .filter(e -> "API_KEY_REVOKED".equals(e.getEventType()))
                            .count();
                    long apiKeyExpiredCount = entities.stream()
                            .filter(e -> "API_KEY_EXPIRED".equals(e.getEventType()))
                            .count();
                    
                    Map<String, Object> apiKeyStats = new HashMap<>();
                    apiKeyStats.put("createdCount", apiKeyCreatedCount);
                    apiKeyStats.put("usedCount", apiKeyUsedCount);
                    apiKeyStats.put("revokedCount", apiKeyRevokedCount);
                    apiKeyStats.put("expiredCount", apiKeyExpiredCount);
                    apiKeyStats.put("totalOperations", apiKeyCreatedCount + apiKeyUsedCount + apiKeyRevokedCount + apiKeyExpiredCount);
                    
                    statistics.put("apiKeyStatistics", apiKeyStats);
                    
                    // 安全事件统计
                    long securityAlertCount = entities.stream()
                            .filter(e -> "SECURITY_ALERT".equals(e.getEventType()))
                            .count();
                    long suspiciousActivityCount = entities.stream()
                            .filter(e -> "SUSPICIOUS_ACTIVITY".equals(e.getEventType()))
                            .count();
                    
                    Map<String, Object> securityStats = new HashMap<>();
                    securityStats.put("alertCount", securityAlertCount);
                    securityStats.put("suspiciousActivityCount", suspiciousActivityCount);
                    securityStats.put("totalSecurityEvents", securityAlertCount + suspiciousActivityCount);
                    
                    statistics.put("securityEventStatistics", securityStats);
                    
                    return statistics;
                });
    }
    
    @Override
    public Mono<Long> cleanupExpiredLogs(int retentionDays) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        
        return auditRepository.deleteByTimestampBefore(cutoffTime)
                .doOnSuccess(count -> {
                    log.info("清理过期审计日志完成，删除了 {} 条记录", count);
                    
                    // 同时清理内存缓存
                    for (ConcurrentLinkedQueue<LocalDateTime> timeQueue : timeWindowEvents.values()) {
                        while (!timeQueue.isEmpty()) {
                            LocalDateTime timestamp = timeQueue.peek();
                            if (timestamp != null && timestamp.isBefore(cutoffTime)) {
                                timeQueue.poll();
                            } else {
                                break;
                            }
                        }
                    }
                })
                .doOnError(e -> log.error("清理过期审计日志失败: {}", e.getMessage(), e));
    }
    
    @Override
    public Mono<Boolean> shouldTriggerAlert(String eventType, int timeWindowMinutes, int threshold) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(timeWindowMinutes);
        
        return Mono.fromCallable(() -> {
            ConcurrentLinkedQueue<LocalDateTime> eventTimes = timeWindowEvents.get(eventType);
            if (eventTimes == null) {
                return false;
            }
            
            // 清理过期的时间戳
            while (!eventTimes.isEmpty()) {
                LocalDateTime timestamp = eventTimes.peek();
                if (timestamp != null && timestamp.isBefore(windowStart)) {
                    eventTimes.poll();
                } else {
                    break;
                }
            }
            
            // 检查是否超过阈值
            boolean shouldAlert = eventTimes.size() >= threshold;
            
            if (shouldAlert) {
                log.warn("安全告警触发: eventType={}, timeWindow={}分钟, threshold={}, actualCount={}", 
                        eventType, timeWindowMinutes, threshold, eventTimes.size());
            }
            
            return shouldAlert;
        });
    }
    
    /**
     * 将 SecurityAuditEvent 转换为 SecurityAuditEntity
     */
    private SecurityAuditEntity convertToEntity(SecurityAuditEvent event) {
        String additionalDataJson = null;
        if (event.getAdditionalData() != null) {
            try {
                additionalDataJson = objectMapper.writeValueAsString(event.getAdditionalData());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize additional data: {}", e.getMessage());
            }
        }
        
        return SecurityAuditEntity.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .userId(event.getUserId())
                .clientIp(event.getClientIp())
                .userAgent(event.getUserAgent())
                .timestamp(event.getTimestamp())
                .resource(event.getResource())
                .action(event.getAction())
                .success(event.isSuccess())
                .failureReason(event.getFailureReason())
                .additionalData(additionalDataJson)
                .requestId(event.getRequestId())
                .sessionId(event.getSessionId())
                .build();
    }
    
    /**
     * 将 SecurityAuditEntity 转换为 SecurityAuditEvent
     */
    private SecurityAuditEvent convertFromEntity(SecurityAuditEntity entity) {
        Map<String, Object> additionalData = null;
        if (entity.getAdditionalData() != null) {
            try {
                additionalData = objectMapper.readValue(entity.getAdditionalData(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize additional data: {}", e.getMessage());
            }
        }
        
        return SecurityAuditEvent.builder()
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .userId(entity.getUserId())
                .clientIp(entity.getClientIp())
                .userAgent(entity.getUserAgent())
                .timestamp(entity.getTimestamp())
                .resource(entity.getResource())
                .action(entity.getAction())
                .success(entity.getSuccess())
                .failureReason(entity.getFailureReason())
                .additionalData(additionalData)
                .requestId(entity.getRequestId())
                .sessionId(entity.getSessionId())
                .build();
    }
    
    /**
     * 将审计事件记录到日志系统
     */
    private void logAuditEvent(SecurityAuditEvent event) {
        String logMessage = String.format(
                "SECURITY_AUDIT: eventType=%s, userId=%s, clientIp=%s, action=%s, success=%s, timestamp=%s",
                event.getEventType(),
                event.getUserId(),
                event.getClientIp(),
                event.getAction(),
                event.isSuccess(),
                event.getTimestamp()
        );
        
        if (event.isSuccess()) {
            log.info(logMessage);
        } else {
            log.warn("{}, failureReason={}", logMessage, event.getFailureReason());
        }
    }
    
    /**
     * 创建认证事件的附加元数据
     */
    private Map<String, Object> createAuthenticationMetadata(boolean success, String failureReason) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("authenticationResult", success ? "SUCCESS" : "FAILURE");
        
        if (!success && failureReason != null) {
            metadata.put("failureCategory", categorizeFailureReason(failureReason));
        }
        
        return metadata;
    }
    
    /**
     * 对失败原因进行分类
     */
    private String categorizeFailureReason(String failureReason) {
        if (failureReason == null) {
            return "UNKNOWN";
        }
        
        String reason = failureReason.toLowerCase();
        if (reason.contains("expired") || reason.contains("过期")) {
            return "EXPIRED_CREDENTIALS";
        } else if (reason.contains("invalid") || reason.contains("无效")) {
            return "INVALID_CREDENTIALS";
        } else if (reason.contains("missing") || reason.contains("缺失")) {
            return "MISSING_CREDENTIALS";
        } else if (reason.contains("blocked") || reason.contains("阻止")) {
            return "BLOCKED_USER";
        } else {
            return "OTHER";
        }
    }
}

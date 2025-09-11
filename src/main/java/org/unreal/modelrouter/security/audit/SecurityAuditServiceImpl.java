package org.unreal.modelrouter.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全审计服务实现类
 * 提供安全事件的记录、查询和分析功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.audit.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAuditServiceImpl implements SecurityAuditService {
    
    // 内存存储审计事件（生产环境应使用数据库或专门的日志系统）
    private final ConcurrentLinkedQueue<SecurityAuditEvent> auditEvents = new ConcurrentLinkedQueue<>();
    
    // 事件类型计数器，用于统计和告警
    private final Map<String, AtomicLong> eventCounters = new ConcurrentHashMap<>();
    
    // 时间窗口内的事件计数，用于告警检查
    private final Map<String, ConcurrentLinkedQueue<LocalDateTime>> timeWindowEvents = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> recordEvent(SecurityAuditEvent event) {
        return Mono.fromRunnable(() -> {
            try {
                // 设置事件ID和时间戳
                if (event.getEventId() == null) {
                    event.setEventId(UUID.randomUUID().toString());
                }
                if (event.getTimestamp() == null) {
                    event.setTimestamp(LocalDateTime.now());
                }
                
                // 存储事件
                auditEvents.offer(event);
                
                // 更新计数器
                eventCounters.computeIfAbsent(event.getEventType(), k -> new AtomicLong(0))
                           .incrementAndGet();
                
                // 记录到时间窗口
                timeWindowEvents.computeIfAbsent(event.getEventType(), k -> new ConcurrentLinkedQueue<>())
                               .offer(event.getTimestamp());
                
                // 记录到日志系统
                logAuditEvent(event);
                
                log.debug("安全审计事件已记录: eventId={}, eventType={}, userId={}", 
                         event.getEventId(), event.getEventType(), event.getUserId());
                
            } catch (Exception e) {
                log.error("记录安全审计事件失败", e);
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
        return Flux.fromIterable(auditEvents)
                .filter(event -> event.getTimestamp().isAfter(startTime) && event.getTimestamp().isBefore(endTime))
                .filter(event -> eventType == null || eventType.equals(event.getEventType()))
                .filter(event -> userId == null || userId.equals(event.getUserId()))
                .sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp())) // 按时间倒序
                .take(limit);
    }
    
    @Override
    public Mono<Map<String, Object>> getSecurityStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        return Flux.fromIterable(auditEvents)
                .filter(event -> event.getTimestamp().isAfter(startTime) && event.getTimestamp().isBefore(endTime))
                .collectList()
                .map(events -> {
                    Map<String, Object> statistics = new HashMap<>();
                    
                    // 总事件数
                    statistics.put("totalEvents", events.size());
                    
                    // 按事件类型统计
                    Map<String, Long> eventTypeStats = new HashMap<>();
                    Map<String, Long> successFailureStats = new HashMap<>();
                    
                    for (SecurityAuditEvent event : events) {
                        eventTypeStats.merge(event.getEventType(), 1L, Long::sum);
                        
                        String successKey = event.isSuccess() ? "success" : "failure";
                        successFailureStats.merge(successKey, 1L, Long::sum);
                    }
                    
                    statistics.put("eventTypeStatistics", eventTypeStats);
                    statistics.put("successFailureStatistics", successFailureStats);
                    
                    // 认证统计
                    long authSuccessCount = events.stream()
                            .filter(e -> "AUTHENTICATION_SUCCESS".equals(e.getEventType()))
                            .count();
                    long authFailureCount = events.stream()
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
                    long sanitizationCount = events.stream()
                            .filter(e -> "DATA_SANITIZATION".equals(e.getEventType()))
                            .count();
                    statistics.put("sanitizationCount", sanitizationCount);
                    
                    return statistics;
                });
    }
    
    @Override
    public Mono<Long> cleanupExpiredLogs(int retentionDays) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        
        return Mono.fromCallable(() -> {
            long removedCount = 0;
            
            // 清理主要事件存储
            while (!auditEvents.isEmpty()) {
                SecurityAuditEvent event = auditEvents.peek();
                if (event != null && event.getTimestamp().isBefore(cutoffTime)) {
                    auditEvents.poll();
                    removedCount++;
                } else {
                    break;
                }
            }
            
            // 清理时间窗口事件
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
            
            log.info("清理过期审计日志完成，删除了 {} 条记录", removedCount);
            return removedCount;
        });
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
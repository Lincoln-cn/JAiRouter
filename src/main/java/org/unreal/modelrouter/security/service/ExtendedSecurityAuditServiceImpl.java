package org.unreal.modelrouter.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.AuditEvent;
import org.unreal.modelrouter.dto.AuditEventQuery;
import org.unreal.modelrouter.dto.AuditEventType;
import org.unreal.modelrouter.dto.SecurityAlert;
import org.unreal.modelrouter.dto.SecurityReport;
import org.unreal.modelrouter.security.audit.SecurityAuditServiceImpl;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;


/**
 * 扩展的安全审计服务实现类
 * 提供JWT和API Key审计功能
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "jairouter.security.audit.enabled", havingValue = "true", matchIfMissing = true)
public class ExtendedSecurityAuditServiceImpl implements ExtendedSecurityAuditService {
    
    // 扩展的审计事件存储（JWT和API Key相关）
    private final ConcurrentLinkedQueue<AuditEvent> extendedAuditEvents = new ConcurrentLinkedQueue<>();
    
    // 事件类型计数器
    private final Map<AuditEventType, Long> eventTypeCounters = new ConcurrentHashMap<>();
    
    // 用户操作计数器
    private final Map<String, Long> userOperationCounters = new ConcurrentHashMap<>();
    
    // IP地址操作计数器
    private final Map<String, Long> ipOperationCounters = new ConcurrentHashMap<>();
    
    // 安全告警存储
    private final ConcurrentLinkedQueue<SecurityAlert> securityAlerts = new ConcurrentLinkedQueue<>();
    
    // JWT令牌审计方法
    
    @Override
    public Mono<Void> auditTokenIssued(String userId, String tokenId, String ipAddress, String userAgent) {
        AuditEvent event = createAuditEvent(
            AuditEventType.JWT_TOKEN_ISSUED,
            userId,
            tokenId,
            "TOKEN_ISSUED",
            "JWT令牌已颁发",
            ipAddress,
            userAgent,
            true,
            createTokenMetadata("issued", tokenId, null)
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.info("JWT令牌颁发审计记录完成: userId={}, tokenId={}, ip={}", 
                userId, tokenId, ipAddress));
    }   
 
    @Override
    public Mono<Void> auditTokenRefreshed(String userId, String oldTokenId, String newTokenId, String ipAddress) {
        Map<String, Object> metadata = createTokenMetadata("refreshed", newTokenId, oldTokenId);
        metadata.put("oldTokenId", oldTokenId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.JWT_TOKEN_REFRESHED,
            userId,
            newTokenId,
            "TOKEN_REFRESHED",
            "JWT令牌已刷新",
            ipAddress,
            null,
            true,
            metadata
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.info("JWT令牌刷新审计记录完成: userId={}, oldTokenId={}, newTokenId={}, ip={}", 
                userId, oldTokenId, newTokenId, ipAddress));
    }
    
    @Override
    public Mono<Void> auditTokenRevoked(String userId, String tokenId, String reason, String revokedBy) {
        Map<String, Object> metadata = createTokenMetadata("revoked", tokenId, null);
        metadata.put("revokeReason", reason);
        metadata.put("revokedBy", revokedBy);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.JWT_TOKEN_REVOKED,
            userId,
            tokenId,
            "TOKEN_REVOKED",
            "JWT令牌已撤销: " + reason,
            null,
            null,
            true,
            metadata
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.warn("JWT令牌撤销审计记录完成: userId={}, tokenId={}, reason={}, revokedBy={}", 
                userId, tokenId, reason, revokedBy));
    }
    
    @Override
    public Mono<Void> auditTokenValidated(String userId, String tokenId, boolean isValid, String ipAddress) {
        AuditEvent event = createAuditEvent(
            AuditEventType.JWT_TOKEN_VALIDATED,
            userId,
            tokenId,
            "TOKEN_VALIDATED",
            isValid ? "JWT令牌验证成功" : "JWT令牌验证失败",
            ipAddress,
            null,
            isValid,
            createTokenMetadata("validated", tokenId, null)
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> {
                if (isValid) {
                    log.debug("JWT令牌验证成功审计记录完成: userId={}, tokenId={}, ip={}", 
                        userId, tokenId, ipAddress);
                } else {
                    log.warn("JWT令牌验证失败审计记录完成: userId={}, tokenId={}, ip={}", 
                        userId, tokenId, ipAddress);
                }
            });
    }    
 
   // API Key审计方法
    
    @Override
    public Mono<Void> auditApiKeyCreated(String keyId, String createdBy, String ipAddress) {
        AuditEvent event = createAuditEvent(
            AuditEventType.API_KEY_CREATED,
            createdBy,
            keyId,
            "API_KEY_CREATED",
            "API Key已创建",
            ipAddress,
            null,
            true,
            createApiKeyMetadata("created", keyId, createdBy)
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.info("API Key创建审计记录完成: keyId={}, createdBy={}, ip={}", 
                keyId, createdBy, ipAddress));
    }
    
    @Override
    public Mono<Void> auditApiKeyUsed(String keyId, String endpoint, String ipAddress, boolean success) {
        Map<String, Object> metadata = createApiKeyMetadata("used", keyId, null);
        metadata.put("endpoint", endpoint);
        metadata.put("usageResult", success ? "SUCCESS" : "FAILURE");
        
        AuditEvent event = createAuditEvent(
            AuditEventType.API_KEY_USED,
            null, // API Key使用时可能没有明确的用户ID
            keyId,
            "API_KEY_USED",
            success ? "API Key使用成功" : "API Key使用失败",
            ipAddress,
            null,
            success,
            metadata
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> {
                if (success) {
                    log.debug("API Key使用成功审计记录完成: keyId={}, endpoint={}, ip={}", 
                        keyId, endpoint, ipAddress);
                } else {
                    log.warn("API Key使用失败审计记录完成: keyId={}, endpoint={}, ip={}", 
                        keyId, endpoint, ipAddress);
                }
            });
    }
    
    @Override
    public Mono<Void> auditApiKeyRevoked(String keyId, String reason, String revokedBy) {
        Map<String, Object> metadata = createApiKeyMetadata("revoked", keyId, revokedBy);
        metadata.put("revokeReason", reason);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.API_KEY_REVOKED,
            revokedBy,
            keyId,
            "API_KEY_REVOKED",
            "API Key已撤销: " + reason,
            null,
            null,
            true,
            metadata
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.warn("API Key撤销审计记录完成: keyId={}, reason={}, revokedBy={}", 
                keyId, reason, revokedBy));
    }
    
    @Override
    public Mono<Void> auditApiKeyExpired(String keyId) {
        AuditEvent event = createAuditEvent(
            AuditEventType.API_KEY_EXPIRED,
            null,
            keyId,
            "API_KEY_EXPIRED",
            "API Key已过期",
            null,
            null,
            true,
            createApiKeyMetadata("expired", keyId, null)
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.info("API Key过期审计记录完成: keyId={}", keyId));
    } 
   
    // 安全事件审计方法
    
    @Override
    public Mono<Void> auditSecurityEvent(String eventType, String details, String userId, String ipAddress) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("securityEventType", eventType);
        metadata.put("eventCategory", "SECURITY");
        
        AuditEvent event = createAuditEvent(
            AuditEventType.SECURITY_ALERT,
            userId,
            null,
            "SECURITY_EVENT",
            details,
            ipAddress,
            null,
            true,
            metadata
        );
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.warn("安全事件审计记录完成: eventType={}, userId={}, ip={}, details={}", 
                eventType, userId, ipAddress, details));
    }
    
    @Override
    public Mono<Void> auditSuspiciousActivity(String activity, String userId, String ipAddress, String details) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("activityType", activity);
        metadata.put("suspiciousLevel", "HIGH");
        metadata.put("eventCategory", "SUSPICIOUS");
        
        AuditEvent event = createAuditEvent(
            AuditEventType.SUSPICIOUS_ACTIVITY,
            userId,
            null,
            "SUSPICIOUS_ACTIVITY",
            details,
            ipAddress,
            null,
            false, // 可疑活动标记为不成功
            metadata
        );
        
        // 创建安全告警
        SecurityAlert alert = new SecurityAlert(
            UUID.randomUUID().toString(),
            "SUSPICIOUS_ACTIVITY",
            "HIGH",
            "检测到可疑活动: " + activity + " - " + details,
            userId,
            ipAddress,
            LocalDateTime.now(),
            false
        );
        securityAlerts.offer(alert);
        
        return recordAuditEvent(event)
            .doOnSuccess(v -> log.error("可疑活动审计记录完成: activity={}, userId={}, ip={}, details={}", 
                activity, userId, ipAddress, details));
    } 
   
    // 扩展查询接口
    
    @Override
    public Flux<AuditEvent> findAuditEvents(AuditEventQuery query) {
        return Flux.fromIterable(extendedAuditEvents)
            .filter(event -> matchesQuery(event, query))
            .sort(this::compareEvents)
            .skip((long) query.getPage() * query.getSize())
            .take(query.getSize());
    }
    
    @Override
    public Mono<SecurityReport> generateSecurityReport(LocalDateTime from, LocalDateTime to) {
        return Flux.fromIterable(extendedAuditEvents)
            .filter(event -> event.getTimestamp().isAfter(from) && event.getTimestamp().isBefore(to))
            .collectList()
            .map(events -> {
                SecurityReport report = new SecurityReport();
                report.setReportPeriodStart(from);
                report.setReportPeriodEnd(to);
                
                // 统计JWT操作
                long jwtOperations = events.stream()
                    .filter(e -> isJwtEvent(e.getType()))
                    .count();
                report.setTotalJwtOperations(jwtOperations);
                
                // 统计API Key操作
                long apiKeyOperations = events.stream()
                    .filter(e -> isApiKeyEvent(e.getType()))
                    .count();
                report.setTotalApiKeyOperations(apiKeyOperations);
                
                // 统计失败的认证
                long failedAuth = events.stream()
                    .filter(e -> !e.isSuccess() && (isJwtEvent(e.getType()) || isApiKeyEvent(e.getType())))
                    .count();
                report.setFailedAuthentications(failedAuth);
                
                // 统计可疑活动
                long suspiciousActivities = events.stream()
                    .filter(e -> e.getType() == AuditEventType.SUSPICIOUS_ACTIVITY)
                    .count();
                report.setSuspiciousActivities(suspiciousActivities);
                
                // 按操作类型统计
                Map<String, Long> operationsByType = events.stream()
                    .collect(Collectors.groupingBy(
                        e -> e.getType().name(),
                        Collectors.counting()
                    ));
                report.setOperationsByType(operationsByType);
                
                // 按用户统计
                Map<String, Long> operationsByUser = events.stream()
                    .filter(e -> e.getUserId() != null)
                    .collect(Collectors.groupingBy(
                        AuditEvent::getUserId,
                        Collectors.counting()
                    ));
                report.setOperationsByUser(operationsByUser);
                
                // 获取活跃IP地址
                List<String> topIpAddresses = events.stream()
                    .filter(e -> e.getIpAddress() != null)
                    .collect(Collectors.groupingBy(
                        AuditEvent::getIpAddress,
                        Collectors.counting()
                    ))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                report.setTopIpAddresses(topIpAddresses);
                
                // 获取相关告警
                List<SecurityAlert> alerts = securityAlerts.stream()
                    .filter(alert -> alert.getTimestamp().isAfter(from) && alert.getTimestamp().isBefore(to))
                    .collect(Collectors.toList());
                report.setAlerts(alerts);
                
                return report;
            });
    }   
 
    @Override
    public Mono<Void> recordAuditEvent(AuditEvent auditEvent) {
        return Mono.fromRunnable(() -> {
            try {
                // 设置事件ID和时间戳
                if (auditEvent.getId() == null) {
                    auditEvent.setId(UUID.randomUUID().toString());
                }
                if (auditEvent.getTimestamp() == null) {
                    auditEvent.setTimestamp(LocalDateTime.now());
                }
                
                // 存储事件到内存（用于查询）
                extendedAuditEvents.offer(auditEvent);
                
                // 更新计数器
                eventTypeCounters.merge(auditEvent.getType(), 1L, Long::sum);
                
                if (auditEvent.getUserId() != null) {
                    userOperationCounters.merge(auditEvent.getUserId(), 1L, Long::sum);
                }
                
                if (auditEvent.getIpAddress() != null) {
                    ipOperationCounters.merge(auditEvent.getIpAddress(), 1L, Long::sum);
                }
                
                // 记录到结构化日志（用于持久化和分析）
                recordToStructuredLog(auditEvent);
                
                log.debug("扩展审计事件已记录: eventId={}, eventType={}, userId={}", 
                         auditEvent.getId(), auditEvent.getType(), auditEvent.getUserId());
                
            } catch (Exception e) {
                log.error("记录扩展审计事件失败", e);
            }
        });
    }
    
    /**
     * 记录到结构化日志
     */
    private void recordToStructuredLog(AuditEvent auditEvent) {
        try {
            // 创建结构化日志条目
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("audit_event_id", auditEvent.getId());
            logEntry.put("audit_event_type", auditEvent.getType().name());
            logEntry.put("audit_user_id", auditEvent.getUserId());
            logEntry.put("audit_resource_id", auditEvent.getResourceId());
            logEntry.put("audit_action", auditEvent.getAction());
            logEntry.put("audit_details", auditEvent.getDetails());
            logEntry.put("audit_ip_address", auditEvent.getIpAddress());
            logEntry.put("audit_user_agent", auditEvent.getUserAgent());
            logEntry.put("audit_success", auditEvent.isSuccess());
            logEntry.put("audit_timestamp", auditEvent.getTimestamp().toString());
            logEntry.put("audit_metadata", auditEvent.getMetadata());
            
            // 使用专门的审计日志记录器
            org.slf4j.Logger auditLogger = org.slf4j.LoggerFactory.getLogger("AUDIT_LOG");
            
            // 记录为JSON格式的结构化日志
            String jsonLog = createJsonLogEntry(logEntry);
            auditLogger.info("AUDIT_EVENT: {}", jsonLog);
            
            // 同时写入文件（简单实现）
            writeToAuditFile(auditEvent, jsonLog);
            
        } catch (Exception e) {
            log.warn("记录结构化审计日志失败: {}", e.getMessage());
        }
    }
    
    /**
     * 写入审计日志文件
     */
    private void writeToAuditFile(AuditEvent auditEvent, String jsonLog) {
        try {
            // 创建审计日志目录
            java.nio.file.Path auditDir = java.nio.file.Paths.get("logs/audit");
            if (!java.nio.file.Files.exists(auditDir)) {
                java.nio.file.Files.createDirectories(auditDir);
            }
            
            // 根据事件类型选择文件
            String fileName = getAuditFileName(auditEvent.getType());
            java.nio.file.Path filePath = auditDir.resolve(fileName);
            
            // 写入日志条目
            String logLine = String.format("%s [AUDIT] %s%n", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                jsonLog);
            
            java.nio.file.Files.write(filePath, logLine.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND);
                
        } catch (Exception e) {
            log.warn("写入审计日志文件失败: {}", e.getMessage());
        }
    }
    
    /**
     * 根据事件类型获取审计日志文件名
     */
    private String getAuditFileName(AuditEventType eventType) {
        switch (eventType) {
            case JWT_TOKEN_ISSUED:
            case JWT_TOKEN_REFRESHED:
            case JWT_TOKEN_REVOKED:
            case JWT_TOKEN_VALIDATED:
            case JWT_TOKEN_EXPIRED:
                return "jwt-audit.log";
                
            case API_KEY_CREATED:
            case API_KEY_USED:
            case API_KEY_REVOKED:
            case API_KEY_EXPIRED:
            case API_KEY_UPDATED:
                return "api-key-audit.log";
                
            case SECURITY_ALERT:
            case SUSPICIOUS_ACTIVITY:
            case AUTHENTICATION_FAILED:
            case AUTHORIZATION_FAILED:
                return "security-events-audit.log";
                
            default:
                return "security-audit.log";
        }
    }
    
    /**
     * 创建JSON格式的日志条目
     */
    private String createJsonLogEntry(Map<String, Object> logEntry) {
        try {
            // 简单的JSON序列化
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : logEntry.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":");
                
                Object value = entry.getValue();
                if (value == null) {
                    json.append("null");
                } else if (value instanceof String) {
                    json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                } else if (value instanceof Boolean || value instanceof Number) {
                    json.append(value.toString());
                } else {
                    json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            log.warn("创建JSON日志条目失败: {}", e.getMessage());
            return logEntry.toString();
        }
    }
    
    // ========================================
    // 实现基础SecurityAuditService接口方法
    // ========================================
    
    @Override
    public Mono<Void> recordEvent(org.unreal.modelrouter.security.model.SecurityAuditEvent event) {
        return Mono.fromRunnable(() -> {
            try {
                // 将SecurityAuditEvent转换为AuditEvent并记录
                AuditEvent auditEvent = convertFromSecurityAuditEvent(event);
                recordAuditEvent(auditEvent).subscribe();
                
                log.debug("基础安全审计事件已记录: eventId={}, eventType={}", 
                         event.getEventId(), event.getEventType());
                
            } catch (Exception e) {
                log.error("记录基础安全审计事件失败", e);
            }
        });
    }
    
    @Override
    public Mono<Void> recordAuthenticationEvent(String userId, String clientIp, String userAgent, 
                                              boolean success, String failureReason) {
        if (success) {
            return auditTokenValidated(userId, "auth-" + System.currentTimeMillis(), true, clientIp);
        } else {
            return auditSecurityEvent("AUTHENTICATION_FAILED", 
                failureReason != null ? failureReason : "认证失败", userId, clientIp);
        }
    }
    
    @Override
    public Mono<Void> recordSanitizationEvent(String userId, String contentType, String ruleId, int matchCount) {
        return auditSecurityEvent("DATA_SANITIZATION", 
            String.format("数据脱敏: contentType=%s, ruleId=%s, matchCount=%d", contentType, ruleId, matchCount), 
            userId, null);
    }
    
    @Override
    public Flux<org.unreal.modelrouter.security.model.SecurityAuditEvent> queryEvents(LocalDateTime startTime, LocalDateTime endTime, 
                                                                                     String eventType, String userId, int limit) {
        AuditEventQuery query = new AuditEventQuery();
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setUserId(userId);
        query.setSize(limit);
        
        if (eventType != null) {
            try {
                AuditEventType type = AuditEventType.valueOf(eventType);
                query.setEventTypes(List.of(type));
            } catch (IllegalArgumentException e) {
                log.warn("无效的事件类型: {}", eventType);
            }
        }
        
        return findAuditEvents(query)
            .map(this::convertToSecurityAuditEvent);
    }
    
    @Override
    public Mono<Map<String, Object>> getSecurityStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        return generateSecurityReport(startTime, endTime)
            .map(report -> {
                Map<String, Object> stats = new HashMap<>();
                stats.put("totalJwtOperations", report.getTotalJwtOperations());
                stats.put("totalApiKeyOperations", report.getTotalApiKeyOperations());
                stats.put("failedAuthentications", report.getFailedAuthentications());
                stats.put("suspiciousActivities", report.getSuspiciousActivities());
                stats.put("operationsByType", report.getOperationsByType());
                stats.put("operationsByUser", report.getOperationsByUser());
                stats.put("topIpAddresses", report.getTopIpAddresses());
                stats.put("alertCount", report.getAlerts().size());
                return stats;
            });
    }
    
    @Override
    public Mono<Long> cleanupExpiredLogs(int retentionDays) {
        return Mono.fromCallable(() -> {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            long removedCount = 0;
            
            // 清理过期的审计事件
            Iterator<AuditEvent> iterator = extendedAuditEvents.iterator();
            while (iterator.hasNext()) {
                AuditEvent event = iterator.next();
                if (event.getTimestamp().isBefore(cutoffTime)) {
                    iterator.remove();
                    removedCount++;
                }
            }
            
            // 清理过期的安全告警
            Iterator<SecurityAlert> alertIterator = securityAlerts.iterator();
            while (alertIterator.hasNext()) {
                SecurityAlert alert = alertIterator.next();
                if (alert.getTimestamp().isBefore(cutoffTime)) {
                    alertIterator.remove();
                }
            }
            
            log.info("清理过期审计日志完成，删除了 {} 条记录", removedCount);
            return removedCount;
        });
    }
    
    @Override
    public Mono<Boolean> shouldTriggerAlert(String eventType, int timeWindowMinutes, int threshold) {
        return Mono.fromCallable(() -> {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(timeWindowMinutes);
            
            long eventCount = extendedAuditEvents.stream()
                .filter(event -> event.getTimestamp().isAfter(windowStart))
                .filter(event -> event.getType().name().equals(eventType))
                .count();
            
            return eventCount >= threshold;
        });
    }
    
    /**
     * 将SecurityAuditEvent转换为AuditEvent
     */
    private AuditEvent convertFromSecurityAuditEvent(org.unreal.modelrouter.security.model.SecurityAuditEvent securityEvent) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setId(securityEvent.getEventId());
        auditEvent.setUserId(securityEvent.getUserId());
        auditEvent.setAction(securityEvent.getAction());
        auditEvent.setDetails(securityEvent.getFailureReason() != null ? securityEvent.getFailureReason() : "安全事件");
        auditEvent.setIpAddress(securityEvent.getClientIp());
        auditEvent.setUserAgent(securityEvent.getUserAgent());
        auditEvent.setSuccess(securityEvent.isSuccess());
        auditEvent.setTimestamp(securityEvent.getTimestamp());
        auditEvent.setResourceId(securityEvent.getResource());
        auditEvent.setMetadata(securityEvent.getAdditionalData());
        
        // 尝试映射事件类型
        try {
            auditEvent.setType(AuditEventType.valueOf(securityEvent.getEventType()));
        } catch (IllegalArgumentException e) {
            auditEvent.setType(AuditEventType.SECURITY_ALERT);
        }
        
        return auditEvent;
    }
    
    @Override
    public Mono<Void> batchRecordAuditEvents(List<AuditEvent> auditEvents) {
        return Flux.fromIterable(auditEvents)
            .flatMap(this::recordAuditEvent)
            .then()
            .doOnSuccess(v -> log.info("批量记录审计事件完成，共 {} 条", auditEvents.size()));
    }
    
    @Override
    public Flux<AuditEvent> getUserAuditEvents(String userId, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return Flux.fromIterable(extendedAuditEvents)
            .filter(event -> userId.equals(event.getUserId()))
            .filter(event -> event.getTimestamp().isAfter(startTime) && event.getTimestamp().isBefore(endTime))
            .sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
            .take(limit);
    }
    
    @Override
    public Flux<AuditEvent> getIpAuditEvents(String ipAddress, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return Flux.fromIterable(extendedAuditEvents)
            .filter(event -> ipAddress.equals(event.getIpAddress()))
            .filter(event -> event.getTimestamp().isAfter(startTime) && event.getTimestamp().isBefore(endTime))
            .sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
            .take(limit);
    }    
  
  // 辅助方法
    
    private AuditEvent createAuditEvent(AuditEventType type, String userId, String resourceId, 
                                       String action, String details, String ipAddress, 
                                       String userAgent, boolean success, Map<String, Object> metadata) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType(type);
        event.setUserId(userId);
        event.setResourceId(resourceId);
        event.setAction(action);
        event.setDetails(details);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        event.setSuccess(success);
        event.setTimestamp(LocalDateTime.now());
        event.setMetadata(metadata);
        return event;
    }
    
    private Map<String, Object> createTokenMetadata(String operation, String tokenId, String relatedTokenId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", operation);
        metadata.put("tokenId", tokenId);
        metadata.put("resourceType", "JWT_TOKEN");
        if (relatedTokenId != null) {
            metadata.put("relatedTokenId", relatedTokenId);
        }
        return metadata;
    }
    
    private Map<String, Object> createApiKeyMetadata(String operation, String keyId, String operatorId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", operation);
        metadata.put("keyId", keyId);
        metadata.put("resourceType", "API_KEY");
        if (operatorId != null) {
            metadata.put("operatorId", operatorId);
        }
        return metadata;
    }
    
    private boolean matchesQuery(AuditEvent event, AuditEventQuery query) {
        if (query.getStartTime() != null && event.getTimestamp().isBefore(query.getStartTime())) {
            return false;
        }
        if (query.getEndTime() != null && event.getTimestamp().isAfter(query.getEndTime())) {
            return false;
        }
        if (query.getEventTypes() != null && !query.getEventTypes().contains(event.getType())) {
            return false;
        }
        if (query.getUserId() != null && !query.getUserId().equals(event.getUserId())) {
            return false;
        }
        if (query.getResourceId() != null && !query.getResourceId().equals(event.getResourceId())) {
            return false;
        }
        if (query.getAction() != null && !query.getAction().equals(event.getAction())) {
            return false;
        }
        if (query.getIpAddress() != null && !query.getIpAddress().equals(event.getIpAddress())) {
            return false;
        }
        if (query.getSuccess() != null && query.getSuccess() != event.isSuccess()) {
            return false;
        }
        return true;
    }
    
    private int compareEvents(AuditEvent e1, AuditEvent e2) {
        // 默认按时间戳倒序排列
        return e2.getTimestamp().compareTo(e1.getTimestamp());
    }
    
    private boolean isJwtEvent(AuditEventType type) {
        return type == AuditEventType.JWT_TOKEN_ISSUED ||
               type == AuditEventType.JWT_TOKEN_REFRESHED ||
               type == AuditEventType.JWT_TOKEN_REVOKED ||
               type == AuditEventType.JWT_TOKEN_VALIDATED ||
               type == AuditEventType.JWT_TOKEN_EXPIRED;
    }
    
    private boolean isApiKeyEvent(AuditEventType type) {
        return type == AuditEventType.API_KEY_CREATED ||
               type == AuditEventType.API_KEY_USED ||
               type == AuditEventType.API_KEY_REVOKED ||
               type == AuditEventType.API_KEY_EXPIRED ||
               type == AuditEventType.API_KEY_UPDATED;
    }
    
    /**
     * 将AuditEvent转换为SecurityAuditEvent
     */
    private org.unreal.modelrouter.security.model.SecurityAuditEvent convertToSecurityAuditEvent(AuditEvent auditEvent) {
        return org.unreal.modelrouter.security.model.SecurityAuditEvent.builder()
            .eventId(auditEvent.getId())
            .eventType(auditEvent.getType().name())
            .userId(auditEvent.getUserId())
            .clientIp(auditEvent.getIpAddress())
            .userAgent(auditEvent.getUserAgent())
            .timestamp(auditEvent.getTimestamp())
            .resource(auditEvent.getResourceId())
            .action(auditEvent.getAction())
            .success(auditEvent.isSuccess())
            .failureReason(auditEvent.isSuccess() ? null : auditEvent.getDetails())
            .additionalData(auditEvent.getMetadata())
            .build();
    }
}
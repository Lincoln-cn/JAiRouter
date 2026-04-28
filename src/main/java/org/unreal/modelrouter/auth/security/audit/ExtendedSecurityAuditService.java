package org.unreal.modelrouter.auth.security.audit;

import org.unreal.modelrouter.dto.AuditEvent;
import org.unreal.modelrouter.dto.AuditEventQuery;
import org.unreal.modelrouter.dto.SecurityReport;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扩展的安全审计服务接口
 * 在现有SecurityAuditService基础上添加JWT和API Key审计功能
 */
public interface ExtendedSecurityAuditService extends SecurityAuditService {

    // JWT令牌审计方法

    /**
     * 记录JWT令牌颁发事件
     */
    Mono<Void> auditTokenIssued(String userId, String tokenId, String ipAddress, String userAgent);

    /**
     * 记录JWT令牌刷新事件
     */
    Mono<Void> auditTokenRefreshed(String userId, String oldTokenId, String newTokenId, String ipAddress);

    /**
     * 记录JWT令牌撤销事件
     */
    Mono<Void> auditTokenRevoked(String userId, String tokenId, String reason, String revokedBy);

    /**
     * 记录JWT令牌验证事件
     */
    Mono<Void> auditTokenValidated(String userId, String tokenId, boolean isValid, String ipAddress);

    // API Key审计方法

    /**
     * 记录API Key创建事件
     */
    Mono<Void> auditApiKeyCreated(String keyId, String createdBy, String ipAddress);

    /**
     * 记录API Key使用事件
     */
    Mono<Void> auditApiKeyUsed(String keyId, String endpoint, String ipAddress, boolean success);

    /**
     * 记录API Key撤销事件
     */
    Mono<Void> auditApiKeyRevoked(String keyId, String reason, String revokedBy);

    /**
     * 记录API Key过期事件
     */
    Mono<Void> auditApiKeyExpired(String keyId);

    // 安全事件审计方法

    /**
     * 记录安全事件
     */
    Mono<Void> auditSecurityEvent(String eventType, String details, String userId, String ipAddress);

    /**
     * 记录可疑活动
     */
    Mono<Void> auditSuspiciousActivity(String activity, String userId, String ipAddress, String details);

    // 扩展查询接口

    /**
     * 根据查询条件查找审计事件
     */
    Flux<AuditEvent> findAuditEvents(AuditEventQuery query);

    /**
     * 根据查询条件统计审计事件数量（用于分页）
     */
    Mono<Long> countAuditEvents(AuditEventQuery query);

    /**
     * 生成安全报告
     */
    Mono<SecurityReport> generateSecurityReport(LocalDateTime from, LocalDateTime to);

    /**
     * 记录通用审计事件
     */
    Mono<Void> recordAuditEvent(AuditEvent auditEvent);

    /**
     * 批量记录审计事件
     */
    Mono<Void> batchRecordAuditEvents(List<AuditEvent> auditEvents);

    /**
     * 获取用户的审计事件
     */
    Flux<AuditEvent> getUserAuditEvents(String userId, LocalDateTime startTime, LocalDateTime endTime, int limit);

    /**
     * 获取IP地址的审计事件
     */
    Flux<AuditEvent> getIpAuditEvents(String ipAddress, LocalDateTime startTime, LocalDateTime endTime, int limit);
}
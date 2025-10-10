package org.unreal.modelrouter.security.service;

import org.unreal.modelrouter.dto.AuditEvent;
import org.unreal.modelrouter.dto.AuditEventQuery;
import org.unreal.modelrouter.dto.SecurityReport;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
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
     * @param userId 用户ID
     * @param tokenId 令牌ID
     * @param ipAddress IP地址
     * @param userAgent 用户代理
     * @return 记录操作结果
     */
    Mono<Void> auditTokenIssued(String userId, String tokenId, String ipAddress, String userAgent);
    
    /**
     * 记录JWT令牌刷新事件
     * @param userId 用户ID
     * @param oldTokenId 旧令牌ID
     * @param newTokenId 新令牌ID
     * @param ipAddress IP地址
     * @return 记录操作结果
     */
    Mono<Void> auditTokenRefreshed(String userId, String oldTokenId, String newTokenId, String ipAddress);
    
    /**
     * 记录JWT令牌撤销事件
     * @param userId 用户ID
     * @param tokenId 令牌ID
     * @param reason 撤销原因
     * @param revokedBy 撤销者
     * @return 记录操作结果
     */
    Mono<Void> auditTokenRevoked(String userId, String tokenId, String reason, String revokedBy);
    
    /**
     * 记录JWT令牌验证事件
     * @param userId 用户ID
     * @param tokenId 令牌ID
     * @param isValid 是否有效
     * @param ipAddress IP地址
     * @return 记录操作结果
     */
    Mono<Void> auditTokenValidated(String userId, String tokenId, boolean isValid, String ipAddress);
    
    // API Key审计方法
    
    /**
     * 记录API Key创建事件
     * @param keyId API Key ID
     * @param createdBy 创建者
     * @param ipAddress IP地址
     * @return 记录操作结果
     */
    Mono<Void> auditApiKeyCreated(String keyId, String createdBy, String ipAddress);
    
    /**
     * 记录API Key使用事件
     * @param keyId API Key ID
     * @param endpoint 访问的端点
     * @param ipAddress IP地址
     * @param success 是否成功
     * @return 记录操作结果
     */
    Mono<Void> auditApiKeyUsed(String keyId, String endpoint, String ipAddress, boolean success);
    
    /**
     * 记录API Key撤销事件
     * @param keyId API Key ID
     * @param reason 撤销原因
     * @param revokedBy 撤销者
     * @return 记录操作结果
     */
    Mono<Void> auditApiKeyRevoked(String keyId, String reason, String revokedBy);
    
    /**
     * 记录API Key过期事件
     * @param keyId API Key ID
     * @return 记录操作结果
     */
    Mono<Void> auditApiKeyExpired(String keyId);
    
    // 安全事件审计方法
    
    /**
     * 记录安全事件
     * @param eventType 事件类型
     * @param details 详细信息
     * @param userId 用户ID
     * @param ipAddress IP地址
     * @return 记录操作结果
     */
    Mono<Void> auditSecurityEvent(String eventType, String details, String userId, String ipAddress);
    
    /**
     * 记录可疑活动
     * @param activity 活动描述
     * @param userId 用户ID
     * @param ipAddress IP地址
     * @param details 详细信息
     * @return 记录操作结果
     */
    Mono<Void> auditSuspiciousActivity(String activity, String userId, String ipAddress, String details);
    
    // 扩展查询接口
    
    /**
     * 根据查询条件查找审计事件
     * @param query 查询条件
     * @return 审计事件列表
     */
    Flux<AuditEvent> findAuditEvents(AuditEventQuery query);
    
    /**
     * 生成安全报告
     * @param from 开始时间
     * @param to 结束时间
     * @return 安全报告
     */
    Mono<SecurityReport> generateSecurityReport(LocalDateTime from, LocalDateTime to);
    
    /**
     * 记录通用审计事件
     * @param auditEvent 审计事件
     * @return 记录操作结果
     */
    Mono<Void> recordAuditEvent(AuditEvent auditEvent);
    
    /**
     * 批量记录审计事件
     * @param auditEvents 审计事件列表
     * @return 记录操作结果
     */
    Mono<Void> batchRecordAuditEvents(List<AuditEvent> auditEvents);
    
    /**
     * 获取用户的审计事件
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制数量
     * @return 用户审计事件列表
     */
    Flux<AuditEvent> getUserAuditEvents(String userId, LocalDateTime startTime, LocalDateTime endTime, int limit);
    
    /**
     * 获取IP地址的审计事件
     * @param ipAddress IP地址
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制数量
     * @return IP地址审计事件列表
     */
    Flux<AuditEvent> getIpAuditEvents(String ipAddress, LocalDateTime startTime, LocalDateTime endTime, int limit);
}
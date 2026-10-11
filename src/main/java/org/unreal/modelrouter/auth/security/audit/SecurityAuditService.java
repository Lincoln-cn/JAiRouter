package org.unreal.modelrouter.auth.security.audit;

import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 安全审计服务接口
 * 提供安全事件的记录、查询和分析功能
 */
public interface SecurityAuditService {
    
    /**
     * 记录安全审计事件
     * @param event 安全审计事件
     * @return 记录操作结果
     */
    Mono<Void> recordEvent(SecurityAuditEvent event);
    
    /**
     * 记录认证事件
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param userAgent 用户代理
     * @param success 是否成功
     * @param failureReason 失败原因（如果失败）
     * @return 记录操作结果
     */
    Mono<Void> recordAuthenticationEvent(String userId, String clientIp, String userAgent, 
                                       boolean success, String failureReason);
    
    /**
     * 记录脱敏事件
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param ruleId 应用的规则ID
     * @param matchCount 匹配次数
     * @return 记录操作结果
     */
    Mono<Void> recordSanitizationEvent(String userId, String contentType, String ruleId, int matchCount);
    
    /**
     * 查询审计事件
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param eventType 事件类型（可选）
     * @param userId 用户ID（可选）
     * @param limit 限制数量
     * @return 审计事件流
     */
    Flux<SecurityAuditEvent> queryEvents(LocalDateTime startTime, LocalDateTime endTime, 
                                       String eventType, String userId, int limit);
    
    /**
     * 获取安全统计信息
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息
     */
    Mono<Map<String, Object>> getSecurityStatistics(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 清理过期的审计日志
     * @param retentionDays 保留天数
     * @return 清理的记录数
     */
    Mono<Long> cleanupExpiredLogs(int retentionDays);
    
    /**
     * 检查是否需要触发告警
     * @param eventType 事件类型
     * @param timeWindowMinutes 时间窗口（分钟）
     * @param threshold 阈值
     * @return 是否需要告警
     */
    Mono<Boolean> shouldTriggerAlert(String eventType, int timeWindowMinutes, int threshold);

    /**
     * 统计窗口内满足条件的事件条数。
     *
     * <p>在存储侧计数，不受「一次取回多少条」限制。告警判据必须用它：用「取回一批再在内存里过滤计数」
     * 会在窗口内事件数超过取回上限时漏报。</p>
     *
     * @param startTime 窗口开始
     * @param endTime 窗口结束
     * @param eventTypes 事件类型（非空）
     * @param clientIp 客户端 IP（null 表示不限）
     * @param success 成功标记（null 表示不限）
     * @return 事件条数
     */
    Mono<Long> countEvents(LocalDateTime startTime, LocalDateTime endTime,
                           List<AuditEventType> eventTypes, String clientIp, Boolean success);
}
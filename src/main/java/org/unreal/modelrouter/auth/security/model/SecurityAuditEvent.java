package org.unreal.modelrouter.auth.security.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 安全审计事件数据模型
 */
@Data
@Builder
public class SecurityAuditEvent {
    
    /**
     * 事件唯一标识符
     */
    private String eventId;
    
    /**
     * 事件类型
     */
    private String eventType;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 客户端IP地址
     */
    private String clientIp;
    
    /**
     * 用户代理字符串
     */
    private String userAgent;
    
    /**
     * 事件发生时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 访问的资源
     */
    private String resource;
    
    /**
     * 执行的操作
     */
    private String action;
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 失败原因（如果操作失败）
     */
    private String failureReason;
    
    /**
     * 附加说明（落 {@code security_audit.details} 列，纯文本，供审计页直接展示）。
     *
     * <p>与 {@link #additionalData} 的分工：这里放**一句话人能读的补充**（如「角色 AUDITOR」、
     * 「由管理端触发」），{@code additionalData} 放结构化 JSON（落 {@code metadata} 列）。
     * 历史上这个字段缺位，导致 {@code recordEvent} 通道落库的行 details 恒为空（issue #1186）。</p>
     */
    private String details;

    /**
     * 附加数据
     */
    private Map<String, Object> additionalData;
    
    /**
     * 请求ID（用于关联请求）
     */
    private String requestId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    // 手动添加 getter 方法，确保编译通过
    public String getEventId() {
        return eventId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public String getResource() {
        return resource;
    }
    
    public String getAction() {
        return action;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
}
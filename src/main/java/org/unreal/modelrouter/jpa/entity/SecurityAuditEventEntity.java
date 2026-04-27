package org.unreal.modelrouter.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.unreal.modelrouter.dto.AuditEventType;

import java.time.LocalDateTime;

/**
 * 安全审计事件实体类
 * 统一存储所有安全相关审计日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "security_audit", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_client_ip", columnList = "client_ip"),
    @Index(name = "idx_audit_category", columnList = "event_category"),
    @Index(name = "idx_audit_resource_id", columnList = "resource_id"),
    @Index(name = "idx_audit_risk_level", columnList = "risk_level")
})
public class SecurityAuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 事件唯一标识符
     */
    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    /**
     * 事件类型
     */
    @Column(name = "event_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    /**
     * 事件分类: JWT_TOKEN, API_KEY, SECURITY, AUTH, SYSTEM
     */
    @Column(name = "event_category", length = 50)
    private String eventCategory;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private String userId;

    /**
     * 资源ID（令牌ID或API Key ID）
     */
    @Column(name = "resource_id")
    private String resourceId;

    /**
     * 客户端IP地址
     */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /**
     * 用户代理字符串
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * 事件发生时间
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * 访问的资源
     */
    @Column(name = "resource", length = 500)
    private String resource;

    /**
     * 执行的操作
     */
    @Column(name = "action", length = 100)
    private String action;

    /**
     * 详细描述
     */
    @Column(name = "details", length = 1000)
    private String details;

    /**
     * 操作是否成功
     */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /**
     * 失败原因（如果操作失败）
     */
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /**
     * 额外元数据（JSON格式）
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * 请求ID（用于关联请求）
     */
    @Column(name = "request_id")
    private String requestId;

    /**
     * 会话ID
     */
    @Column(name = "session_id")
    private String sessionId;

    /**
     * IP地理位置
     */
    @Column(name = "geo_location", length = 100)
    private String geoLocation;

    /**
     * 设备信息
     */
    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    /**
     * 风险等级: LOW, MEDIUM, HIGH, CRITICAL
     */
    @Column(name = "risk_level", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        LOW,      // 低风险
        MEDIUM,   // 中风险
        HIGH,     // 高风险
        CRITICAL  // 严重风险
    }

    /**
     * 根据事件类型自动设置事件分类
     */
    @PrePersist
    public void prePersist() {
        if (this.eventCategory == null && this.eventType != null) {
            this.eventCategory = determineEventCategory(this.eventType);
        }
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
        if (this.riskLevel == null) {
            this.riskLevel = determineRiskLevel(this.eventType, this.success);
        }
    }

    /**
     * 根据事件类型确定事件分类
     */
    private String determineEventCategory(final AuditEventType type) {
        if (type == null) return "SYSTEM";
        
        String typeName = type.name();
        if (typeName.startsWith("JWT_TOKEN")) return "JWT_TOKEN";
        if (typeName.startsWith("API_KEY")) return "API_KEY";
        if (typeName.equals(AuditEventType.AUTHENTICATION_FAILED.name()) ||
            typeName.equals(AuditEventType.AUTHORIZATION_FAILED.name())) return "AUTH";
        if (typeName.equals(AuditEventType.SECURITY_ALERT.name()) ||
            typeName.equals(AuditEventType.SUSPICIOUS_ACTIVITY.name())) return "SECURITY";
        return "SYSTEM";
    }

    /**
     * 根据事件类型和成功状态确定风险等级
     */
    private RiskLevel determineRiskLevel(final AuditEventType type, final Boolean success) {
        if (type == null) return RiskLevel.LOW;
        
        // 失败事件风险较高
        if (success != null && !success) {
            if (type == AuditEventType.SECURITY_ALERT) return RiskLevel.CRITICAL;
            if (type == AuditEventType.SUSPICIOUS_ACTIVITY) return RiskLevel.HIGH;
            if (type == AuditEventType.AUTHORIZATION_FAILED) return RiskLevel.MEDIUM;
            return RiskLevel.LOW;
        }
        
        // 成功事件风险较低
        if (type == AuditEventType.JWT_TOKEN_REVOKED) return RiskLevel.MEDIUM;
        if (type == AuditEventType.API_KEY_REVOKED) return RiskLevel.MEDIUM;
        
        return RiskLevel.LOW;
    }
}
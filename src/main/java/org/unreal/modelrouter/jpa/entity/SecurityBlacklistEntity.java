package org.unreal.modelrouter.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 统一安全黑名单实体
 * 支持多种类型: TOKEN, IP, DEVICE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "security_blacklist", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"blacklist_type", "target_value"})
})
public class SecurityBlacklistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 黑名单类型: TOKEN, IP, DEVICE
     */
    @Column(name = "blacklist_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BlacklistType blacklistType;

    /**
     * 目标值: token_hash, IP地址, 设备标识
     */
    @Column(name = "target_value", nullable = false)
    private String targetValue;

    /**
     * SHA-256哈希值（用于快速匹配）
     */
    @Column(name = "target_hash")
    private String targetHash;

    /**
     * 关联用户ID
     */
    @Column(name = "user_id")
    private String userId;

    /**
     * 加入黑名单原因
     */
    @Column(name = "reason", length = 1000)
    private String reason;

    /**
     * 风险等级: LOW, MEDIUM, HIGH, CRITICAL
     */
    @Column(name = "risk_level", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    /**
     * 添加者
     */
    @Column(name = "added_by")
    private String addedBy;

    /**
     * 添加时间
     */
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    /**
     * 过期时间（NULL表示永久）
     */
    @Column(name = "expires_at", columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime expiresAt;

    /**
     * 状态: ACTIVE, EXPIRED, REMOVED
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BlacklistStatus status;

    /**
     * 来源: MANUAL, AUTO, SYSTEM
     */
    @Column(name = "source", length = 50)
    @Enumerated(EnumType.STRING)
    private BlacklistSource source;

    /**
     * 扩展元数据（JSON格式）
     */
    @Column(name = "metadata", length = 2000)
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 黑名单类型枚举
     */
    public enum BlacklistType {
        TOKEN,   // JWT令牌
        IP,      // IP地址
        DEVICE   // 设备信息
    }

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
     * 黑名单状态枚举
     */
    public enum BlacklistStatus {
        ACTIVE,   // 活跃
        EXPIRED,  // 已过期
        REMOVED   // 已移除
    }

    /**
     * 黑名单来源枚举
     */
    public enum BlacklistSource {
        MANUAL,   // 手动添加
        AUTO,     // 自动检测添加
        SYSTEM    // 系统添加
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // 永久有效
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 检查是否活跃
     */
    public boolean isActive() {
        return status == BlacklistStatus.ACTIVE && !isExpired();
    }
}
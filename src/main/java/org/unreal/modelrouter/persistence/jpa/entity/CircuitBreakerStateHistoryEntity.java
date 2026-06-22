package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker.State;

import java.time.LocalDateTime;

/**
 * 熔断器状态变化历史记录实体
 * 
 * v2.6.13: 新增，用于记录熔断器状态变化历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circuit_breaker_state_history", indexes = {
    @Index(name = "idx_instance_id", columnList = "instance_id"),
    @Index(name = "idx_changed_at", columnList = "changed_at"),
    @Index(name = "idx_instance_changed", columnList = "instance_id, changed_at")
})
public class CircuitBreakerStateHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 实例 ID（UUID 格式）
     */
    @Column(name = "instance_id", nullable = false, length = 64)
    private String instanceId;

    /**
     * 实例名称（冗余存储，便于查询）
     */
    @Column(name = "instance_name", length = 255)
    private String instanceName;

    /**
     * 服务类型
     */
    @Column(name = "service_type", length = 32)
    private String serviceType;

    /**
     * 变化前的状态
     */
    @Column(name = "previous_state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private State previousState;

    /**
     * 变化后的状态
     */
    @Column(name = "current_state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private State currentState;

    /**
     * 触发原因
     * FAILURE_THRESHOLD: 失败次数达到阈值
     * SUCCESS_THRESHOLD: 成功次数达到阈值
     * TIMEOUT: 超时后自动进入 HALF_OPEN
     * MANUAL_RESET: 手动重置
     * MANUAL_FORCE_OPEN: 手动强制打开
     * MANUAL_FORCE_CLOSE: 手动强制关闭
     */
    @Column(name = "trigger_reason", length = 32)
    private String triggerReason;

    /**
     * 状态变化时的失败计数
     */
    @Column(name = "failure_count")
    private Integer failureCount;

    /**
     * 状态变化时的成功计数
     */
    @Column(name = "success_count")
    private Integer successCount;

    /**
     * 变化时间
     */
    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;
}

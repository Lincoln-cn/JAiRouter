package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 熔断器统计数据实体
 * 
 * 用于自适应阈值调整，记录时间窗口内的调用统计数据
 * 
 * @author JAiRouter Team
 * @since v2.6.12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circuit_breaker_metrics", indexes = {
    @Index(name = "idx_instance_id", columnList = "instance_id"),
    @Index(name = "idx_window_end", columnList = "window_end")
})
public class CircuitBreakerMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 实例 ID (UUID 格式)
     */
    @Column(name = "instance_id", nullable = false, length = 64)
    private String instanceId;

    /**
     * 实例名称
     */
    @Column(name = "instance_name", length = 255)
    private String instanceName;

    /**
     * 服务类型
     */
    @Column(name = "service_type", length = 32)
    private String serviceType;

    /**
     * 时间窗口开始时间
     */
    @Column(name = "window_start")
    private LocalDateTime windowStart;

    /**
     * 时间窗口结束时间
     */
    @Column(name = "window_end")
    private LocalDateTime windowEnd;

    /**
     * 总调用次数
     */
    @Column(name = "total_calls", nullable = false)
    private Long totalCalls = 0L;

    /**
     * 失败调用次数
     */
    @Column(name = "failure_calls", nullable = false)
    private Long failureCalls = 0L;

    /**
     * 成功调用次数
     */
    @Column(name = "success_calls", nullable = false)
    private Long successCalls = 0L;

    /**
     * 失败率 (0.0 - 1.0)
     */
    @Column(name = "failure_rate")
    private Double failureRate = 0.0;

    /**
     * 平均响应时间 (毫秒)
     */
    @Column(name = "avg_response_time_ms")
    private Long avgResponseTimeMs = 0L;

    /**
     * 当前失败阈值
     */
    @Column(name = "current_failure_threshold")
    private Integer currentFailureThreshold = 5;

    /**
     * 调整后的失败阈值
     */
    @Column(name = "adjusted_failure_threshold")
    private Integer adjustedFailureThreshold;

    /**
     * 调整原因
     */
    @Column(name = "adjustment_reason", length = 512)
    private String adjustmentReason;

    /**
     * 是否已应用调整
     */
    @Column(name = "adjustment_applied")
    private Boolean adjustmentApplied = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

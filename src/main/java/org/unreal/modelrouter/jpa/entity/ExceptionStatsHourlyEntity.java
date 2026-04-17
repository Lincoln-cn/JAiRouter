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
 * 异常统计小时表实体类
 * 按小时聚合异常统计信息
 * 
 * @author JAiRouter Team
 * @since 1.9.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exception_stats_hourly", indexes = {
    @Index(name = "idx_exception_stats_hour_ts", columnList = "hour_timestamp"),
    @Index(name = "idx_exception_stats_hour_type", columnList = "exception_type"),
    @Index(name = "idx_exception_stats_hour_category", columnList = "error_category"),
    @Index(name = "idx_exception_stats_hour_operation", columnList = "operation")
}, uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_exception_stats_hour",
        columnNames = {"hour_timestamp", "exception_type", "error_code", "operation"}
    )
})
public class ExceptionStatsHourlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 小时时间戳（截断到小时）
     */
    @Column(name = "hour_timestamp", nullable = false)
    private LocalDateTime hourTimestamp;

    /**
     * 异常类型
     */
    @Column(name = "exception_type", nullable = false, length = 500)
    private String exceptionType;

    /**
     * 错误代码
     */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    /**
     * 错误分类
     */
    @Column(name = "error_category", length = 50)
    private String errorCategory;

    /**
     * 操作名称
     */
    @Column(name = "operation", length = 255)
    private String operation;

    /**
     * 服务名称
     */
    @Column(name = "service_name", length = 100)
    private String serviceName;

    /**
     * 总次数
     */
    @Column(name = "total_count", nullable = false)
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * 成功次数
     */
    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Long successCount = 0L;

    /**
     * 失败次数
     */
    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private Long failureCount = 0L;

    /**
     * 唯一追踪 ID 数量
     */
    @Column(name = "unique_trace_ids", nullable = false)
    @Builder.Default
    private Long uniqueTraceIds = 0L;

    /**
     * 唯一客户端 IP 数量
     */
    @Column(name = "unique_client_ips", nullable = false)
    @Builder.Default
    private Long uniqueClientIps = 0L;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 增加计数
     */
    public void incrementTotal() {
        this.totalCount = this.totalCount + 1;
    }

    /**
     * 增加成功计数
     */
    public void incrementSuccess() {
        this.successCount = this.successCount + 1;
    }

    /**
     * 增加失败计数
     */
    public void incrementFailure() {
        this.failureCount = this.failureCount + 1;
    }
}

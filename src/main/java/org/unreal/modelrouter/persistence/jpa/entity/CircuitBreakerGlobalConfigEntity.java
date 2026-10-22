package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 熔断器全局配置实体
 * 
 * 单行配置表，ID 固定为 1
 * 
 * v2.6.13: 新增，支持全局熔断器配置管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circuit_breaker_global_config")
public class CircuitBreakerGlobalConfigEntity {

    /**
     * 固定 ID，全局只有一条配置
     */
    @Id
    @Column(name = "id")
    private Long id = 1L;

    /**
     * 启用自适应阈值调整
     * 根据历史数据自动调整失败阈值
     */
    @Column(name = "adaptive_threshold_enabled")
    private Boolean adaptiveThresholdEnabled = false;

    /**
     * 状态同步间隔（分钟）。
     *
     * <p>历史遗留列：全仓没有任何消费方（没有定时任务读它），因此从 #554 起已从配置 DTO、
     * 服务层与前端移除，不再可设置、不再回显。字段本身保留映射，是因为
     * {@code h2/V1__baseline.sql} 已发布且不可回改（Flyway 按校验和判定），
     * 删字段会让 {@code H2BaselineMatchesEntityModelTest} 报 schema 漂移；
     * 真要删列需另加一份增量迁移，属独立变更。</p>
     */
    @Deprecated
    @Column(name = "state_sync_interval_minutes")
    private Integer stateSyncIntervalMinutes = 5;

    /**
     * 过期清理间隔（分钟）
     * 清理过期历史记录的间隔
     */
    @Column(name = "cleanup_interval_minutes")
    private Integer cleanupIntervalMinutes = 30;

    /**
     * 历史记录保留天数
     */
    @Column(name = "history_retention_days")
    private Integer historyRetentionDays = 30;

    /**
     * 默认失败阈值
     */
    @Column(name = "default_failure_threshold")
    private Integer defaultFailureThreshold = 5;

    /**
     * 默认成功阈值
     */
    @Column(name = "default_success_threshold")
    private Integer defaultSuccessThreshold = 2;

    /**
     * 默认超时时间（毫秒）
     */
    @Column(name = "default_timeout_ms")
    private Long defaultTimeoutMs = 60000L;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

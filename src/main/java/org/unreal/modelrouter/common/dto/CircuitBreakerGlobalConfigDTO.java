package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 熔断器全局配置 DTO
 * 
 * v2.6.13: 新增，用于前端全局配置管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerGlobalConfigDTO {

    /**
     * 启用自适应阈值调整
     */
    private Boolean adaptiveThresholdEnabled;

    /**
     * 状态同步间隔（分钟）
     */
    private Integer stateSyncIntervalMinutes;

    /**
     * 过期清理间隔（分钟）
     */
    private Integer cleanupIntervalMinutes;

    /**
     * 历史记录保留天数
     */
    private Integer historyRetentionDays;

    /**
     * 默认失败阈值
     */
    private Integer defaultFailureThreshold;

    /**
     * 默认成功阈值
     */
    private Integer defaultSuccessThreshold;

    /**
     * 默认超时时间（毫秒）
     */
    private Long defaultTimeoutMs;
}

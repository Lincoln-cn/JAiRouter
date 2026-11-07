package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实例熔断器配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class InstanceCircuitBreakerDTO {

    private Long id;

    /**
     * 所属实例的稳定唯一标识（UUID），与 {@link ServiceInstanceDTO#getInstanceId()} 一致。
     * v3.8.0 起由数据库主键（Long）改为 UUID（String）。
     */
    private String instanceId;
    private Boolean enabled;
    private Integer failureThreshold;
    private Integer timeout;
    private Integer successThreshold;

    /**
     * issue #1010：运行时熔断状态（CLOSED/OPEN/HALF_OPEN）；该实例无运行时熔断器时为 null。
     */
    private String circuitState;

    /**
     * issue #1010：当前熔断打开时刻（epoch 毫秒）；未打开或无运行时熔断器时为 null/0。
     */
    private Long openedAt;

    /**
     * issue #1010：最近一次真实失败原因标识（如 CONNECT_FAILED）；无失败详情时为 null。
     */
    private String lastFailureReason;
}
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
public class InstanceCircuitBreakerDTO {

    private Long id;
    private Long instanceId;
    private Boolean enabled;
    private Integer failureThreshold;
    private Integer timeout;
    private Integer successThreshold;
}
package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实例限流配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class InstanceRateLimitDTO {

    private Long id;

    /**
     * 所属实例的稳定唯一标识（UUID），与 {@link ServiceInstanceDTO#getInstanceId()} 一致。
     * v3.8.0 起由数据库主键（Long）改为 UUID（String）。
     */
    private String instanceId;
    private Boolean enabled;
    private String algorithm;
    private Integer capacity;
    private Integer rate;
    private String scope;
    private String key;
    private Boolean clientIpEnable;
}
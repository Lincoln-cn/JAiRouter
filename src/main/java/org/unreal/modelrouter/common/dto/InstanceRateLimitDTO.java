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
public class InstanceRateLimitDTO {

    private Long id;
    private Long instanceId;
    private Boolean enabled;
    private String algorithm;
    private Integer capacity;
    private Integer rate;
    private String scope;
    private String key;
    private Boolean clientIpEnable;
}
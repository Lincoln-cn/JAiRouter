package org.unreal.modelrouter.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 熔断器配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CircuitBreakerConfig {

    /**
     * 是否启用熔断器
     */
    private Boolean enabled;

    /**
     * 失败阈值（触发熔断的连续失败次数）
     */
    private Integer failureThreshold;

    /**
     * 熔断超时时间（毫秒）
     */
    private Integer timeout;

    /**
     * 成功阈值（半开状态下恢复所需的连续成功次数）
     */
    private Integer successThreshold;
}
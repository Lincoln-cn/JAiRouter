package org.unreal.modelrouter.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 负载均衡配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoadBalanceConfig {

    /**
     * 负载均衡类型：round-robin, weighted, least-connections, random
     */
    private String type;

    /**
     * 哈希算法（用于一致性哈希）
     */
    private String hashAlgorithm;
}
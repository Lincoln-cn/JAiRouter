package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务配置 DTO - 用于服务配置管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfigDTO {
    
    /**
     * 服务类型
     */
    private String serviceType;
    
    /**
     * 适配器
     */
    private String adapter;
    
    /**
     * 负载均衡类型
     */
    private String loadBalanceType;
    
    /**
     * 负载均衡哈希算法
     */
    private String loadBalanceHashAlgorithm;
    
    /**
     * 说明
     */
    private String description;
}

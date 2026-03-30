package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实例更新请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceUpdateRequest {
    
    /**
     * 实例 ID
     */
    private String instanceId;
    
    /**
     * 实例名称
     */
    private String name;
    
    /**
     * 基础 URL
     */
    private String baseUrl;
    
    /**
     * 路径
     */
    private String path;
    
    /**
     * 权重
     */
    private Integer weight;
    
    /**
     * 状态：active, inactive
     */
    private String status;
    
    /**
     * 请求头
     */
    private Object headers;
    
    /**
     * 是否启用限流
     */
    private Boolean rateLimitEnabled;
    
    /**
     * 限流算法
     */
    private String rateLimitAlgorithm;
    
    /**
     * 限流容量
     */
    private Integer rateLimitCapacity;
    
    /**
     * 限流速率
     */
    private Integer rateLimitRate;
    
    /**
     * 限流范围
     */
    private String rateLimitScope;
    
    /**
     * 是否启用客户端 IP
     */
    private Boolean rateLimitClientIpEnable;
}

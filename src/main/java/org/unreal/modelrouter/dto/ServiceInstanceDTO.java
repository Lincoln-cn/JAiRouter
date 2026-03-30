package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务实例 DTO - 用于服务实例管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstanceDTO {
    
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
     * 健康状态
     */
    private String healthStatus;
    
    /**
     * 请求头
     */
    private Object headers;
    
    /**
     * 限流配置
     */
    private RateLimitDTO rateLimit;
    
    /**
     * 限流配置 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitDTO {
        private Boolean enabled;
        private String algorithm;
        private Integer capacity;
        private Integer rate;
        private String scope;
        private String key;
        private Boolean clientIpEnable;
    }
}

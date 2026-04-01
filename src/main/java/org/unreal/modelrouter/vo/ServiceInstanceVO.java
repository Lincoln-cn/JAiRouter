package org.unreal.modelrouter.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务实例 VO - 用于返回实例信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstanceVO {
    
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
    private RateLimitVO rateLimit;
    
    /**
     * 熔断器配置
     */
    private CircuitBreakerVO circuitBreaker;
    
    /**
     * 限流配置 VO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitVO {
        private Boolean enabled;
        private String algorithm;
        private Integer capacity;
        private Integer rate;
        private String scope;
        private String key;
        private Boolean clientIpEnable;
    }
    
    /**
     * 熔断器配置 VO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitBreakerVO {
        private Boolean enabled;
        private Integer failureThreshold;
        private Integer timeout;
        private Integer successThreshold;
    }
}

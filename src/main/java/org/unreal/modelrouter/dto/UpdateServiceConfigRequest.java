package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新服务配置请求 DTO
 * 用于前端更新服务配置（不含实例）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateServiceConfigRequest {

    /**
     * 适配器类型
     */
    private String adapter;

    /**
     * 负载均衡配置
     */
    private LoadBalanceConfig loadBalance;

    /**
     * 限流配置
     */
    private RateLimitConfig rateLimit;

    /**
     * 熔断器配置
     */
    private CircuitBreakerConfig circuitBreaker;

    /**
     * 回退配置
     */
    private Object fallback;
}
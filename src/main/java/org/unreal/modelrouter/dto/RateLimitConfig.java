package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 限流配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RateLimitConfig {

    /**
     * 是否启用限流
     */
    private Boolean enabled;

    /**
     * 限流算法：token-bucket, sliding-window, leaky-bucket
     */
    private String algorithm;

    /**
     * 容量
     */
    private Integer capacity;

    /**
     * 速率（每秒请求数）
     */
    private Integer rate;

    /**
     * 限流范围：service, instance, global
     */
    private String scope;

    /**
     * 限流键（自定义键）
     */
    private String key;

    /**
     * 是否启用客户端IP限流
     */
    private Boolean clientIpEnable;
}
package org.unreal.modelrouter.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务实例 VO - 用于返回实例信息
 *
 * <p>实例列表的读模型：基础信息 + 请求头 + 嵌套的限流与熔断配置视图。
 * <p><b>Deprecated / zero consumers:</b> nothing outside this file references it &mdash; the main + test
 * reference count is 0 (scan on 2026-10-20).
 *
 * <p><b>What it used to do:</b> the read model for instance listing &mdash; string {@code instanceId},
 * name / baseUrl / path / weight / status / healthStatus, raw {@code headers}, and nested rate-limit and
 * circuit-breaker views. Instance responses now use {@link ServiceInstanceDTO} (built by
 * {@code ServiceInstanceManager.convertToDTO}, returned by {@code ServiceTypeInstanceController}), so no
 * controller emits this VO shape any more.
 *
 * <p><b>Before deleting:</b> it is a response VO &mdash; re-check any documented JSON shape for
 * {@code rateLimit} / {@code circuitBreaker} (and the frontend's instance types, out of scope here)
 * before deleting.
 *
 * @deprecated No caller remains in main or test sources (scan 2026-10-20); kept only until the dead-code
 *             sweep deletes it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(forRemoval = true)
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

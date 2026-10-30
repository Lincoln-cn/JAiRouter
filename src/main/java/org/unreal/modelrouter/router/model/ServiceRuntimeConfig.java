package org.unreal.modelrouter.router.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务运行时配置
 * 用于缓存解析后的服务配置，提高运行时性能
 */
public final class ServiceRuntimeConfig {
    private List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
    private String adapter;
    private ModelRouterProperties.LoadBalanceConfig loadBalanceConfig;
    private ModelRouterProperties.RateLimitConfig rateLimitConfig;
    private ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig;
    private ModelRouterProperties.FallbackConfig fallbackConfig;

    public List<ModelRouterProperties.ModelInstance> getInstances() {
        return instances;
    }

    public void setInstances(final List<ModelRouterProperties.ModelInstance> instances) {
        this.instances = instances;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(final String adapter) {
        this.adapter = adapter;
    }

    public ModelRouterProperties.LoadBalanceConfig getLoadBalanceConfig() {
        return loadBalanceConfig;
    }

    public void setLoadBalanceConfig(final ModelRouterProperties.LoadBalanceConfig loadBalanceConfig) {
        this.loadBalanceConfig = loadBalanceConfig;
    }

    public ModelRouterProperties.RateLimitConfig getRateLimitConfig() {
        return rateLimitConfig;
    }

    public void setRateLimitConfig(final ModelRouterProperties.RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    public ModelRouterProperties.CircuitBreakerConfig getCircuitBreakerConfig() {
        return circuitBreakerConfig;
    }

    public void setCircuitBreakerConfig(final ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig) {
        this.circuitBreakerConfig = circuitBreakerConfig;
    }

    /**
     * #819：快照复制的浅副本 —— 实例列表独立复制，其余配置对象按不可变共享。
     *
     * <p>动态更新走「复制 → 改副本 → 单次发布」而不是就地 set，避免并发请求读到
     * 「实例已换、适配器还是旧的」这类半更新状态。</p>
     *
     * @return 与当前对象互不影响的可写副本
     */
    public ServiceRuntimeConfig copy() {
        final ServiceRuntimeConfig copy = new ServiceRuntimeConfig();
        copy.instances = new ArrayList<>(instances);
        copy.adapter = adapter;
        copy.loadBalanceConfig = loadBalanceConfig;
        copy.rateLimitConfig = rateLimitConfig;
        copy.circuitBreakerConfig = circuitBreakerConfig;
        copy.fallbackConfig = fallbackConfig;
        return copy;
    }

    public ModelRouterProperties.FallbackConfig getFallbackConfig() {
        return fallbackConfig;
    }

    public void setFallbackConfig(final ModelRouterProperties.FallbackConfig fallbackConfig) {
        this.fallbackConfig = fallbackConfig;
    }
}

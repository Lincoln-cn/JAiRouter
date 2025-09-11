package org.unreal.modelrouter.model;

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

    public void setInstances(List<ModelRouterProperties.ModelInstance> instances) {
        this.instances = instances;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public ModelRouterProperties.LoadBalanceConfig getLoadBalanceConfig() {
        return loadBalanceConfig;
    }

    public void setLoadBalanceConfig(ModelRouterProperties.LoadBalanceConfig loadBalanceConfig) {
        this.loadBalanceConfig = loadBalanceConfig;
    }

    public ModelRouterProperties.RateLimitConfig getRateLimitConfig() {
        return rateLimitConfig;
    }

    public void setRateLimitConfig(ModelRouterProperties.RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    public ModelRouterProperties.CircuitBreakerConfig getCircuitBreakerConfig() {
        return circuitBreakerConfig;
    }

    public void setCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig) {
        this.circuitBreakerConfig = circuitBreakerConfig;
    }

    public ModelRouterProperties.FallbackConfig getFallbackConfig() {
        return fallbackConfig;
    }

    public void setFallbackConfig(ModelRouterProperties.FallbackConfig fallbackConfig) {
        this.fallbackConfig = fallbackConfig;
    }
}

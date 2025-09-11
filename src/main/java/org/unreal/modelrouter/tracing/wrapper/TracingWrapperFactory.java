package org.unreal.modelrouter.tracing.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.ratelimit.RateLimiter;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

/**
 * 追踪包装器工厂
 * 
 * 负责创建各种组件的追踪包装器，包括：
 * - LoadBalancer追踪包装器
 * - RateLimiter追踪包装器
 * - CircuitBreaker追踪包装器
 * 
 * 支持条件创建，可以通过配置开关控制是否启用追踪功能。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jairouter.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingWrapperFactory {
    
    private final StructuredLogger structuredLogger;
    private final TracingConfiguration tracingConfiguration;
    
    /**
     * 创建LoadBalancer追踪包装器
     * 
     * @param delegate 原始LoadBalancer实例
     * @return 包装后的LoadBalancer，如果追踪未启用则返回原始实例
     */
    public LoadBalancer wrapLoadBalancer(LoadBalancer delegate) {
        if (!isComponentTracingEnabled("loadbalancer") || delegate == null) {
            log.debug("LoadBalancer追踪功能未启用或实例为空，返回原始实例");
            return delegate;
        }
        
        // 避免重复包装
        if (delegate instanceof LoadBalancerTracingWrapper) {
            log.debug("LoadBalancer已经被包装，返回原实例");
            return delegate;
        }
        
        try {
            LoadBalancerTracingWrapper wrapper = new LoadBalancerTracingWrapper(delegate, structuredLogger);
            log.info("创建LoadBalancer追踪包装器: {}", delegate.getClass().getSimpleName());
            return wrapper;
        } catch (Exception e) {
            log.warn("创建LoadBalancer追踪包装器失败，返回原始实例: {}", e.getMessage(), e);
            return delegate;
        }
    }
    
    /**
     * 创建RateLimiter追踪包装器
     * 
     * @param delegate 原始RateLimiter实例
     * @return 包装后的RateLimiter，如果追踪未启用则返回原始实例
     */
    public RateLimiter wrapRateLimiter(RateLimiter delegate) {
        if (!isComponentTracingEnabled("ratelimiter") || delegate == null) {
            log.debug("RateLimiter追踪功能未启用或实例为空，返回原始实例");
            return delegate;
        }
        
        // 避免重复包装
        if (delegate instanceof RateLimiterTracingWrapper) {
            log.debug("RateLimiter已经被包装，返回原实例");
            return delegate;
        }
        
        try {
            RateLimiterTracingWrapper wrapper = new RateLimiterTracingWrapper(delegate, structuredLogger);
            log.info("创建RateLimiter追踪包装器: {} (算法: {})", 
                    delegate.getClass().getSimpleName(), 
                    delegate.getConfig().getAlgorithm());
            return wrapper;
        } catch (Exception e) {
            log.warn("创建RateLimiter追踪包装器失败，返回原始实例: {}", e.getMessage(), e);
            return delegate;
        }
    }
    
    /**
     * 创建CircuitBreaker追踪包装器
     * 
     * @param delegate 原始CircuitBreaker实例
     * @param instanceId 实例ID
     * @return 包装后的CircuitBreaker，如果追踪未启用则返回原始实例
     */
    public CircuitBreaker wrapCircuitBreaker(CircuitBreaker delegate, String instanceId) {
        if (!isComponentTracingEnabled("circuitbreaker") || delegate == null) {
            log.debug("CircuitBreaker追踪功能未启用或实例为空，返回原始实例");
            return delegate;
        }
        
        // 避免重复包装
        if (delegate instanceof CircuitBreakerTracingWrapper) {
            log.debug("CircuitBreaker已经被包装，返回原实例");
            return delegate;
        }
        
        try {
            CircuitBreakerTracingWrapper wrapper = new CircuitBreakerTracingWrapper(
                    delegate, structuredLogger, instanceId);
            log.info("创建CircuitBreaker追踪包装器: {} (实例: {})", 
                    delegate.getClass().getSimpleName(), instanceId);
            return wrapper;
        } catch (Exception e) {
            log.warn("创建CircuitBreaker追踪包装器失败，返回原始实例: {}", e.getMessage(), e);
            return delegate;
        }
    }
    
    /**
     * 检查追踪功能是否启用
     * 
     * @return 如果追踪功能启用返回true
     */
    private boolean isTracingEnabled() {
        return tracingConfiguration != null && tracingConfiguration.isEnabled();
    }
    
    /**
     * 检查组件追踪是否启用
     * 
     * @param componentType 组件类型 (loadbalancer, ratelimiter, circuitbreaker)
     * @return 如果组件追踪启用返回true
     */
    private boolean isComponentTracingEnabled(String componentType) {
        if (!isTracingEnabled()) {
            return false;
        }
        
        // 根据配置检查具体组件的追踪开关
        return switch (componentType.toLowerCase()) {
            case "loadbalancer" -> tracingConfiguration.getComponents().getLoadBalancer().isEnabled();
            case "ratelimiter" -> tracingConfiguration.getComponents().getRateLimiter().isEnabled();
            case "circuitbreaker" -> tracingConfiguration.getComponents().getCircuitBreaker().isEnabled();
            default -> true; // 默认启用
        };
    }
    
    /**
     * 获取追踪包装器统计信息
     * 
     * @return 统计信息Map
     */
    public java.util.Map<String, Object> getWrapperStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("tracing_enabled", isTracingEnabled());
        stats.put("factory_class", this.getClass().getSimpleName());
        
        if (tracingConfiguration != null) {
            stats.put("service_name", tracingConfiguration.getServiceName());
            stats.put("service_version", tracingConfiguration.getServiceVersion());
        }
        
        return stats;
    }
}
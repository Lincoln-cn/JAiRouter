package org.unreal.modelrouter.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ConfigurationValidator;
import org.unreal.modelrouter.fallback.FallbackStrategy;
import org.unreal.modelrouter.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.fallback.impl.DefaultFallbackStrategy;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.IpHashLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.LeastConnectionsLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.RandomLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.RoundRobinLoadBalancer;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimiter;
import org.unreal.modelrouter.ratelimit.ScopedRateLimiterWrapper;
import org.unreal.modelrouter.ratelimit.impl.LeakyBucketRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.SlidingWindowRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.TokenBucketRateLimiter;
import org.unreal.modelrouter.tracing.wrapper.TracingWrapperFactory;

@Component
public class ComponentFactory {
    private static final Logger logger = LoggerFactory.getLogger(ComponentFactory.class);
    
    private final ConfigurationValidator configurationValidator;
    private final TracingWrapperFactory tracingWrapperFactory;

    @Autowired
    public ComponentFactory(ConfigurationValidator configurationValidator,
                           @Autowired(required = false) TracingWrapperFactory tracingWrapperFactory) {
        this.configurationValidator = configurationValidator;
        this.tracingWrapperFactory = tracingWrapperFactory;
        
        if (tracingWrapperFactory != null) {
            logger.info("追踪包装器工厂已启用，将为组件添加追踪功能");
        } else {
            logger.debug("追踪包装器工厂未启用，组件将不包含追踪功能");
        }
    }

    /* ---------------- LoadBalancer ---------------- */

    public LoadBalancer createLoadBalancer(ModelRouterProperties.LoadBalanceConfig config) {
        if (config == null) {
            logger.warn("Load balance config null, fallback to random");
            LoadBalancer loadBalancer = new RandomLoadBalancer();
            return wrapWithTracing(loadBalancer);
        }
        
        LoadBalancer loadBalancer = switch (config.getType().toLowerCase()) {
            case "random" -> new RandomLoadBalancer();
            case "round-robin" -> new RoundRobinLoadBalancer();
            case "least-connections" -> new LeastConnectionsLoadBalancer();
            case "ip-hash" -> new IpHashLoadBalancer(config.getHashAlgorithm());
            default -> {
                logger.warn("Unsupported load balancer: {}, fallback to random", config.getType());
                yield new RandomLoadBalancer();
            }
        };
        
        return wrapWithTracing(loadBalancer);
    }

    /* ---------------- RateLimiter Core ---------------- */

    public RateLimiter createRateLimiter(RateLimitConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) return null;
        
        RateLimiter rateLimiter = switch (cfg.getAlgorithm().toLowerCase()) {
            case "token-bucket" -> new TokenBucketRateLimiter(cfg);
            case "leaky-bucket" -> new LeakyBucketRateLimiter(cfg);
            case "sliding-window" -> new SlidingWindowRateLimiter(cfg);
            case "warm-up" -> new org.unreal.modelrouter.ratelimit.impl.WarmUpRateLimiter(cfg);
            default -> {
                logger.warn("Unknown algorithm: {}, fallback to token-bucket", cfg.getAlgorithm());
                yield new TokenBucketRateLimiter(cfg);
            }
        };
        
        return wrapWithTracing(rateLimiter);
    }

    /* ---------------- Scoped RateLimiter ---------------- */

    public RateLimiter createScopedRateLimiter(RateLimitConfig cfg) {
        RateLimiter scopedRateLimiter = new ScopedRateLimiterWrapper(cfg, this::createRateLimiter);
        return wrapWithTracing(scopedRateLimiter);
    }

    /* ---------------- Validation ---------------- */

    public boolean validateRateLimitConfig(RateLimitConfig cfg) {
        return configurationValidator.validateRateLimitConfig(cfg);
    }

    public boolean validateCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig cfg) {
        if (cfg == null) {
            return false;
        }
        
        if (cfg.getEnabled() == null) {
            return false;
        }
        
        if (cfg.getFailureThreshold() != null && cfg.getFailureThreshold() <= 0) {
            return false;
        }
        
        if (cfg.getTimeout() != null && cfg.getTimeout() <= 0) {
            return false;
        }
        
        if (cfg.getSuccessThreshold() != null && cfg.getSuccessThreshold() <= 0) {
            return false;
        }
        
        return true;
    }

    public boolean validateLoadBalanceConfig(ModelRouterProperties.LoadBalanceConfig cfg) {
        return configurationValidator.validateLoadBalanceConfig(cfg);
    }
    
    public boolean validateFallbackConfig(ModelRouterProperties.FallbackConfig cfg) {
        return cfg != null && cfg.getEnabled() != null;
    }
    
    /**
     * 创建降级策略
     * @param config 降级配置
     * @param serviceType 服务类型
     * @return 降级策略实现
     */
    public FallbackStrategy<ResponseEntity<?>> createFallbackStrategy(
            ModelRouterProperties.FallbackConfig config, 
            String serviceType) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        
        return switch (config.getStrategy().toLowerCase()) {
            case "cache" -> new CacheFallbackStrategy(serviceType, config.getCacheSize());
            case "default" -> new DefaultFallbackStrategy(serviceType);
            default -> new DefaultFallbackStrategy(serviceType);
        };
    }
    
    /* ---------------- CircuitBreaker ---------------- */
    
    /**
     * 创建熔断器
     * @param instanceId 实例ID
     * @param failureThreshold 失败阈值
     * @param timeout 超时时间
     * @param successThreshold 成功阈值
     * @return 熔断器实例
     */
    public org.unreal.modelrouter.circuitbreaker.CircuitBreaker createCircuitBreaker(
            String instanceId, int failureThreshold, long timeout, int successThreshold) {
        org.unreal.modelrouter.circuitbreaker.CircuitBreaker circuitBreaker = 
                new org.unreal.modelrouter.circuitbreaker.DefaultCircuitBreaker(
                        instanceId, failureThreshold, timeout, successThreshold);
        return wrapWithTracing(circuitBreaker, instanceId);
    }
    
    /* ---------------- 追踪包装器集成方法 ---------------- */
    
    /**
     * 为LoadBalancer添加追踪包装器
     * 
     * @param loadBalancer 原始LoadBalancer
     * @return 包装后的LoadBalancer
     */
    private LoadBalancer wrapWithTracing(LoadBalancer loadBalancer) {
        if (tracingWrapperFactory != null && loadBalancer != null) {
            try {
                return tracingWrapperFactory.wrapLoadBalancer(loadBalancer);
            } catch (Exception e) {
                logger.warn("为LoadBalancer添加追踪包装器失败: {}", e.getMessage(), e);
            }
        }
        return loadBalancer;
    }
    
    /**
     * 为RateLimiter添加追踪包装器
     * 
     * @param rateLimiter 原始RateLimiter
     * @return 包装后的RateLimiter
     */
    private RateLimiter wrapWithTracing(RateLimiter rateLimiter) {
        if (tracingWrapperFactory != null && rateLimiter != null) {
            try {
                return tracingWrapperFactory.wrapRateLimiter(rateLimiter);
            } catch (Exception e) {
                logger.warn("为RateLimiter添加追踪包装器失败: {}", e.getMessage(), e);
            }
        }
        return rateLimiter;
    }
    
    /**
     * 为CircuitBreaker添加追踪包装器
     * 
     * @param circuitBreaker 原始CircuitBreaker
     * @param instanceId 实例ID
     * @return 包装后的CircuitBreaker
     */
    private org.unreal.modelrouter.circuitbreaker.CircuitBreaker wrapWithTracing(
            org.unreal.modelrouter.circuitbreaker.CircuitBreaker circuitBreaker, String instanceId) {
        if (tracingWrapperFactory != null && circuitBreaker != null) {
            try {
                return tracingWrapperFactory.wrapCircuitBreaker(circuitBreaker, instanceId);
            } catch (Exception e) {
                logger.warn("为CircuitBreaker添加追踪包装器失败: {}", e.getMessage(), e);
            }
        }
        return circuitBreaker;
    }
    
    /**
     * 检查追踪功能是否启用
     * 
     * @return 如果追踪功能启用返回true
     */
    public boolean isTracingEnabled() {
        return tracingWrapperFactory != null;
    }
    
    /**
     * 获取组件工厂统计信息
     * 
     * @return 统计信息Map
     */
    public java.util.Map<String, Object> getFactoryStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("factory_class", this.getClass().getSimpleName());
        stats.put("tracing_enabled", isTracingEnabled());
        
        if (tracingWrapperFactory != null) {
            stats.putAll(tracingWrapperFactory.getWrapperStatistics());
        }
        
        return stats;
    }
}
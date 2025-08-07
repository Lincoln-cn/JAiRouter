package org.unreal.modelrouter.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.*;
import org.unreal.modelrouter.ratelimit.*;
import org.unreal.modelrouter.ratelimit.impl.*;
import org.unreal.modelrouter.fallback.FallbackStrategy;
import org.unreal.modelrouter.fallback.DefaultFallbackStrategy;
import org.unreal.modelrouter.fallback.CacheFallbackStrategy;

import org.springframework.http.ResponseEntity;

@Component
public class ComponentFactory {
    private static final Logger logger = LoggerFactory.getLogger(ComponentFactory.class);

    /* ---------------- LoadBalancer ---------------- */

    public LoadBalancer createLoadBalancer(ModelRouterProperties.LoadBalanceConfig config) {
        if (config == null) {
            logger.warn("Load balance config null, fallback to random");
            return new RandomLoadBalancer();
        }
        return switch (config.getType().toLowerCase()) {
            case "random" -> new RandomLoadBalancer();
            case "round-robin" -> new RoundRobinLoadBalancer();
            case "least-connections" -> new LeastConnectionsLoadBalancer();
            case "ip-hash" -> new IpHashLoadBalancer(config.getHashAlgorithm());
            default -> {
                logger.warn("Unsupported load balancer: {}, fallback to random", config.getType());
                yield new RandomLoadBalancer();
            }
        };
    }

    /* ---------------- RateLimiter Core ---------------- */

    public RateLimiter createRateLimiter(RateLimitConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) return null;
        return switch (cfg.getAlgorithm().toLowerCase()) {
            case "token-bucket" -> new TokenBucketRateLimiter(cfg);
            case "leaky-bucket" -> new LeakyBucketRateLimiter(cfg);
            case "sliding-window" -> new SlidingWindowRateLimiter(cfg);
            case "warm-up" -> new org.unreal.modelrouter.ratelimit.impl.WarmUpRateLimiter(cfg);
            default -> {
                logger.warn("Unknown algorithm: {}, fallback to token-bucket", cfg.getAlgorithm());
                yield new TokenBucketRateLimiter(cfg);
            }
        };
    }

    /* ---------------- Scoped RateLimiter ---------------- */

    public RateLimiter createScopedRateLimiter(RateLimitConfig cfg) {
        return new ScopedRateLimiterWrapper(cfg, this::createRateLimiter);
    }

    /* ---------------- Validation ---------------- */

    public boolean validateRateLimitConfig(RateLimitConfig cfg) {
        return cfg != null && cfg.getAlgorithm() != null && cfg.getCapacity() > 0 && cfg.getRate() > 0;
    }

    public boolean validateCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig cfg) {
        return cfg != null && cfg.getEnabled() != null && cfg.getFailureThreshold() > 0 && cfg.getTimeout() > 0;
    }

    public boolean validateLoadBalanceConfig(ModelRouterProperties.LoadBalanceConfig cfg) {
        return cfg != null && cfg.getType() != null;
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
}
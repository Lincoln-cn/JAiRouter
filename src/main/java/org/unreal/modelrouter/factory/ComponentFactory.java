package org.unreal.modelrouter.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.*;
import org.unreal.modelrouter.ratelimit.RateLimiter;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.impl.*;

/**
 * 统一组件工厂
 * 负责创建负载均衡器和限流器，消除重复的创建逻辑
 */
@Component
public class ComponentFactory {
    private static final Logger logger = LoggerFactory.getLogger(ComponentFactory.class);

    /**
     * 创建负载均衡器
     * 支持优雅降级，如果指定类型不支持则使用默认实现
     */
    public LoadBalancer createLoadBalancer(ModelRouterProperties.LoadBalanceConfig config) {
        if (config == null) {
            logger.warn("Load balance config is null, using default random load balancer");
            return new RandomLoadBalancer();
        }

        String type = config.getType() != null ? config.getType().toLowerCase() : "random";

        try {
            LoadBalancer loadBalancer = switch (type) {
                case "random" -> {
                    logger.debug("Creating RandomLoadBalancer");
                    yield new RandomLoadBalancer();
                }
                case "round-robin" -> {
                    logger.debug("Creating RoundRobinLoadBalancer");
                    yield new RoundRobinLoadBalancer();
                }
                case "least-connections" -> {
                    logger.debug("Creating LeastConnectionsLoadBalancer");
                    yield new LeastConnectionsLoadBalancer();
                }
                case "ip-hash" -> {
                    String hashAlgorithm = config.getHashAlgorithm() != null ?
                            config.getHashAlgorithm() : "md5";
                    logger.debug("Creating IpHashLoadBalancer with algorithm: {}", hashAlgorithm);
                    yield new IpHashLoadBalancer(hashAlgorithm);
                }
                default -> {
                    logger.warn("Unsupported load balance type: {}, fallback to random", type);
                    yield new RandomLoadBalancer();
                }
            };

            logger.info("Successfully created load balancer of type: {}", type);
            return loadBalancer;

        } catch (Exception e) {
            logger.error("Failed to create load balancer of type: {}, fallback to random. Error: {}",
                    type, e.getMessage());
            return new RandomLoadBalancer();
        }
    }

    /**
     * 创建限流器
     * 支持优雅降级，如果指定算法不支持则使用默认实现
     */
    public RateLimiter createRateLimiter(RateLimitConfig config) {
        if (config == null || !config.isEnabled()) {
            logger.debug("Rate limit config is null or disabled, no limiter created");
            return null;
        }

        String algorithm = config.getAlgorithm() != null ?
                config.getAlgorithm().toLowerCase() : "token-bucket";

        try {
            RateLimiter rateLimiter = switch (algorithm) {
                case "token-bucket" -> {
                    logger.debug("Creating TokenBucketRateLimiter with capacity: {}, rate: {}",
                            config.getCapacity(), config.getRate());
                    yield new TokenBucketRateLimiter(config);
                }
                case "leaky-bucket" -> {
                    logger.debug("Creating LeakyBucketRateLimiter with capacity: {}, rate: {}",
                            config.getCapacity(), config.getRate());
                    yield new LeakyBucketRateLimiter(config);
                }
                case "sliding-window" -> {
                    logger.debug("Creating SlidingWindowRateLimiter with rate: {}",
                            config.getRate());
                    yield new SlidingWindowRateLimiter(config);
                }
                default -> {
                    logger.warn("Unsupported rate limit algorithm: {}, fallback to token-bucket", algorithm);
                    yield new TokenBucketRateLimiter(config);
                }
            };

            logger.info("Successfully created rate limiter of type: {} with scope: {}",
                    algorithm, config.getScope());
            return rateLimiter;

        } catch (Exception e) {
            logger.error("Failed to create rate limiter of type: {}, fallback to token-bucket. Error: {}",
                    algorithm, e.getMessage());
            return new TokenBucketRateLimiter(config);
        }
    }

    /**
     * 批量创建负载均衡器
     * 为多个服务类型创建负载均衡器
     */
    public java.util.Map<org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType, LoadBalancer>
    createLoadBalancers(java.util.Map<org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType,
            ModelRouterProperties.LoadBalanceConfig> configs) {

        java.util.Map<org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType, LoadBalancer> result =
                new java.util.EnumMap<>(org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType.class);

        configs.forEach((serviceType, config) -> {
            try {
                LoadBalancer loadBalancer = createLoadBalancer(config);
                result.put(serviceType, loadBalancer);
                logger.debug("Created load balancer for service type: {}", serviceType);
            } catch (Exception e) {
                logger.error("Failed to create load balancer for service type: {}. Error: {}",
                        serviceType, e.getMessage());
                result.put(serviceType, new RandomLoadBalancer()); // 默认实现
            }
        });

        return result;
    }

    /**
     * 批量创建限流器
     * 为多个服务类型创建限流器
     */
    public java.util.Map<org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType, RateLimiter>
    createRateLimiters(java.util.Map<org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType,
            RateLimitConfig> configs) {

        java.util.Map<org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType, RateLimiter> result =
                new java.util.EnumMap<>(org.unreal.modelrouter.config.ModelServiceRegistry.ServiceType.class);

        configs.forEach((serviceType, config) -> {
            try {
                RateLimiter rateLimiter = createRateLimiter(config);
                if (rateLimiter != null) {
                    result.put(serviceType, rateLimiter);
                    logger.debug("Created rate limiter for service type: {}", serviceType);
                }
            } catch (Exception e) {
                logger.error("Failed to create rate limiter for service type: {}. Error: {}",
                        serviceType, e.getMessage());
            }
        });

        return result;
    }

    /**
     * 验证组件配置
     */
    public boolean validateLoadBalanceConfig(ModelRouterProperties.LoadBalanceConfig config) {
        if (config == null) return false;

        String type = config.getType();
        if (type == null) return false;

        String normalizedType = type.toLowerCase();
        return normalizedType.equals("random") ||
                normalizedType.equals("round-robin") ||
                normalizedType.equals("least-connections") ||
                normalizedType.equals("ip-hash");
    }

    /**
     * 验证限流配置
     */
    public boolean validateRateLimitConfig(RateLimitConfig config) {
        if (config == null || !config.isEnabled()) return true; // null是有效的

        return config.getAlgorithm() != null &&
                config.getCapacity() > 0 &&
                config.getRate() > 0;
    }
}
package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.DefaultCircuitBreaker;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.common.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.loadbalancer.utils.WeightCalculator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 随机负载均衡策略
 */
// 修改 RandomLoadBalancer 实现
public class RandomLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalancer.class);
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final int failureThreshold = 5;
    private final long timeout = 60000; // 60秒
    private final int successThreshold = 2;
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances, final String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances, final String clientIp,final String serviceType) {

        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for random selection");
            throw new IllegalArgumentException("No instances available");
        }

        // 过滤掉熔断状态的实例
        List<ModelRouterProperties.ModelInstance> availableInstances =
                instances.stream()
                        .filter(instance -> {
                            CircuitBreaker cb = circuitBreakers.computeIfAbsent(
                                    instance.getBaseUrl(),
                                    id -> new DefaultCircuitBreaker(id, failureThreshold, timeout, successThreshold));
                            return cb.canExecute();
                        })
                        .collect(Collectors.toList());

        if (availableInstances.isEmpty()) {
            logger.warn("No available instances (all are in circuit breaker state)");
            throw new RuntimeException("All instances are unavailable");
        }

        // 考虑权重的随机选择 - 使用安全的权重计算
        long totalWeight = WeightCalculator.calculateTotalWeight(availableInstances);

        if (totalWeight <= 0) {
            // 如果没有权重，使用简单随机
            ModelRouterProperties.ModelInstance selected =
                    availableInstances.get(random.nextInt(availableInstances.size()));
            logger.debug("Selected instance {} using simple random strategy for service {}", selected.getName(), serviceType);
            recordLoadBalancerSelection(serviceType, "random", selected.getName());
            return selected;
        }

        long randomWeight;
        try {
            randomWeight = (long) (java.security.SecureRandom.getInstanceStrong().nextDouble() * totalWeight);
        } catch (java.security.NoSuchAlgorithmException e) {
            // 如果安全随机数算法不可用，回退到普通随机数
            randomWeight = (long) (Math.random() * totalWeight);
            logger.warn("SecureRandom algorithm not available, using Math.random() as fallback: {}", e.getMessage());
        }
        long currentWeight = 0;

        for (ModelRouterProperties.ModelInstance instance : availableInstances) {
            currentWeight += Math.max(0, instance.getWeight());
            if (randomWeight < currentWeight) {
                logger.debug("Selected instance {} using weighted random strategy for service {}", instance.getName(), serviceType);
                recordLoadBalancerSelection(serviceType, "random", instance.getName());
                return instance;
            }
        }

        // 兜底返回最后一个
        ModelRouterProperties.ModelInstance lastInstance =
                availableInstances.get(availableInstances.size() - 1);
        logger.debug("Fallback selection of instance {} for service {}", lastInstance.getName(), serviceType);
        recordLoadBalancerSelection(serviceType, "random", lastInstance.getName());
        return lastInstance;
    }

    @Override
    public void recordCallComplete(final ModelRouterProperties.ModelInstance instance) {
        CircuitBreaker cb = circuitBreakers.get(instance.getBaseUrl());
        if (cb != null) {
            cb.onSuccess();
        }
    }

    @Override
    public void recordCallFailure(final ModelRouterProperties.ModelInstance instance) {
        CircuitBreaker cb = circuitBreakers.get(instance.getBaseUrl());
        if (cb != null) {
            cb.onFailure();
        }
    }

    /**
     * 记录负载均衡器选择指标
     */
    private void recordLoadBalancerSelection(final String service, final String strategy,final String selectedInstance) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordLoadBalancer(service, strategy, selectedInstance);
            } catch (Exception e) {
                logger.warn("Failed to record load balancer metrics: {}", e.getMessage());
            }
        }
    }
}
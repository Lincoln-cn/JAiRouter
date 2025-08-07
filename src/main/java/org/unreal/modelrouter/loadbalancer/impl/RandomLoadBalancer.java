package org.unreal.modelrouter.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.circuitbreaker.DefaultCircuitBreaker;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 随机负载均衡策略
 */
// 修改 RandomLoadBalancer 实现
public class RandomLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalancer.class);
    private final Random random = new Random();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final int failureThreshold = 5;
    private final long timeout = 60000; // 60秒
    private final int successThreshold = 2;

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            List<ModelRouterProperties.ModelInstance> instances, String clientIp) {

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

        // 考虑权重的随机选择
        int totalWeight = availableInstances.stream()
                .mapToInt(ModelRouterProperties.ModelInstance::getWeight).sum();

        if (totalWeight <= 0) {
            // 如果没有权重，使用简单随机
            ModelRouterProperties.ModelInstance selected =
                    availableInstances.get(random.nextInt(availableInstances.size()));
            logger.debug("Selected instance {} using simple random strategy", selected.getName());
            return selected;
        }

        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (ModelRouterProperties.ModelInstance instance : availableInstances) {
            currentWeight += instance.getWeight();
            if (randomWeight < currentWeight) {
                logger.debug("Selected instance {} using weighted random strategy", instance.getName());
                return instance;
            }
        }

        // 兜底返回最后一个
        ModelRouterProperties.ModelInstance lastInstance =
                availableInstances.get(availableInstances.size() - 1);
        logger.debug("Fallback selection of instance {}", lastInstance.getName());
        return lastInstance;
    }

    @Override
    public void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
        CircuitBreaker cb = circuitBreakers.get(instance.getBaseUrl());
        if (cb != null) {
            cb.onSuccess();
        }
    }

    @Override
    public void recordCallFailure(ModelRouterProperties.ModelInstance instance) {
        CircuitBreaker cb = circuitBreakers.get(instance.getBaseUrl());
        if (cb != null) {
            cb.onFailure();
        }
    }
}
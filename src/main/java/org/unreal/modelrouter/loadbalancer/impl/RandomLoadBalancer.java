package org.unreal.modelrouter.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡策略
 */
public class RandomLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalancer.class);
    private final Random random = new Random();

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for random selection");
            throw new IllegalArgumentException("No instances available");
        }

        // 考虑权重的随机选择
        int totalWeight = instances.stream().mapToInt(ModelRouterProperties.ModelInstance::getWeight).sum();
        if (totalWeight <= 0) {
            // 如果没有权重，使用简单随机
            ModelRouterProperties.ModelInstance selected = instances.get(random.nextInt(instances.size()));
            logger.debug("Selected instance {} using simple random strategy", selected.getName());
            return selected;
        }

        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (ModelRouterProperties.ModelInstance instance : instances) {
            currentWeight += instance.getWeight();
            if (randomWeight < currentWeight) {
                logger.debug("Selected instance {} using weighted random strategy", instance.getName());
                return instance;
            }
        }

        // 兜底返回最后一个
        ModelRouterProperties.ModelInstance lastInstance = instances.get(instances.size() - 1);
        logger.debug("Fallback selection of instance {}", lastInstance.getName());
        return lastInstance;
    }
}
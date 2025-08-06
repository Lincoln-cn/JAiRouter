package org.unreal.modelrouter.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡策略
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for round robin selection");
            throw new IllegalArgumentException("No instances available");
        }

        String key = generateInstancesKey(instances);
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));

        // 考虑权重的轮询
        List<ModelRouterProperties.ModelInstance> weightedInstances = createWeightedList(instances);
        int index = Math.abs(counter.getAndIncrement() % weightedInstances.size());
        ModelRouterProperties.ModelInstance selected = weightedInstances.get(index);

        logger.debug("Selected instance {} using round robin strategy", selected.getName());
        return selected;
    }

    private String generateInstancesKey(List<ModelRouterProperties.ModelInstance> instances) {
        return instances.stream()
                .map(i -> i.getBaseUrl() + ":" + i.getPath())
                .sorted()
                .reduce("", (a, b) -> a + "," + b);
    }

    private List<ModelRouterProperties.ModelInstance> createWeightedList(List<ModelRouterProperties.ModelInstance> instances) {
        List<ModelRouterProperties.ModelInstance> weightedList = new ArrayList<>();
        for (ModelRouterProperties.ModelInstance instance : instances) {
            int weight = Math.max(1, instance.getWeight());
            for (int i = 0; i < weight; i++) {
                weightedList.add(instance);
            }
        }
        return weightedList;
    }
}

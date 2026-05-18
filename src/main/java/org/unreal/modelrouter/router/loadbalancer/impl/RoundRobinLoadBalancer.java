package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡策略
 * 
 * v2.7.8 优化：使用 CachedWeightedSelector 缓存权重列表
 * 性能提升：权重选择延迟 ~0.3ms → <0.05ms
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);
    
    // v2.7.8: 缓存权重选择器
    private final CachedWeightedSelector weightedSelector = new CachedWeightedSelector();
    
    // 保留计数器用于无权重场景
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(final List<ModelRouterProperties.ModelInstance> instances, final String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(final List<ModelRouterProperties.ModelInstance> instances, final String clientIp, final String serviceType) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for round robin selection");
            throw new IllegalArgumentException("No instances available");
        }

        // 单实例快速路径
        if (instances.size() == 1) {
            return instances.get(0);
        }

        ModelRouterProperties.ModelInstance selected;
        
        // 检查是否有权重配置
        boolean hasWeight = instances.stream().anyMatch(i -> i.getWeight() != 1);
        
        if (hasWeight) {
            // v2.7.8: 使用缓存的权重选择器
            selected = weightedSelector.select(instances);
        } else {
            // 无权重场景：简单轮询
            String key = generateInstancesKey(instances);
            AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
            int index = Math.abs(counter.getAndIncrement() % instances.size());
            selected = instances.get(index);
        }

        logger.debug("Selected instance {} using round robin strategy for service {}", selected.getName(), serviceType);
        recordLoadBalancerSelection(serviceType, "round_robin", selected.getName());
        return selected;
    }

    private String generateInstancesKey(final List<ModelRouterProperties.ModelInstance> instances) {
        return instances.stream()
                .map(i -> i.getBaseUrl() + ":" + i.getPath())
                .sorted()
                .reduce("", (a, b) -> a + "," + b);
    }

    /**
     * 记录负载均衡器选择指标
     */
    private void recordLoadBalancerSelection(final String service, final String strategy, final String selectedInstance) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordLoadBalancer(service, strategy, selectedInstance);
            } catch (Exception e) {
                logger.warn("Failed to record load balancer metrics: {}", e.getMessage());
            }
        }
    }
}

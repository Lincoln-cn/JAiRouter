package org.unreal.modelrouter.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.monitoring.MetricsCollector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 最少连接数负载均衡策略
 */
public class LeastConnectionsLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(LeastConnectionsLoadBalancer.class);

    private final Map<String, AtomicLong> connectionCounts = new ConcurrentHashMap<>();
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp, String serviceType) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for least connections selection");
            throw new IllegalArgumentException("No instances available");
        }

        ModelRouterProperties.ModelInstance selectedInstance = null;
        long minConnections = Long.MAX_VALUE;
        double minWeightedConnections = Double.MAX_VALUE;

        for (ModelRouterProperties.ModelInstance instance : instances) {
            String key = getInstanceKey(instance);
            long connections = connectionCounts.getOrDefault(key, new AtomicLong(0)).get();

            // 考虑权重的最少连接算法: connections / weight
            double weightedConnections = instance.getWeight() > 0 ?
                    (double) connections / instance.getWeight() : connections;

            if (weightedConnections < minWeightedConnections ||
                    (weightedConnections == minWeightedConnections && connections < minConnections)) {
                minWeightedConnections = weightedConnections;
                minConnections = connections;
                selectedInstance = instance;
            }
        }

        logger.debug("Selected instance {} with {} connections using least connections strategy for service {}",
                selectedInstance.getName(), minConnections, serviceType);
        recordLoadBalancerSelection(serviceType, "least_connections", selectedInstance.getName());
        return selectedInstance;
    }

    @Override
    public void recordCall(ModelRouterProperties.ModelInstance instance) {
        String key = getInstanceKey(instance);
        long currentCount = connectionCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        logger.info("Instance {} call recorded. Current connections: {}", key, currentCount);
    }

    @Override
    public void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
        String key = getInstanceKey(instance);
        AtomicLong count = connectionCounts.get(key);
        if (count != null) {
            long currentCount = count.decrementAndGet();
            logger.info("Instance {} call completed. Current connections: {}", key, currentCount);
        } else {
            logger.info("Instance {} call completed. No active connection count found.", key);
        }
    }

    private String getInstanceKey(ModelRouterProperties.ModelInstance instance) {
        return instance.getBaseUrl() + ":" + instance.getPath();
    }

    /**
     * 记录负载均衡器选择指标
     */
    private void recordLoadBalancerSelection(String service, String strategy, String selectedInstance) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordLoadBalancer(service, strategy, selectedInstance);
            } catch (Exception e) {
                logger.warn("Failed to record load balancer metrics: {}", e.getMessage());
            }
        }
    }
}

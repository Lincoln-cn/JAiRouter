package org.unreal.modelrouter.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.checker.ServiceStateManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 一致性哈希负载均衡器
 * 
 * 使用一致性哈希算法解决IP Hash冲突问题，支持虚拟节点机制
 * 
 * @author JAiRouter Team
 * @since 2.4.0
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashLoadBalancer.class);
    
    // 默认虚拟节点数量
    private static final int DEFAULT_VIRTUAL_NODES = 150;
    
    // 一致性哈希环
    private final TreeMap<Long, ModelRouterProperties.ModelInstance> hashCircle = new TreeMap<>();
    
    // 虚拟节点映射，用于快速查找
    private final Map<String, List<Long>> virtualNodesMap = new ConcurrentHashMap<>();
    
    // 虚拟节点数量
    private final int virtualNodeCount;
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;
    
    @Autowired(required = false)
    private ServiceStateManager serviceStateManager;

    public ConsistentHashLoadBalancer() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashLoadBalancer(int virtualNodeCount) {
        this.virtualNodeCount = virtualNodeCount;
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            List<ModelRouterProperties.ModelInstance> instances, 
            String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            List<ModelRouterProperties.ModelInstance> instances, 
            String clientIp, 
            String serviceType) {
        
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for consistent hash selection");
            throw new IllegalArgumentException("No instances available");
        }

        // 过滤健康实例
        List<ModelRouterProperties.ModelInstance> healthyInstances = instances.stream()
                .filter(instance -> isInstanceHealthy(serviceType, instance))
                .collect(Collectors.toList());

        if (healthyInstances.isEmpty()) {
            logger.warn("No healthy instances available, falling back to all instances");
            healthyInstances = instances; // 如果没有健康实例，使用所有实例
        }

        // 确保哈希环是最新的
        updateHashRing(healthyInstances);

        if (hashCircle.isEmpty()) {
            logger.error("Hash circle is empty, unable to select instance");
            throw new RuntimeException("Hash circle is empty");
        }

        // 计算客户端IP的哈希值
        long hash = hash(clientIp);
        
        // 在哈希环上找到大于等于该哈希值的第一个节点
        Map.Entry<Long, ModelRouterProperties.ModelInstance> entry = hashCircle.higherEntry(hash);
        
        // 如果没有找到，则使用环上的第一个节点（形成环状结构）
        if (entry == null) {
            entry = hashCircle.firstEntry();
        }

        ModelRouterProperties.ModelInstance selected = entry.getValue();
        
        logger.debug("Selected instance {} using consistent hash for service {}, client IP: {}", 
                selected.getName(), serviceType, clientIp);
        
        recordLoadBalancerSelection(serviceType, "consistent_hash", selected.getName());
        
        return selected;
    }
    
    /**
     * 检查实例是否健康
     * 
     * @param serviceType 服务类型
     * @param instance 实例
     * @return 是否健康
     */
    private boolean isInstanceHealthy(String serviceType, ModelRouterProperties.ModelInstance instance) {
        if (serviceStateManager != null) {
            return serviceStateManager.isInstanceHealthy(serviceType, instance);
        }
        // 如果没有服务状态管理器，则默认认为是健康的
        return true;
    }

    /**
     * 更新哈希环，添加或移除实例及其虚拟节点
     * 
     * @param instances 实例列表
     */
    private void updateHashRing(List<ModelRouterProperties.ModelInstance> instances) {
        // 获取当前哈希环中的实例ID
        Set<String> currentInstanceIds = new HashSet<>();
        for (ModelRouterProperties.ModelInstance instance : hashCircle.values()) {
            currentInstanceIds.add(instance.getInstanceId());
        }

        // 获取目标实例ID
        Set<String> targetInstanceIds = instances.stream()
                .map(ModelRouterProperties.ModelInstance::getInstanceId)
                .collect(Collectors.toSet());

        // 计算需要移除的实例
        Set<String> toRemove = new HashSet<>(currentInstanceIds);
        toRemove.removeAll(targetInstanceIds);

        // 计算需要添加的实例
        Set<String> toAdd = new HashSet<>(targetInstanceIds);
        toAdd.removeAll(currentInstanceIds);

        // 移除不在目标列表中的实例及其虚拟节点
        if (!toRemove.isEmpty()) {
            removeInstances(toRemove);
        }

        // 添加新实例及其虚拟节点
        if (!toAdd.isEmpty()) {
            List<ModelRouterProperties.ModelInstance> newInstances = instances.stream()
                    .filter(instance -> toAdd.contains(instance.getInstanceId()))
                    .collect(Collectors.toList());
            addInstances(newInstances);
        }
    }

    /**
     * 添加实例及其虚拟节点到哈希环
     * 
     * @param instances 要添加的实例列表
     */
    private void addInstances(List<ModelRouterProperties.ModelInstance> instances) {
        for (ModelRouterProperties.ModelInstance instance : instances) {
            addInstanceToHashRing(instance);
        }
    }

    /**
     * 添加单个实例及其虚拟节点到哈希环
     * 
     * @param instance 要添加的实例
     */
    private void addInstanceToHashRing(ModelRouterProperties.ModelInstance instance) {
        List<Long> virtualNodeHashes = new ArrayList<>();
        
        for (int i = 0; i < virtualNodeCount; i++) {
            String virtualNodeKey = instance.getInstanceId() + "#" + i;
            long hash = hash(virtualNodeKey);
            hashCircle.put(hash, instance);
            virtualNodeHashes.add(hash);
        }
        
        virtualNodesMap.put(instance.getInstanceId(), virtualNodeHashes);
        
        logger.debug("Added instance {} with {} virtual nodes to hash ring", 
                instance.getName(), virtualNodeCount);
    }

    /**
     * 从哈希环中移除实例及其虚拟节点
     * 
     * @param instanceIds 要移除的实例ID集合
     */
    private void removeInstances(Set<String> instanceIds) {
        for (String instanceId : instanceIds) {
            List<Long> virtualNodeHashes = virtualNodesMap.get(instanceId);
            if (virtualNodeHashes != null) {
                for (Long hash : virtualNodeHashes) {
                    hashCircle.remove(hash);
                }
                virtualNodesMap.remove(instanceId);
                logger.debug("Removed instance with ID {} and its virtual nodes from hash ring", instanceId);
            }
        }
    }

    /**
     * 计算字符串的哈希值
     * 
     * @param key 输入字符串
     * @return 哈希值
     */
    private long hash(String key) {
        if (key == null) {
            key = "";
        }
        
        // 使用改进的哈希算法，避免冲突
        long hash = 0;
        byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        for (byte b : keyBytes) {
            hash = (hash * 31) + b;
        }
        
        return Math.abs(hash);
    }

    @Override
    public void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
        // 可在此处添加统计逻辑
    }

    @Override
    public void recordCallFailure(ModelRouterProperties.ModelInstance instance) {
        // 可在此处添加统计逻辑
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
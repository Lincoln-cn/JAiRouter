package org.unreal.modelrouter.router.loadbalancer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
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

    // 为测试添加 getter 方法
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    public ServiceStateManager getServiceStateManager() {
        return serviceStateManager;
    }

    public ConsistentHashLoadBalancer() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashLoadBalancer(final int virtualNodeCount) {
        this.virtualNodeCount = virtualNodeCount;
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances, 
            final String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }

    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances, 
            final String clientIp, 
            final String serviceType) {
        
        if (instances == null || instances.isEmpty()) {
            logger.warn("No instances available for consistent hash selection");
            throw new IllegalArgumentException("No instances available");
        }

        // 确保哈希环是最新的（使用所有实例构建哈希环）
        updateHashRing(instances);

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

        // 从哈希环中选择健康实例，如果当前选中的是不健康的，则查找下一个健康的
        ModelRouterProperties.ModelInstance selected = findHealthyInstance(entry, serviceType, instances);

        if (selected == null) {
            logger.warn("No healthy instance found, selecting any instance");
            // 如果找不到健康实例，尝试使用原始哈希值找到的实例
            selected = entry.getValue(); // 如果没有健康实例，返回任意实例
        }

        logger.debug("Selected instance {} using consistent hash for service {}, client IP: {}", 
                selected.getName(), serviceType, clientIp);

        recordLoadBalancerSelection(serviceType, "consistent_hash", selected.getName());

        return selected;
    }
    
    /**
     * 从哈希环中查找健康实例
     * 如果当前实例不健康，则继续查找下一个实例，直到找到健康实例或遍历完整个环
     * 
     * @param startEntry 起始条目
     * @param serviceType 服务类型
     * @param allInstances 所有实例列表
     * @return 健康实例或 null（如果没有健康实例）
     */
    private ModelRouterProperties.ModelInstance findHealthyInstance(
            Map.Entry<Long, ModelRouterProperties.ModelInstance> startEntry,
            final String serviceType,
            final List<ModelRouterProperties.ModelInstance> allInstances) {
        
        if (startEntry == null) {
            return null;
        }
        
        // 如果起始实例是健康的，直接返回
        if (isInstanceHealthy(serviceType, startEntry.getValue())) {
            return startEntry.getValue();
        }
        
        // 从哈希环中查找下一个健康实例
        Map.Entry<Long, ModelRouterProperties.ModelInstance> currentEntry = startEntry;
        int iterationCount = 0; // 防止无限循环
        int maxIterations = hashCircle.size(); // 最大迭代次数为哈希环大小
        
        do {
            // 找到下一个条目
            currentEntry = hashCircle.higherEntry(currentEntry.getKey());
            
            // 如果到达末尾，回到开头（形成环状）
            if (currentEntry == null) {
                currentEntry = hashCircle.firstEntry();
            }
            
            // 检查当前实例是否健康
            if (isInstanceHealthy(serviceType, currentEntry.getValue())) {
                return currentEntry.getValue();
            }
            
            iterationCount++;
            
        } while (iterationCount < maxIterations && currentEntry != null && 
                !currentEntry.getKey().equals(startEntry.getKey()));
        
        return null; // 没有找到健康实例
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
        // 如果没有服务状态管理器，则使用实例的健康状态字段
        return instance.isHealthy() != null ? instance.isHealthy() : true;
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
     * 使用 MurmurHash 算法以获得更好的分布特性
     * 
     * @param key 输入字符串
     * @return 哈希值
     */
    private long hash(String key) {
        if (key == null) {
            key = "";
        }
        
        // 使用 MurmurHash 算法以获得更好的哈希分布
        // 这种算法具有很好的雪崩效应，能够均匀分布键值
        return murmurHash3(key);
    }
    
    /**
     * MurmurHash3 算法实现
     * 
     * @param input 输入字符串
     * @return 哈希值
     */
    private long murmurHash3(String input) {
        byte[] data = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = data.length;
        int seed = 0xcc9e2d51;
        int hash = seed;
        
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        final int r1 = 15;
        final int r2 = 13;
        final int m = 5;
        final int n = 0xe6546b64;
        
        int i = 0;
        while (i <= len - 4) {
            int k = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | 
                    ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k *= c1;
            k = (k << r1) | (k >>> (32 - r1));
            k *= c2;
            
            hash ^= k;
            hash = (hash << r2) | (hash >>> (32 - r2));
            hash = hash * m + n;
            
            i += 4;
        }
        
        // 处理剩余字节
        int remaining = len - i;
        if (remaining > 0) {
            int k = 0;
            if (remaining == 3) {
                k ^= data[i + 2] << 16;
            }
            if (remaining >= 2) {
                k ^= data[i + 1] << 8;
            }
            if (remaining >= 1) {
                k ^= data[i];
            }
            k *= c1;
            k = (k << r1) | (k >>> (32 - r1));
            k *= c2;
            hash ^= k;
        }
        
        hash ^= len;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);
        
        return Math.abs((long) hash);
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
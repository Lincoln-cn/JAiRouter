package org.unreal.modelrouter.store.persistence.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.LeastConnectionsLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.ConsistentHashLoadBalancer;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.persistence.StatePersistenceService;
import org.unreal.modelrouter.store.persistence.StatePersistenceService.StateType;
import reactor.core.publisher.Mono;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负载均衡器状态持久化适配器
 * 
 * v2.4.4: 使用三层退坡策略持久化负载均衡器状态
 * 
 * 持久化内容:
 * - LeastConnectionsLoadBalancer: 各实例连接计数
 * - ConsistentHashLoadBalancer: 哈希环状态 (可选，可动态重建)
 * - 策略类型和配置信息
 * 
 * 事件驱动同步:
 * - 实例选择事件
 * - 调用开始/完成事件
 * - 配置变更事件
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Component
public class LoadBalancerStatePersistenceAdapter {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerStatePersistenceAdapter.class);

    private static final String STRATEGY_KEY = "strategy";
    private static final String CONNECTION_COUNTS_KEY = "connectionCounts";
    private static final String TIMESTAMP_KEY = "timestamp";

    @Autowired
    private StatePersistenceService persistenceService;

    /**
     * 状态变更标记（用于事件驱动同步）
     */
    private final Map<ModelServiceRegistry.ServiceType, Boolean> pendingSync = new ConcurrentHashMap<>();

    /**
     * 保存负载均衡器状态
     * 
     * @param serviceType 服务类型
     * @param loadBalancer 负载均衡器实例
     * @return 保存结果
     */
    public Mono<Boolean> saveLoadBalancerState(
            ModelServiceRegistry.ServiceType serviceType, 
            LoadBalancer loadBalancer) {
        
        Map<String, Object> stateData = extractLoadBalancerState(loadBalancer);
        if (stateData.isEmpty()) {
            logger.debug("No state to save for load balancer of type: {}", serviceType);
            return Mono.just(false);
        }

        String key = serviceType.name().toLowerCase();
        stateData.put("serviceType", serviceType.name());

        logger.debug("Saving load balancer state for service: {}", serviceType);
        
        return persistenceService.save(StateType.LOAD_BALANCER, key, stateData)
                .doOnSuccess(saved -> {
                    if (Boolean.TRUE.equals(saved)) {
                        pendingSync.remove(serviceType);
                        logger.info("Load balancer state saved: {} (tier: {})", 
                                serviceType, persistenceService.getTierName());
                    }
                })
                .doOnError(e -> {
                    pendingSync.put(serviceType, true);
                    logger.error("Failed to save load balancer state: {}", serviceType, e);
                });
    }

    /**
     * 加载负载均衡器状态
     * 
     * @param serviceType 服务类型
     * @return 状态数据
     */
    public Mono<Map<String, Object>> loadLoadBalancerState(
            ModelServiceRegistry.ServiceType serviceType) {
        
        String key = serviceType.name().toLowerCase();
        
        return persistenceService.load(StateType.LOAD_BALANCER, key)
                .doOnNext(data -> {
                    if (!data.isEmpty()) {
                        logger.debug("Load balancer state loaded: {} (tier: {})", 
                                serviceType, persistenceService.getTierName());
                    }
                });
    }

    /**
     * 恢复负载均衡器状态
     * 
     * @param serviceType 服务类型
     * @param loadBalancer 负载均衡器实例
     * @return 是否恢复成功
     */
    public Mono<Boolean> restoreLoadBalancerState(
            ModelServiceRegistry.ServiceType serviceType,
            LoadBalancer loadBalancer) {
        
        return loadLoadBalancerState(serviceType)
                .flatMap(stateData -> {
                    if (stateData.isEmpty()) {
                        logger.info("No saved state found for load balancer: {}", serviceType);
                        return Mono.just(false);
                    }

                    try {
                        applyLoadBalancerState(loadBalancer, stateData);
                        logger.info("Load balancer state restored: {} (strategy: {})", 
                                serviceType, stateData.get(STRATEGY_KEY));
                        return Mono.just(true);
                    } catch (Exception e) {
                        logger.error("Failed to restore load balancer state: {}", serviceType, e);
                        return Mono.just(false);
                    }
                });
    }

    /**
     * 删除负载均衡器状态
     * 
     * @param serviceType 服务类型
     * @return 删除结果
     */
    public Mono<Boolean> deleteLoadBalancerState(ModelServiceRegistry.ServiceType serviceType) {
        String key = serviceType.name().toLowerCase();
        
        logger.info("Deleting load balancer state for service: {}", serviceType);
        
        return persistenceService.delete(StateType.LOAD_BALANCER, key)
                .doOnSuccess(deleted -> {
                    pendingSync.remove(serviceType);
                    logger.info("Load balancer state deleted: {} (deleted={})", serviceType, deleted);
                });
    }

    /**
     * 批量保存负载均衡器状态
     * 
     * @param loadBalancers 服务类型 -> 负载均衡器映射
     * @return 成功保存的数量
     */
    public Mono<Integer> saveLoadBalancerStatesBatch(
            Map<ModelServiceRegistry.ServiceType, LoadBalancer> loadBalancers) {
        
        if (loadBalancers == null || loadBalancers.isEmpty()) {
            return Mono.just(0);
        }

        Map<String, Map<String, Object>> states = new HashMap<>();
        for (Map.Entry<ModelServiceRegistry.ServiceType, LoadBalancer> entry : loadBalancers.entrySet()) {
            ModelServiceRegistry.ServiceType serviceType = entry.getKey();
            LoadBalancer lb = entry.getValue();
            
            Map<String, Object> stateData = extractLoadBalancerState(lb);
            if (!stateData.isEmpty()) {
                stateData.put("serviceType", serviceType.name());
                states.put(serviceType.name().toLowerCase(), stateData);
            }
        }

        return persistenceService.saveBatch(StateType.LOAD_BALANCER, states)
                .doOnSuccess(count -> logger.info("Batch saved {} load balancer states", count));
    }

    /**
     * 获取所有负载均衡器状态
     * 
     * @return 服务类型 -> 状态数据映射
     */
    public Mono<Map<ModelServiceRegistry.ServiceType, Map<String, Object>>> getAllLoadBalancerStates() {
        return persistenceService.getAllKeys(StateType.LOAD_BALANCER)
                .flatMap(keys -> {
                    Map<String, Map<String, Object>> states = new HashMap<>();
                    for (String key : keys) {
                        Map<String, Object> data = persistenceService
                                .load(StateType.LOAD_BALANCER, key).block();
                        if (data != null && !data.isEmpty()) {
                            states.put(key, data);
                        }
                    }

                    // 转换为 ServiceType 映射
                    Map<ModelServiceRegistry.ServiceType, Map<String, Object>> result = 
                            new EnumMap<>(ModelServiceRegistry.ServiceType.class);
                    for (Map.Entry<String, Map<String, Object>> entry : states.entrySet()) {
                        try {
                            ModelServiceRegistry.ServiceType serviceType = 
                                    ModelServiceRegistry.ServiceType.valueOf(entry.getKey().toUpperCase());
                            result.put(serviceType, entry.getValue());
                        } catch (IllegalArgumentException e) {
                            logger.warn("Unknown service type in state: {}", entry.getKey());
                        }
                    }

                    return Mono.just(result);
                });
    }

    /**
     * 清除所有负载均衡器状态
     */
    public Mono<Boolean> clearAllLoadBalancerStates() {
        logger.warn("Clearing all load balancer states");
        return persistenceService.clearAll(StateType.LOAD_BALANCER)
                .doOnSuccess(cleared -> {
                    pendingSync.clear();
                    logger.info("All load balancer states cleared: {}", cleared);
                });
    }

    /**
     * 事件驱动：实例选择时触发同步
     */
    public void onInstanceSelected(
            ModelServiceRegistry.ServiceType serviceType, 
            LoadBalancer loadBalancer) {
        
        // 对于 LeastConnections，选择事件不触发同步（高频操作）
        // 仅在特定策略变更时同步
        if (loadBalancer instanceof LeastConnectionsLoadBalancer) {
            // 仅标记待同步，不立即同步
            pendingSync.put(serviceType, true);
        }
    }

    /**
     * 事件驱动：调用开始时触发同步
     */
    public void onCallStarted(
            ModelServiceRegistry.ServiceType serviceType, 
            LoadBalancer loadBalancer) {
        
        // 高频操作，不立即同步
        pendingSync.put(serviceType, true);
    }

    /**
     * 事件驱动：调用完成时触发同步（可选）
     */
    public void onCallCompleted(
            ModelServiceRegistry.ServiceType serviceType, 
            LoadBalancer loadBalancer) {
        
        // 高频操作，不立即同步
        pendingSync.put(serviceType, true);
    }

    /**
     * 事件驱动：配置变更时触发同步
     */
    public void onConfigurationChanged(
            ModelServiceRegistry.ServiceType serviceType, 
            LoadBalancer loadBalancer) {
        
        logger.info("Load balancer configuration changed for {}, triggering state sync", serviceType);
        saveLoadBalancerState(serviceType, loadBalancer).subscribe();
    }

    /**
     * 事件驱动：策略切换时触发同步
     */
    public void onStrategySwitched(
            ModelServiceRegistry.ServiceType serviceType, 
            LoadBalancer loadBalancer,
            String newStrategy) {
        
        logger.info("Load balancer strategy switched to {} for {}, triggering state sync", 
                newStrategy, serviceType);
        saveLoadBalancerState(serviceType, loadBalancer).subscribe();
    }

    /**
     * 定时同步：批量同步所有待同步状态
     * 
     * @param loadBalancers 服务类型 -> 负载均衡器映射
     */
    public void syncPendingStates(Map<ModelServiceRegistry.ServiceType, LoadBalancer> loadBalancers) {
        if (pendingSync.isEmpty()) {
            logger.debug("No pending load balancer states to sync");
            return;
        }

        logger.info("Syncing {} pending load balancer states", pendingSync.size());
        
        for (ModelServiceRegistry.ServiceType serviceType : pendingSync.keySet()) {
            LoadBalancer lb = loadBalancers.get(serviceType);
            if (lb != null) {
                saveLoadBalancerState(serviceType, lb).subscribe();
            }
        }
    }

    /**
     * 提取负载均衡器状态数据
     */
    private Map<String, Object> extractLoadBalancerState(LoadBalancer loadBalancer) {
        Map<String, Object> stateData = new HashMap<>();

        // 记录策略类型
        String strategy = loadBalancer.getClass().getSimpleName()
                .replace("LoadBalancer", "")
                .toLowerCase();
        stateData.put(STRATEGY_KEY, strategy);
        stateData.put(TIMESTAMP_KEY, System.currentTimeMillis());

        // 提取 LeastConnections 连接计数
        if (loadBalancer instanceof LeastConnectionsLoadBalancer) {
            LeastConnectionsLoadBalancer lclb = (LeastConnectionsLoadBalancer) loadBalancer;
            // 通过反射获取 connectionCounts (protected field)
            try {
                java.lang.reflect.Field field = LeastConnectionsLoadBalancer.class
                        .getDeclaredField("connectionCounts");
                field.setAccessible(true);
                Map<String, java.util.concurrent.atomic.AtomicLong> counts = 
                        (Map<String, java.util.concurrent.atomic.AtomicLong>) field.get(lclb);
                
                Map<String, Long> connectionCounts = new HashMap<>();
                for (Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry : counts.entrySet()) {
                    connectionCounts.put(entry.getKey(), entry.getValue().get());
                }
                stateData.put(CONNECTION_COUNTS_KEY, connectionCounts);
                
            } catch (Exception e) {
                logger.warn("Failed to extract connection counts from LeastConnectionsLoadBalancer", e);
            }
        }

        // ConsistentHash 哈希环状态可动态重建，不持久化
        if (loadBalancer instanceof ConsistentHashLoadBalancer) {
            // 标记为可重建状态
            stateData.put("rebuildable", true);
        }

        return stateData;
    }

    /**
     * 应用负载均衡器状态数据
     */
    private void applyLoadBalancerState(LoadBalancer loadBalancer, Map<String, Object> stateData) {
        if (loadBalancer instanceof LeastConnectionsLoadBalancer) {
            LeastConnectionsLoadBalancer lclb = (LeastConnectionsLoadBalancer) loadBalancer;
            
            Map<String, Long> connectionCounts = (Map<String, Long>) stateData.get(CONNECTION_COUNTS_KEY);
            if (connectionCounts != null) {
                // 通过反射设置 connectionCounts
                try {
                    java.lang.reflect.Field field = LeastConnectionsLoadBalancer.class
                            .getDeclaredField("connectionCounts");
                    field.setAccessible(true);
                    Map<String, java.util.concurrent.atomic.AtomicLong> counts = 
                            new ConcurrentHashMap<>();
                    for (Map.Entry<String, Long> entry : connectionCounts.entrySet()) {
                        counts.put(entry.getKey(), new java.util.concurrent.atomic.AtomicLong(entry.getValue()));
                    }
                    field.set(lclb, counts);
                    
                    logger.debug("Restored {} connection counts for LeastConnectionsLoadBalancer", 
                            counts.size());
                    
                } catch (Exception e) {
                    logger.warn("Failed to apply connection counts to LeastConnectionsLoadBalancer", e);
                }
            }
        }
        
        // ConsistentHash 哈希环可动态重建，无需恢复
    }

    /**
     * 获取待同步状态数量
     */
    public int getPendingSyncCount() {
        return pendingSync.size();
    }
}
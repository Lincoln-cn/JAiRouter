package org.unreal.modelrouter.persistence.store.persistence.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.store.persistence.adapter.CircuitBreakerStatePersistenceAdapter;
import org.unreal.modelrouter.persistence.store.persistence.adapter.LoadBalancerStatePersistenceAdapter;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态恢复服务
 * 
 * v2.4.4: 实现启动时自动恢复 + 手动 API 两种恢复机制
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Service
@ConditionalOnProperty(name = "jairouter.persistence.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class StateRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(StateRecoveryService.class);

    @Autowired
    private CircuitBreakerManager circuitBreakerManager;

    @Autowired
    private LoadBalancerManager loadBalancerManager;

    @Autowired
    private ModelRouterProperties properties;

    @Autowired
    private CircuitBreakerStatePersistenceAdapter cbPersistenceAdapter;

    @Autowired
    private LoadBalancerStatePersistenceAdapter lbPersistenceAdapter;

    private volatile RecoveryStatistics lastRecoveryStats = new RecoveryStatistics();

    /**
     * 启动时自动恢复状态
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("=== State Recovery Service: Starting automatic state recovery ===");
        
        if (!isRecoveryEnabled()) {
            logger.info("State recovery is disabled by configuration");
            return;
        }

        performAutomaticRecovery()
                .doOnSuccess(stats -> {
                    logger.info("=== Automatic state recovery completed ===");
                    logger.info("Recovery statistics: {}", stats);
                    lastRecoveryStats = stats;
                })
                .doOnError(e -> logger.error("Automatic state recovery failed", e))
                .subscribe();
    }

    /**
     * 执行自动恢复
     */
    public Mono<RecoveryStatistics> performAutomaticRecovery() {
        logger.info("Starting automatic state recovery from persistence layers");
        
        RecoveryStatistics stats = new RecoveryStatistics();
        stats.startTime = System.currentTimeMillis();

        return Mono.when(
                recoverCircuitBreakerStates(stats),
                recoverLoadBalancerStates(stats)
        )
                .doOnTerminate(() -> {
                    stats.endTime = System.currentTimeMillis();
                    stats.durationMs = stats.endTime - stats.startTime;
                    stats.success = true;
                })
                .thenReturn(stats);
    }

    /**
     * 恢复熔断器状态
     */
    private Mono<Void> recoverCircuitBreakerStates(final RecoveryStatistics stats) {
        logger.info("Recovering circuit breaker states...");
        
        return cbPersistenceAdapter.getAllCircuitBreakerInstanceIds()
                .flatMap(instanceIds -> {
                    stats.cbStatesFound = countIterable(instanceIds);
                    logger.info("Found {} circuit breaker states to recover", stats.cbStatesFound);

                    if (stats.cbStatesFound == 0) {
                        logger.info("No circuit breaker states to recover");
                        return Mono.empty();
                    }

                    // 获取实例 URL 映射
                    Map<String, String> instanceUrls = buildInstanceUrlMap();

                    int recoveredCount = 0;
                    int failedCount = 0;

                    for (String instanceId : instanceIds) {
                        String instanceUrl = instanceUrls.get(instanceId);
                        if (instanceUrl != null) {
                            CircuitBreaker cb = circuitBreakerManager.getCircuitBreaker(instanceId, instanceUrl);
                            if (cb != null) {
                                Boolean result = cbPersistenceAdapter.restoreCircuitBreakerState(cb).block();
                                if (Boolean.TRUE.equals(result)) {
                                    recoveredCount++;
                                } else {
                                    failedCount++;
                                }
                            } else {
                                logger.warn("Circuit breaker not found for instance: {}", instanceId);
                                failedCount++;
                            }
                        } else {
                            logger.warn("No URL mapping for instance: {}", instanceId);
                            failedCount++;
                        }
                    }

                    stats.cbStatesRecovered = recoveredCount;
                    stats.cbStatesFailed = failedCount;

                    logger.info("Circuit breaker recovery: {} recovered, {} failed", 
                            recoveredCount, failedCount);

                    return Mono.empty();
                });
    }

    /**
     * 构建实例 ID 到 URL 的映射
     */
    private Map<String, String> buildInstanceUrlMap() {
        Map<String, String> instanceUrls = new HashMap<>();
        
        if (properties.getServices() != null) {
            for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry : 
                    properties.getServices().entrySet()) {
                ModelRouterProperties.ServiceConfig serviceConfig = entry.getValue();
                if (serviceConfig.getInstances() != null) {
                    for (ModelRouterProperties.ModelInstance instance : serviceConfig.getInstances()) {
                        String instanceId = instance.getInstanceId();
                        String instanceUrl = instance.getBaseUrl() + instance.getPath();
                        instanceUrls.put(instanceId, instanceUrl);
                    }
                }
            }
        }
        
        return instanceUrls;
    }

    /**
     * 恢复负载均衡器状态
     */
    private Mono<Void> recoverLoadBalancerStates(final RecoveryStatistics stats) {
        logger.info("Recovering load balancer states...");
        
        return lbPersistenceAdapter.getAllLoadBalancerStates()
                .flatMap(allStates -> {
                    stats.lbStatesFound = allStates.size();
                    logger.info("Found {} load balancer states to recover", stats.lbStatesFound);

                    if (stats.lbStatesFound == 0) {
                        logger.info("No load balancer states to recover");
                        return Mono.empty();
                    }

                    int recoveredCount = 0;
                    int failedCount = 0;

                    for (Map.Entry<ModelServiceRegistry.ServiceType, Map<String, Object>> entry : 
                            allStates.entrySet()) {
                        ModelServiceRegistry.ServiceType serviceType = entry.getKey();
                        LoadBalancer lb = loadBalancerManager.getLoadBalancer(serviceType);
                        
                        if (lb != null) {
                            Boolean result = lbPersistenceAdapter
                                    .restoreLoadBalancerState(serviceType, lb).block();
                            if (Boolean.TRUE.equals(result)) {
                                recoveredCount++;
                            } else {
                                failedCount++;
                            }
                        } else {
                            logger.warn("Load balancer not found for service: {}", serviceType);
                            failedCount++;
                        }
                    }

                    stats.lbStatesRecovered = recoveredCount;
                    stats.lbStatesFailed = failedCount;

                    logger.info("Load balancer recovery: {} recovered, {} failed", 
                            recoveredCount, failedCount);

                    return Mono.empty();
                });
    }

    /**
     * 手动触发恢复（API）
     */
    public Mono<RecoveryStatistics> triggerManualRecovery() {
        logger.info("Manual state recovery triggered");
        return performAutomaticRecovery()
                .doOnSuccess(stats -> {
                    lastRecoveryStats = stats;
                    logger.info("Manual recovery completed: {}", stats);
                });
    }

    /**
     * 手动触发指定熔断器恢复
     */
    public Mono<Boolean> recoverSingleCircuitBreaker(final String instanceId) {
        logger.info("Manual recovery for single circuit breaker: {}", instanceId);
        
        Map<String, String> instanceUrls = buildInstanceUrlMap();
        String instanceUrl = instanceUrls.get(instanceId);
        
        if (instanceUrl == null) {
            logger.warn("No URL mapping found for circuit breaker: {}", instanceId);
            return Mono.just(false);
        }

        CircuitBreaker cb = circuitBreakerManager.getCircuitBreaker(instanceId, instanceUrl);
        if (cb == null) {
            logger.warn("Circuit breaker not found: {}", instanceId);
            return Mono.just(false);
        }

        return cbPersistenceAdapter.restoreCircuitBreakerState(cb)
                .doOnSuccess(result -> logger.info("Single CB recovery result: {} -> {}", 
                        instanceId, result));
    }

    /**
     * 手动触发指定负载均衡器恢复
     */
    public Mono<Boolean> recoverSingleLoadBalancer(final ModelServiceRegistry.ServiceType serviceType) {
        logger.info("Manual recovery for single load balancer: {}", serviceType);
        
        LoadBalancer lb = loadBalancerManager.getLoadBalancer(serviceType);
        if (lb == null) {
            logger.warn("Load balancer not found: {}", serviceType);
            return Mono.just(false);
        }

        return lbPersistenceAdapter.restoreLoadBalancerState(serviceType, lb)
                .doOnSuccess(result -> logger.info("Single LB recovery result: {} -> {}", 
                        serviceType, result));
    }

    public RecoveryStatistics getLastRecoveryStats() {
        return lastRecoveryStats;
    }

    private boolean isRecoveryEnabled() {
        return true;
    }

    private int countIterable(final Iterable<?> iterable) {
        int count = 0;
        for (Object ignored : iterable) {
            count++;
        }
        return count;
    }

    /**
     * 恢复统计信息
     */
    public static class RecoveryStatistics {
        public long startTime;
        public long endTime;
        public long durationMs;
        public boolean success;
        public int cbStatesFound;
        public int cbStatesRecovered;
        public int cbStatesFailed;
        public int lbStatesFound;
        public int lbStatesRecovered;
        public int lbStatesFailed;

        public RecoveryStatistics() {
            startTime = 0;
            endTime = 0;
            durationMs = 0;
            success = false;
            cbStatesFound = 0;
            cbStatesRecovered = 0;
            cbStatesFailed = 0;
            lbStatesFound = 0;
            lbStatesRecovered = 0;
            lbStatesFailed = 0;
        }

        public int getTotalRecovered() {
            return cbStatesRecovered + lbStatesRecovered;
        }

        public int getTotalFailed() {
            return cbStatesFailed + lbStatesFailed;
        }

        @Override
        public String toString() {
            return String.format(
                    "RecoveryStatistics{success=%s, duration=%dms, " +
                    "cb(found=%d, recovered=%d, failed=%d), " +
                    "lb(found=%d, recovered=%d, failed=%d)}",
                    success, durationMs,
                    cbStatesFound, cbStatesRecovered, cbStatesFailed,
                    lbStatesFound, lbStatesRecovered, lbStatesFailed);
        }
    }
}
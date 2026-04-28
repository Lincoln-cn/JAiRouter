package org.unreal.modelrouter.store.persistence.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.LockFreeCircuitBreaker;
import org.unreal.modelrouter.store.persistence.StatePersistenceService;
import org.unreal.modelrouter.store.persistence.StatePersistenceService.StateType;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器状态持久化适配器
 * 
 * v2.4.4: 使用三层退坡策略持久化熔断器状态
 * 
 * 事件驱动同步:
 * - 状态变更事件 (CLOSED → OPEN → HALF_OPEN) 触发同步
 * - 熔断触发事件触发同步
 * - 恢复事件触发同步
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Component
public class CircuitBreakerStatePersistenceAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerStatePersistenceAdapter.class);

    @Autowired
    private StatePersistenceService persistenceService;

    /**
     * 状态变更监听器缓存
     */
    private final Map<String, Boolean> pendingSync = new ConcurrentHashMap<>();

    /**
     * 保存熔断器状态（事件驱动）
     * 
     * @param circuitBreaker 熔断器实例
     * @return 保存结果
     */
    public Mono<Boolean> saveCircuitBreakerState(final CircuitBreaker circuitBreaker) {
        if (!(circuitBreaker instanceof LockFreeCircuitBreaker)) {
            logger.warn("Only LockFreeCircuitBreaker state persistence is supported");
            return Mono.just(false);
        }

        LockFreeCircuitBreaker lcb = (LockFreeCircuitBreaker) circuitBreaker;
        Map<String, Object> stateData = lcb.persistState();
        String instanceId = (String) stateData.get("instanceId");

        if (instanceId == null) {
            logger.warn("Cannot save circuit breaker state without instanceId");
            return Mono.just(false);
        }

        logger.debug("Saving circuit breaker state for instance: {}", instanceId);
        
        return persistenceService.save(StateType.CIRCUIT_BREAKER, instanceId, stateData)
                .doOnSuccess(saved -> {
                    if (Boolean.TRUE.equals(saved)) {
                        pendingSync.remove(instanceId);
                        logger.info("Circuit breaker state saved successfully: {} (tier: {})", 
                                instanceId, persistenceService.getTierName());
                    }
                })
                .doOnError(e -> {
                    pendingSync.put(instanceId, true);
                    logger.error("Failed to save circuit breaker state: {}", instanceId, e);
                });
    }

    /**
     * 加载熔断器状态
     * 
     * @param instanceId 实例 ID
     * @return 状态数据
     */
    public Mono<Map<String, Object>> loadCircuitBreakerState(final String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Mono.just(java.util.Collections.emptyMap());
        }

        logger.debug("Loading circuit breaker state for instance: {}", instanceId);
        
        return persistenceService.load(StateType.CIRCUIT_BREAKER, instanceId)
                .doOnNext(data -> {
                    if (!data.isEmpty()) {
                        logger.debug("Circuit breaker state loaded: {} (tier: {})", 
                                instanceId, persistenceService.getTierName());
                    }
                });
    }

    /**
     * 恢复熔断器状态
     * 
     * @param circuitBreaker 熔断器实例
     * @return 是否恢复成功
     */
    public Mono<Boolean> restoreCircuitBreakerState(final CircuitBreaker circuitBreaker) {
        if (!(circuitBreaker instanceof LockFreeCircuitBreaker)) {
            logger.warn("Only LockFreeCircuitBreaker state restoration is supported");
            return Mono.just(false);
        }

        LockFreeCircuitBreaker lcb = (LockFreeCircuitBreaker) circuitBreaker;
        Map<String, Object> stateDetail = lcb.getStateDetail();
        String instanceId = (String) stateDetail.get("instanceId");

        if (instanceId == null) {
            logger.warn("Cannot restore circuit breaker state without instanceId");
            return Mono.just(false);
        }

        return loadCircuitBreakerState(instanceId)
                .flatMap(stateData -> {
                    if (stateData.isEmpty()) {
                        logger.info("No saved state found for circuit breaker: {}", instanceId);
                        return Mono.just(false);
                    }

                    try {
                        lcb.restoreState(stateData);
                        logger.info("Circuit breaker state restored successfully: {} (state: {})", 
                                instanceId, stateData.get("state"));
                        return Mono.just(true);
                    } catch (Exception e) {
                        logger.error("Failed to restore circuit breaker state: {}", instanceId, e);
                        return Mono.just(false);
                    }
                });
    }

    /**
     * 删除熔断器状态
     * 
     * @param instanceId 实例 ID
     * @return 删除结果
     */
    public Mono<Boolean> deleteCircuitBreakerState(final String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Mono.just(false);
        }

        logger.info("Deleting circuit breaker state for instance: {}", instanceId);
        
        return persistenceService.delete(StateType.CIRCUIT_BREAKER, instanceId)
                .doOnSuccess(deleted -> {
                    pendingSync.remove(instanceId);
                    logger.info("Circuit breaker state deleted: {} (deleted={})", instanceId, deleted);
                });
    }

    /**
     * 检查熔断器状态是否存在
     * 
     * @param instanceId 实例 ID
     * @return 是否存在
     */
    public Mono<Boolean> existsCircuitBreakerState(final String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Mono.just(false);
        }

        return persistenceService.exists(StateType.CIRCUIT_BREAKER, instanceId);
    }

    /**
     * 获取所有熔断器实例 ID
     * 
     * @return 实例 ID 列表
     */
    public Mono<Iterable<String>> getAllCircuitBreakerInstanceIds() {
        return persistenceService.getAllKeys(StateType.CIRCUIT_BREAKER);
    }

    /**
     * 批量保存熔断器状态
     * 
     * @param circuitBreakers 熔断器实例列表
     * @return 成功保存的数量
     */
    public Mono<Integer> saveCircuitBreakerStatesBatch(
            final java.util.List<CircuitBreaker> circuitBreakers) {
        if (circuitBreakers == null || circuitBreakers.isEmpty()) {
            return Mono.just(0);
        }

        Map<String, Map<String, Object>> states = new java.util.HashMap<>();
        for (CircuitBreaker cb : circuitBreakers) {
            if (cb instanceof LockFreeCircuitBreaker) {
                LockFreeCircuitBreaker lcb = (LockFreeCircuitBreaker) cb;
                Map<String, Object> stateData = lcb.persistState();
                String instanceId = (String) stateData.get("instanceId");
                if (instanceId != null) {
                    states.put(instanceId, stateData);
                }
            }
        }

        return persistenceService.saveBatch(StateType.CIRCUIT_BREAKER, states)
                .doOnSuccess(count -> logger.info("Batch saved {} circuit breaker states", count));
    }

    /**
     * 清除所有熔断器状态
     * 
     * @return 清除结果
     */
    public Mono<Boolean> clearAllCircuitBreakerStates() {
        logger.warn("Clearing all circuit breaker states");
        return persistenceService.clearAll(StateType.CIRCUIT_BREAKER)
                .doOnSuccess(cleared -> logger.info("All circuit breaker states cleared: {}", cleared));
    }

    /**
     * 事件驱动：状态变更时触发同步
     * 
     * @param circuitBreaker 熔断器实例
     * @param newState 新状态
     */
    public void onStateChange(final CircuitBreaker circuitBreaker, final String newState) {
        logger.debug("Circuit breaker state change event: {}", newState);
        saveCircuitBreakerState(circuitBreaker).subscribe();
    }

    /**
     * 事件驱动：熔断触发时同步
     * 
     * @param circuitBreaker 熔断器实例
     */
    public void onCircuitBreakerTripped(final CircuitBreaker circuitBreaker) {
        logger.info("Circuit breaker tripped event, triggering state sync");
        saveCircuitBreakerState(circuitBreaker).subscribe();
    }

    /**
     * 事件驱动：恢复时同步
     * 
     * @param circuitBreaker 熔断器实例
     */
    public void onCircuitBreakerRecovered(final CircuitBreaker circuitBreaker) {
        logger.info("Circuit breaker recovered event, triggering state sync");
        saveCircuitBreakerState(circuitBreaker).subscribe();
    }

    /**
     * 获取待同步的状态数量
     */
    public int getPendingSyncCount() {
        return pendingSync.size();
    }

    /**
     * 重试所有待同步的状态
     * 
     * @param cbManager 熔断器管理器
     * @param instanceUrls 实例 URL 映射 (instanceId -> instanceUrl)
     */
    public void retryPendingSyncs(
            final org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager cbManager,
            final Map<String, String> instanceUrls) {
        if (pendingSync.isEmpty()) {
            logger.debug("No pending syncs to retry");
            return;
        }

        logger.info("Retrying {} pending state syncs", pendingSync.size());
        
        java.util.Set<String> keys = new java.util.HashSet<>(pendingSync.keySet());
        for (String instanceId : keys) {
            String instanceUrl = instanceUrls.get(instanceId);
            if (instanceUrl != null) {
                CircuitBreaker cb = cbManager.getCircuitBreaker(instanceId, instanceUrl);
                if (cb != null) {
                    saveCircuitBreakerState(cb).subscribe();
                } else {
                    pendingSync.remove(instanceId);
                }
            } else {
                pendingSync.remove(instanceId);
            }
        }
    }
}
package org.unreal.modelrouter.persistence.store.persistence;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 状态持久化服务接口
 * 
 * v2.4.4: 支持三层退坡策略 (Redis → H2 → File)
 * 
 * 提供统一的状态持久化 API，用于熔断器、负载均衡器等组件的状态存储。
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
public interface StatePersistenceService {

    /**
     * 状态类型枚举
     */
    enum StateType {
        CIRCUIT_BREAKER,
        LOAD_BALANCER,
        RATE_LIMITER,
        MODEL_STATS,
        CUSTOM
    }

    /**
     * 保存状态
     *
     * @param stateType 状态类型
     * @param key 状态键 (如 instanceId)
     * @param stateData 状态数据
     * @return 保存结果
     */
    Mono<Boolean> save(StateType stateType, String key, Map<String, Object> stateData);

    /**
     * 加载状态
     *
     * @param stateType 状态类型
     * @param key 状态键
     * @return 状态数据，不存在时返回空 Map
     */
    Mono<Map<String, Object>> load(StateType stateType, String key);

    /**
     * 删除状态
     *
     * @param stateType 状态类型
     * @param key 状态键
     * @return 删除结果
     */
    Mono<Boolean> delete(StateType stateType, String key);

    /**
     * 检查状态是否存在
     *
     * @param stateType 状态类型
     * @param key 状态键
     * @return 是否存在
     */
    Mono<Boolean> exists(StateType stateType, String key);

    /**
     * 获取指定类型的所有状态键
     *
     * @param stateType 状态类型
     * @return 所有状态键列表
     */
    Mono<Iterable<String>> getAllKeys(StateType stateType);

    /**
     * 批量保存状态
     *
     * @param stateType 状态类型
     * @param states 状态数据 Map (key -> stateData)
     * @return 成功保存的数量
     */
    Mono<Integer> saveBatch(StateType stateType, Map<String, Map<String, Object>> states);

    /**
     * 批量加载状态
     *
     * @param stateType 状态类型
     * @param keys 状态键列表
     * @return 状态数据 Map (key -> stateData)
     */
    Mono<Map<String, Map<String, Object>>> loadBatch(StateType stateType, Iterable<String> keys);

    /**
     * 清除指定类型的所有状态
     *
     * @param stateType 状态类型
     * @return 清除结果
     */
    Mono<Boolean> clearAll(StateType stateType);

    /**
     * 检查服务健康状态
     *
     * @return 服务是否可用
     */
    Mono<Boolean> isHealthy();

    /**
     * 获取存储层名称
     *
     * @return 存储层标识 (redis, h2, file)
     */
    String getTierName();

    /**
     * 获取存储层优先级 (数字越小优先级越高)
     *
     * @return 优先级值
     */
    int getTierPriority();
}
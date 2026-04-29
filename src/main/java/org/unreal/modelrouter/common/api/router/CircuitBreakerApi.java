package org.unreal.modelrouter.common.api.router;

import org.unreal.modelrouter.common.api.dto.CircuitBreakerSnapshot;

import java.util.List;
import java.util.Map;

/**
 * 熔断器接口 - 其他模块通过此接口获取熔断器状态。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>monitor 模块通过此接口获取熔断器监控数据</li>
 *   <li>config 模块通过此接口配置熔断器参数</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 router-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface CircuitBreakerApi {

    /**
     * 获取熔断器状态快照。
     *
     * @param instanceId 实例 ID
     * @return 状态快照
     */
    CircuitBreakerSnapshot getState(String instanceId);

    /**
     * 获取所有熔断器状态。
     *
     * @return 状态快照列表
     */
    List<CircuitBreakerSnapshot> getAllStates();

    /**
     * 获取熔断器统计信息。
     *
     * @param instanceId 实例 ID
     * @return 统计数据
     */
    Map<String, Object> getStatistics(String instanceId);

    /**
     * 重置熔断器。
     *
     * @param instanceId 实例 ID
     */
    void resetCircuitBreaker(String instanceId);

    /**
     * 强制打开熔断器。
     *
     * @param instanceId 实例 ID
     * @param durationSeconds 持续时间（秒）
     */
    void forceOpenCircuitBreaker(String instanceId, int durationSeconds);

    /**
     * 获取 OPEN 状态的熔断器数量。
     *
     * @return OPEN 状态数量
     */
    int getOpenCircuitBreakerCount();
}
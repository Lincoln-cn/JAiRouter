package org.unreal.modelrouter.common.api.persistence;

import org.unreal.modelrouter.common.api.dto.CircuitBreakerSnapshot;
import org.unreal.modelrouter.common.api.dto.LoadBalancerSnapshot;
import org.unreal.modelrouter.common.api.dto.RateLimiterSnapshot;

/**
 * 状态存储接口 - persistence 模块对外暴露的存储能力。
 *
 * <p>此接口用于解耦 persistence 与 router 业务模块：
 * <ul>
 *   <li>persistence 模块实现此接口，提供纯存储能力</li>
 *   <li>router 模块通过此接口访问 persistence，不再直接依赖</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为服务间通信协议。
 *
 * @since v2.8.0
 */
public interface StateRepositoryApi {

    // === 熔断器状态存储 ===

    /**
     * 保存熔断器状态快照。
     *
     * @param instanceId 实例 ID
     * @param snapshot 状态快照
     */
    void saveCircuitBreakerSnapshot(String instanceId, CircuitBreakerSnapshot snapshot);

    /**
     * 加载熔断器状态快照。
     *
     * @param instanceId 实例 ID
     * @return 状态快照，不存在时返回 null
     */
    CircuitBreakerSnapshot loadCircuitBreakerSnapshot(String instanceId);

    /**
     * 删除熔断器状态快照。
     *
     * @param instanceId 实例 ID
     */
    void deleteCircuitBreakerSnapshot(String instanceId);

    /**
     * 获取所有熔断器状态快照。
     *
     * @return 所有快照列表
     */
    java.util.List<CircuitBreakerSnapshot> loadAllCircuitBreakerSnapshots();

    // === 负载均衡状态存储 ===

    /**
     * 保存负载均衡状态快照。
     *
     * @param serviceId 服务 ID
     * @param snapshot 状态快照
     */
    void saveLoadBalancerSnapshot(String serviceId, LoadBalancerSnapshot snapshot);

    /**
     * 加载负载均衡状态快照。
     *
     * @param serviceId 服务 ID
     * @return 状态快照，不存在时返回 null
     */
    LoadBalancerSnapshot loadLoadBalancerSnapshot(String serviceId);

    /**
     * 删除负载均衡状态快照。
     *
     * @param serviceId 服务 ID
     */
    void deleteLoadBalancerSnapshot(String serviceId);

    /**
     * 获取所有负载均衡状态快照。
     *
     * @return 所有快照列表
     */
    java.util.List<LoadBalancerSnapshot> loadAllLoadBalancerSnapshots();

    // === 限流器状态存储 ===

    /**
     * 保存限流器状态快照。
     *
     * @param instanceId 实例 ID
     * @param snapshot 状态快照
     */
    void saveRateLimiterSnapshot(String instanceId, RateLimiterSnapshot snapshot);

    /**
     * 加载限流器状态快照。
     *
     * @param instanceId 实例 ID
     * @return 状态快照，不存在时返回 null
     */
    RateLimiterSnapshot loadRateLimiterSnapshot(String instanceId);

    /**
     * 删除限流器状态快照。
     *
     * @param instanceId 实例 ID
     */
    void deleteRateLimiterSnapshot(String instanceId);

    /**
     * 获取所有限流器状态快照。
     *
     * @return 所有快照列表
     */
    java.util.List<RateLimiterSnapshot> loadAllRateLimiterSnapshots();

    // === 批量操作 ===

    /**
     * 清除所有状态快照（用于重置）。
     */
    void clearAllSnapshots();

    /**
     * 检查是否有持久化状态。
     *
     * @return true 如果有状态数据
     */
    boolean hasPersistedState();
}
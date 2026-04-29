package org.unreal.modelrouter.common.api.router;

import org.unreal.modelrouter.common.api.dto.LoadBalancerSnapshot;

import java.util.List;
import java.util.Map;

/**
 * 负载均衡接口 - 其他模块通过此接口获取负载均衡状态。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>monitor 模块通过此接口获取负载均衡监控数据</li>
 *   <li>config 模块通过此接口配置负载均衡策略</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 router-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface LoadBalancerApi {

    /**
     * 获取负载均衡状态快照。
     *
     * @param serviceId 服务 ID
     * @return 状态快照
     */
    LoadBalancerSnapshot getState(String serviceId);

    /**
     * 获取所有负载均衡状态。
     *
     * @return 状态快照列表
     */
    List<LoadBalancerSnapshot> getAllStates();

    /**
     * 获取负载均衡统计信息。
     *
     * @param serviceId 服务 ID
     * @return 统计数据
     */
    Map<String, Object> getStatistics(String serviceId);

    /**
     * 更新实例权重。
     *
     * @param serviceId 服务 ID
     * @param instanceId 实例 ID
     * @param weight 新权重
     */
    void updateInstanceWeight(String serviceId, String instanceId, int weight);

    /**
     * 获取服务选择分布。
     *
     * @param serviceId 服务 ID
     * @return 实例选择次数映射
     */
    Map<String, Long> getSelectionDistribution(String serviceId);
}
package org.unreal.modelrouter.common.api.router;

import org.unreal.modelrouter.common.api.dto.RateLimiterSnapshot;

import java.util.List;
import java.util.Map;

/**
 * 限流器接口 - 其他模块通过此接口获取限流器状态。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>monitor 模块通过此接口获取限流器监控数据</li>
 *   <li>config 模块通过此接口配置限流器参数</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 router-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface RateLimiterApi {

    /**
     * 获取限流器状态快照。
     *
     * @param instanceId 实例 ID
     * @return 状态快照
     */
    RateLimiterSnapshot getState(String instanceId);

    /**
     * 获取所有限流器状态。
     *
     * @return 状态快照列表
     */
    List<RateLimiterSnapshot> getAllStates();

    /**
     * 获取限流器统计信息。
     *
     * @param instanceId 实例 ID
     * @return 统计数据
     */
    Map<String, Object> getStatistics(String instanceId);

    /**
     * 重置限流器。
     *
     * @param instanceId 实例 ID
     */
    void resetRateLimiter(String instanceId);

    /**
     * 获取限流器拒绝率。
     *
     * @param instanceId 实例 ID
     * @return 拒绝率（0-1）
     */
    double getRejectionRate(String instanceId);
}
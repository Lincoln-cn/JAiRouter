package org.unreal.modelrouter.common.api.monitor;

import java.util.List;
import java.util.Map;

/**
 * 监控指标接口 - 其他模块通过此接口上报和查询指标。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>router 模块通过此接口上报路由指标</li>
 *   <li>auth 模块通过此接口上报认证指标</li>
 *   <li>config 模块通过此接口上报配置变更指标</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 monitor-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface MetricsApi {

    /**
     * 记录指标数据。
     *
     * @param componentType 组件类型：router, auth, config, persistence
     * @param metrics 指标数据
     */
    void recordMetrics(String componentType, Map<String, Object> metrics);

    /**
     * 获取组件指标。
     *
     * @param componentType 组件类型
     * @param componentId 组件 ID
     * @return 指标数据
     */
    Map<String, Object> getMetrics(String componentType, String componentId);

    /**
     * 获取系统总体指标。
     *
     * @return 系统指标数据
     */
    Map<String, Object> getSystemMetrics();

    /**
     * 获取组件指标历史。
     *
     * @param componentType 组件类型
     * @param componentId 组件 ID
     * @param durationMinutes 时间范围（分钟）
     * @return 指标历史列表
     */
    List<Map<String, Object>> getMetricsHistory(String componentType, String componentId, int durationMinutes);
}
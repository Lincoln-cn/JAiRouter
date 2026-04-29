package org.unreal.modelrouter.common.api.monitor;

import java.util.Map;

/**
 * 链路追踪接口 - 其他模块通过此接口上报追踪数据。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>router 模块通过此接口上报路由追踪</li>
 *   <li>auth 模块通过此接口上报认证追踪</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 monitor-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface TracingApi {

    /**
     * 开始追踪。
     *
     * @param traceId 追踪 ID
     * @param operation 操作名称
     * @param context 追踪上下文
     */
    void startTrace(String traceId, String operation, Map<String, Object> context);

    /**
     * 结束追踪。
     *
     * @param traceId 追踪 ID
     * @param success 是否成功
     * @param durationMs 持续时间（毫秒）
     */
    void endTrace(String traceId, boolean success, long durationMs);

    /**
     * 添加追踪事件。
     *
     * @param traceId 追踪 ID
     * @param eventName 事件名称
     * @param eventData 事件数据
     */
    void addTraceEvent(String traceId, String eventName, Map<String, Object> eventData);

    /**
     * 获取追踪数据。
     *
     * @param traceId 追踪 ID
     * @return 追踪数据
     */
    Map<String, Object> getTraceData(String traceId);

    /**
     * 获取慢追踪列表。
     *
     * @param thresholdMs 阈值（毫秒）
     * @param limit 数量限制
     * @return 慢追踪列表
     */
    java.util.List<Map<String, Object>> getSlowTraces(long thresholdMs, int limit);
}
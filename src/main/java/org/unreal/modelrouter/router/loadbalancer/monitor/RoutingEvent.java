package org.unreal.modelrouter.router.loadbalancer.monitor;

import java.time.Instant;

/**
 * 路由事件记录
 * 记录每次负载均衡器选择实例的决策过程
 *
 * @param timestamp    事件时间戳
 * @param serviceType  服务类型 (chat, embedding, rerank, tts, stt等)
 * @param modelName    模型名称 (qwen2.5:1.5b, llama3 等)
 * @param strategy     负载均衡策略 (random, round-robin, least-connections, ip-hash, consistent-hash)
 * @param selectedInstance 选中的实例ID
 * @param selectedInstanceUrl 选中的实例URL
 * @param clientId     客户端标识 (IP地址或请求key)
 * @param candidateCount 候选实例数量
 * @param selectionTimeMs 选择耗时(毫秒)
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
public record RoutingEvent(
    Instant timestamp,
    String serviceType,
    String modelName,
    String strategy,
    String selectedInstance,
    String selectedInstanceUrl,
    String clientId,
    int candidateCount,
    long selectionTimeMs
) {
    /**
     * 创建路由事件（不含模型名，向后兼容）
     */
    public static RoutingEvent of(
            String serviceType,
            String strategy,
            String selectedInstance,
            String selectedInstanceUrl,
            String clientId,
            int candidateCount,
            long selectionTimeMs) {
        return of(serviceType, null, strategy, selectedInstance, selectedInstanceUrl,
            clientId, candidateCount, selectionTimeMs);
    }

    /**
     * 创建路由事件（包含模型名）
     */
    public static RoutingEvent of(
            String serviceType,
            String modelName,
            String strategy,
            String selectedInstance,
            String selectedInstanceUrl,
            String clientId,
            int candidateCount,
            long selectionTimeMs) {
        return new RoutingEvent(
            Instant.now(),
            serviceType,
            modelName,
            strategy,
            selectedInstance,
            selectedInstanceUrl,
            clientId,
            candidateCount,
            selectionTimeMs
        );
    }

    /**
     * 转换为紧凑的字符串表示（用于日志）
     */
    public String toCompactString() {
        return String.format("[%s] %s -> %s (%s, %d candidates, %dms)",
            serviceType,
            clientId != null ? clientId : "N/A",
            selectedInstance,
            strategy,
            candidateCount,
            selectionTimeMs);
    }
}

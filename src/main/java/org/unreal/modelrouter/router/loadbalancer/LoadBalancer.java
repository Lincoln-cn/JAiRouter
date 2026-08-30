package org.unreal.modelrouter.router.loadbalancer;

import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.List;

/**
 * 实现了四种负载均衡策略：
 * RandomLoadBalancer: 随机策略（支持权重）
 * RoundRobinLoadBalancer: 轮询策略（支持权重）
 * LeastConnectionsLoadBalancer: 最少连接策略（支持权重）
 * IpHashLoadBalancer: IP Hash策略（一致性哈希，支持权重）
 */
public interface LoadBalancer {
    
    /**
     * 选择一个实例
     * @param instances 可用实例列表
     * @param clientIp 客户端IP (用于IP Hash策略)
     * @return 选中的实例
     */
    ModelRouterProperties.ModelInstance selectInstance(
            List<ModelRouterProperties.ModelInstance> instances,
            String clientIp);

    /**
     * 选择一个实例（带服务上下文）
     * @param instances 可用实例列表
     * @param clientIp 客户端IP (用于IP Hash策略)
     * @param serviceType 服务类型
     * @return 选中的实例
     */
    default ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String clientIp, final String serviceType) {
        return selectInstance(instances, clientIp);
    }

    /**
     * 选择一个实例（带会话亲和性支持）
     * v2.9.0: 支持 sticky routing，通过 affinityKey 将同一租户的请求固定到同一实例
     *
     * @param instances 可用实例列表
     * @param affinityKey 会话亲和性键(apiKeyId|serviceType|modelName)，null 表示无亲和性
     * @param clientIp 客户端IP (用于IP Hash策略或亲和性回退)
     * @param serviceType 服务类型
     * @return 选中的实例
     */
    default ModelRouterProperties.ModelInstance selectInstance(
            final List<ModelRouterProperties.ModelInstance> instances,
            final String affinityKey,
            final String clientIp,
            final String serviceType) {
        return selectInstance(instances, clientIp, serviceType);
    }

    /**
     * 记录实例调用
     * @param instance 被调用的实例
     */
    default void recordCall(final ModelRouterProperties.ModelInstance instance) {
        // 默认空实现
    }

    /**
     * 记录实例调用完成
     * @param instance 调用完成的实例
     */
    default void recordCallComplete(final ModelRouterProperties.ModelInstance instance) {
        // 默认空实现
    }

    /**
     * 记录实例调用失败
     * @param instance 调用失败的实例
     */
    default void recordCallFailure(final ModelRouterProperties.ModelInstance instance) { }

    /**
     * 记录实例调用完成（带调用时长和成功状态）
     * <p>
     * v2.9.3: 默认委托给 {@link #recordCallComplete(ModelRouterProperties.ModelInstance)}，
     * 保持现有实现无需修改。
     *
     * @param instance   调用完成的实例
     * @param durationMs 调用耗时（毫秒）
     * @param success    是否成功
     */
    default void recordCallComplete(final ModelRouterProperties.ModelInstance instance,
                                    final long durationMs,
                                    final boolean success) {
        recordCallComplete(instance);
    }

    /**
     * 记录实例调用失败（带调用时长和错误码）
     * <p>
     * v2.9.3: 默认委托给 {@link #recordCallFailure(ModelRouterProperties.ModelInstance)}，
     * 保持现有实现无需修改。
     *
     * @param instance   调用失败的实例
     * @param durationMs 调用耗时（毫秒）
     * @param errorCode  错误码
     */
    default void recordCallFailure(final ModelRouterProperties.ModelInstance instance,
                                   final long durationMs,
                                   final String errorCode) {
        recordCallFailure(instance);
    }

}
package org.unreal.modelrouter.loadbalancer;

import org.unreal.modelrouter.config.ModelRouterProperties;

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
    ModelRouterProperties.ModelInstance selectInstance(List<ModelRouterProperties.ModelInstance> instances, String clientIp);

    /**
     * 记录实例调用
     * @param instance 被调用的实例
     */
    default void recordCall(ModelRouterProperties.ModelInstance instance) {
        // 默认空实现
    }

    /**
     * 记录实例调用完成
     * @param instance 调用完成的实例
     */
    default void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
        // 默认空实现
    }

    /**
     * 记录实例调用失败
     * @param instance 调用失败的实例
     */
    default void recordCallFailure(ModelRouterProperties.ModelInstance instance) {}


}
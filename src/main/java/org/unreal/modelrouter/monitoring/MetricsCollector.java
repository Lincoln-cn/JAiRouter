package org.unreal.modelrouter.monitoring;

/**
 * 指标收集器接口
 * 定义了各种指标收集的标准方法
 */
public interface MetricsCollector {

    /**
     * 记录请求指标
     * 
     * @param service 服务名称 (chat, embedding等)
     * @param method HTTP方法
     * @param duration 响应时间(毫秒)
     * @param status 状态码或状态描述
     */
    void recordRequest(String service, String method, long duration, String status);

    /**
     * 记录后端调用指标
     * 
     * @param adapter 适配器类型 (gpustack, ollama等)
     * @param instance 实例名称
     * @param duration 调用时长(毫秒)
     * @param success 是否成功
     */
    void recordBackendCall(String adapter, String instance, long duration, boolean success);

    /**
     * 记录限流器指标
     * 
     * @param service 服务名称
     * @param algorithm 限流算法
     * @param allowed 是否允许通过
     */
    void recordRateLimit(String service, String algorithm, boolean allowed);

    /**
     * 记录熔断器指标
     * 
     * @param service 服务名称
     * @param state 熔断器状态
     * @param event 事件类型
     */
    void recordCircuitBreaker(String service, String state, String event);

    /**
     * 记录负载均衡器指标
     * 
     * @param service 服务名称
     * @param strategy 负载均衡策略
     * @param selectedInstance 选中的实例
     */
    void recordLoadBalancer(String service, String strategy, String selectedInstance);

    /**
     * 记录健康检查指标
     * 
     * @param adapter 适配器类型
     * @param instance 实例名称
     * @param healthy 是否健康
     * @param responseTime 响应时间(毫秒)
     */
    void recordHealthCheck(String adapter, String instance, boolean healthy, long responseTime);

    /**
     * 记录请求大小指标
     * 
     * @param service 服务名称
     * @param requestSize 请求大小(字节)
     * @param responseSize 响应大小(字节)
     */
    void recordRequestSize(String service, long requestSize, long responseSize);
}
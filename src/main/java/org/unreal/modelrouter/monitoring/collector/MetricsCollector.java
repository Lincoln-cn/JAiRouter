package org.unreal.modelrouter.monitoring.collector;

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

    /**
     * 记录追踪指标
     * 
     * @param traceId 追踪ID
     * @param spanId Span ID
     * @param operationName 操作名称
     * @param duration 持续时间(毫秒)
     * @param success 是否成功
     */
    void recordTrace(String traceId, String spanId, String operationName, long duration, boolean success);

    /**
     * 记录追踪导出指标
     * 
     * @param exporterType 导出器类型
     * @param duration 导出耗时(毫秒)
     * @param success 是否成功
     * @param batchSize 批量大小
     */
    void recordTraceExport(String exporterType, long duration, boolean success, int batchSize);

    /**
     * 记录追踪采样指标
     * 
     * @param samplingRate 采样率
     * @param sampled 是否被采样
     */
    void recordTraceSampling(double samplingRate, boolean sampled);

    /**
     * 记录追踪数据质量指标
     * 
     * @param traceId 追踪ID
     * @param spanCount Span数量
     * @param attributeCount 属性数量
     * @param errorCount 错误数量
     */
    void recordTraceDataQuality(String traceId, int spanCount, int attributeCount, int errorCount);
    
    /**
     * 记录追踪处理指标
     * 
     * @param processorName 处理器名称
     * @param duration 处理耗时(毫秒)
     * @param success 是否成功
     */
    void recordTraceProcessing(String processorName, long duration, boolean success);
    
    /**
     * 记录追踪分析指标
     * 
     * @param analyzerName 分析器名称
     * @param spanCount Span数量
     * @param duration 分析耗时(毫秒)
     * @param success 是否成功
     */
    void recordTraceAnalysis(String analyzerName, int spanCount, long duration, boolean success);
}
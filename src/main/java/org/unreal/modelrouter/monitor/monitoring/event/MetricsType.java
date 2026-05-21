package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 指标类型枚举
 * 用于采样率配置
 */
public enum MetricsType {
    /** 请求指标 */
    REQUEST,
    /** 后端调用指标 */
    BACKEND,
    /** 基础设施指标（限流、熔断、负载均衡、健康检查） */
    INFRASTRUCTURE,
    /** 追踪指标 */
    TRACE,
    /** 追踪处理指标 */
    TRACE_PROCESSING,
    /** 追踪分析指标 */
    TRACE_ANALYSIS
}

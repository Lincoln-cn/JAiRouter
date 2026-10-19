package org.unreal.modelrouter.router.circuitbreaker.monitor;

/**
 * 熔断器监控视图中的单个实例。
 *
 * <p>数据来源合并两处：熔断器运行时（状态、失败/成功计数、阈值，来自
 * {@code LockFreeCircuitBreaker#getStateDetail()}）与实例表（实例名、服务类型、上游地址，来自
 * {@code ServiceInstanceEntity} + {@code ServiceConfigEntity}）。实例名与服务类型可能为空——
 * 熔断器先于实例注册表存在时只能给出 UUID。
 *
 * @param instanceId 实例 UUID（熔断器 key）
 * @param instanceName 实例名；实例已不存在时为 null
 * @param serviceType 服务类型；实例已不存在时为 null
 * @param baseUrl 上游地址（baseUrl + path）；实例已不存在时为 null
 * @param state 当前状态名（CLOSED / OPEN / HALF_OPEN）
 * @param failureCount 连续失败计数
 * @param successCount HALF_OPEN 下的连续成功计数
 * @param failureThreshold 触发熔断的失败阈值
 * @param successThreshold HALF_OPEN 转 CLOSED 所需的成功阈值
 * @param timeoutMillis OPEN 转 HALF_OPEN 的等待时长（毫秒）
 * @param lastFailureTime 最近一次失败时间（epoch 毫秒），从未失败时为 null
 * @author JAiRouter Team
 * @since 3.3.6
 */
public record CircuitBreakerInstanceView(
        String instanceId,
        String instanceName,
        String serviceType,
        String baseUrl,
        String state,
        int failureCount,
        int successCount,
        int failureThreshold,
        int successThreshold,
        long timeoutMillis,
        Long lastFailureTime
) {
}

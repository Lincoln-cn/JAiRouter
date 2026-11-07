package org.unreal.modelrouter.router.circuitbreaker.monitor;

/**
 * 单个实例的手动重置结果。
 *
 * @param instanceId 实例 UUID
 * @param instanceName 实例名；实例已不存在时为 null
 * @param serviceType 服务类型；实例已不存在时为 null
 * @param previousState 重置前状态
 * @param currentState 重置后状态（恒为 CLOSED）
 * @param persistedStateDeleted 持久化层是否确实删除了该实例的状态记录
 * @param triggerReason 写入历史记录的触发原因（固定 MANUAL_RESET，操作者备注见日志）
 * @param lastFailureReason 重置前最近一次真实失败原因标识（issue #1010；无失败详情时为 null）
 * @author JAiRouter Team
 * @since 3.3.6
 */
public record CircuitBreakerResetResult(
        String instanceId,
        String instanceName,
        String serviceType,
        String previousState,
        String currentState,
        boolean persistedStateDeleted,
        String triggerReason,
        String lastFailureReason
) {
}

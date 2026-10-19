package org.unreal.modelrouter.router.circuitbreaker.monitor;

/**
 * 批量手动重置的结果。
 *
 * @param matched 命中过滤条件的实例数
 * @param reset 实际重置成功的实例（逐个结果）
 * @author JAiRouter Team
 * @since 3.3.6
 */
public record CircuitBreakerBatchResetResult(
        int matched,
        java.util.List<CircuitBreakerResetResult> reset
) {
}

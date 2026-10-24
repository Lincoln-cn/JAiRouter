package org.unreal.modelrouter.auth.security.quota;

/**
 * 配额分布式计数的降级状态（最近一次 Redis 操作是否失败、失败原因）。
 *
 * <p>从 {@code QuotaLedgerService} 抽出（issue #604 批次 B）：写路径与读路径都要标记 / 清除它，
 * 抽成独立对象后 {@code QuotaDistributedWriter} 与账本服务共享同一份状态，不必把状态字段
 * 和一堆 {@code markDegraded} 调用散在 1200 行的服务里。</p>
 *
 * <p>线程安全：两个字段都是 {@code volatile}，语义与抽取前一致（读多写少、允许瞬时旧值）。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.1
 */
final class QuotaDegradationState {

    private volatile boolean degraded;

    private volatile String reason = "";

    /**
     * 当前是否降级。
     *
     * @return 降级时为 {@code true}
     */
    boolean degraded() {
        return degraded;
    }

    /**
     * 降级原因。
     *
     * @return 原因；未降级时为空串
     */
    String reason() {
        return reason;
    }

    /**
     * 标记降级（每次失败都覆盖原因，取最近一次）。
     *
     * @param newReason 降级原因
     */
    void mark(final String newReason) {
        this.reason = newReason;
        this.degraded = true;
    }

    /**
     * 清除降级状态（一次成功的分布式操作即视为恢复）。
     *
     * @return 清除前是否处于降级状态（供调用方决定是否打「已恢复」日志）
     */
    boolean clear() {
        final boolean wasDegraded = degraded;
        this.degraded = false;
        this.reason = "";
        return wasDegraded;
    }
}

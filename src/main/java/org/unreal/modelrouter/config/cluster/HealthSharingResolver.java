package org.unreal.modelrouter.config.cluster;

/**
 * 健康判定语义的纯函数实现（issue #1137）。
 *
 * <p>把「本地探测结果 + 共享视图」合并成最终判定的逻辑单独抽出来，是为了能在没有任何
 * Redis / Spring 上下文的情况下做单元测试，也便于在热路径上被 {@code ServiceStateManager}
 * 以同步方式调用（只读内存快照，不发起 IO）。</p>
 */
public final class HealthSharingResolver {

    /** 未启用共享或不在集群模式时的语义。 */
    public static final String MODE_LOCAL = "local";
    /** 乐观语义：共享视图里任一健康副本即可。 */
    public static final String MODE_SHARED = "shared";
    /** 折中语义：本地健康 或 健康副本数达到容忍值。 */
    public static final String MODE_QUORUM = "quorum";

    private HealthSharingResolver() {
    }

    /**
     * 合并本地与共享视图。
     *
     * @param mode              判定语义（未知值按 quorum 处理）
     * @param toleranceCount    quorum 所需的最小健康副本数
     * @param graceMillis       共享上报的有效期（毫秒）
     * @param localHealthy      本副本的探测结果
     * @param healthyReplicas   共享视图里认为该实例健康的副本数
     * @param lastReportedAt    共享视图里该实例最近一次上报的时间戳（毫秒），无上报传 0
     * @param now               当前时间（毫秒）
     * @return 最终判定
     */
    public static boolean resolve(final String mode,
                                  final int toleranceCount,
                                  final long graceMillis,
                                  final boolean localHealthy,
                                  final int healthyReplicas,
                                  final long lastReportedAt,
                                  final long now) {
        if (MODE_LOCAL.equals(mode)) {
            return localHealthy;
        }
        final boolean fresh = lastReportedAt > 0 && now - lastReportedAt <= Math.max(0L, graceMillis);
        if (MODE_SHARED.equals(mode)) {
            // 没有可用的共享上报时退回本地判断，避免「共享视图为空 ⇒ 全部判不健康」
            return fresh ? healthyReplicas > 0 : localHealthy;
        }
        // quorum（含未识别的取值）：本地健康直接放行；本地不健康时看共享视图是否达到容忍值
        if (localHealthy) {
            return true;
        }
        if (!fresh) {
            return false;
        }
        return healthyReplicas >= Math.max(1, toleranceCount);
    }
}

package org.unreal.modelrouter.auth.security.quota;

import java.time.LocalDateTime;

/**
 * 配额账本用量读数（某一维度在某一窗口内的累计用量）。
 *
 * <p>账本为旁路只读视图：{@link #requestCount()} / {@link #tokenCount()} 已做非负钳制，
 * 不会因为回滚产生负数。</p>
 *
 * @param dimension    维度
 * @param window       窗口类型
 * @param windowStart  窗口起点（与数据库 {@code window_start} 列一致）
 * @param requestCount 窗口内累计请求数
 * @param tokenCount   窗口内累计 token 数
 * @author JAiRouter Team
 * @since 3.1.0
 */
public record QuotaUsage(QuotaDimension dimension,
                         QuotaWindow window,
                         LocalDateTime windowStart,
                         long requestCount,
                         long tokenCount) {

    /**
     * 判断该用量是否已经过期（窗口起点早于给定截止时间）。
     *
     * @param cutoff 截止时间
     * @return 窗口起点早于截止时间时返回 {@code true}
     */
    public boolean expiredBefore(final LocalDateTime cutoff) {
        return cutoff != null && windowStart != null && windowStart.isBefore(cutoff);
    }
}

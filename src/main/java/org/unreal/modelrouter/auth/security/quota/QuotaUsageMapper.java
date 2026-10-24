package org.unreal.modelrouter.auth.security.quota;

import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;

/**
 * 配额账本的读数映射（数据库行 / 计数值 → {@link QuotaUsage}）。
 *
 * <p>从 {@code QuotaLedgerService} 抽出（issue #604 批次 B）：这些方法都是纯函数，
 * 与账本的生命周期、后端切换、降级语义无关，单独成类后账本服务只剩编排逻辑。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.1
 */
final class QuotaUsageMapper {

    private QuotaUsageMapper() {
    }

    /**
     * 计数键 → 用量读数（非负钳制）。
     *
     * @param key    计数键
     * @param totals 计数值 {@code [requests, tokens]}
     * @return 用量读数
     */
    static QuotaUsage toUsage(final QuotaCounterKey key, final long[] totals) {
        return new QuotaUsage(key.dimension(), key.window(), key.windowStart(),
            Math.max(0L, totals[0]), Math.max(0L, totals[1]));
    }

    /**
     * 数据库行 → 用量读数。
     *
     * @param key 计数键
     * @param row 数据库行
     * @return 用量读数
     */
    static QuotaUsage toUsage(final QuotaCounterKey key, final QuotaLedgerEntity row) {
        return new QuotaUsage(key.dimension(), key.window(), key.windowStart(),
            Math.max(0L, nullToZero(row.getRequestCount())), Math.max(0L, nullToZero(row.getTokenCount())));
    }

    /**
     * 数据库行 → 维度（空值由实体自身的哨兵语义处理）。
     *
     * @param row 数据库行
     * @return 维度
     */
    static QuotaDimension dimensionOf(final QuotaLedgerEntity row) {
        return new QuotaDimension(row.getTenantId(), row.getApiKeyId(), row.getUserId(),
            row.getServiceType(), row.getModel());
    }

    /**
     * {@code Long} → {@code long} 空值兜底。
     *
     * @param value 可能为 {@code null} 的计数
     * @return 非 {@code null} 的计数
     */
    static long nullToZero(final Long value) {
        return value == null ? 0L : value;
    }
}

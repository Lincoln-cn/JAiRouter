package org.unreal.modelrouter.auth.security.quota;

/**
 * 配额预留决策结果。
 *
 * <p>账本异常时默认 fail-open：{@link #allowed()} 为 {@code true}（放行请求）、
 * {@link #degraded()} 为 {@code true}（标记本次决策发生在降级状态，便于观测与后续补偿），
 * 并同时携带 {@link #reason()} 便于日志定位。当 {@code jairouter.quota.fail-open=false}
 * 时异常改为 fail-closed（{@link #allowed()} 为 {@code false}）。</p>
 *
 * @param allowed  是否放行本次请求
 * @param degraded 本次决策是否处于降级状态（账本不可用 / 未启用）
 * @param reason   决策原因，未设置时为空串（不会为 {@code null}）
 * @author JAiRouter Team
 * @since 3.1.0
 */
public record QuotaDecision(boolean allowed, boolean degraded, String reason) {

    /** 账本未启用（默认状态，零行为变更） */
    public static final String REASON_DISABLED = "quota-ledger-disabled";

    /** 账本可用（正常放行） */
    public static final String REASON_OK = "";

    /** 账本读写异常（fail-open / fail-closed 的触发原因） */
    public static final String REASON_LEDGER_UNAVAILABLE = "quota-ledger-unavailable";

    /** 请求参数非法（维度缺失等） */
    public static final String REASON_INVALID_REQUEST = "invalid-request";

    /**
     * 规范化构造：{@link #reason()} 的 {@code null} 折叠为空串。
     */
    public QuotaDecision {
        reason = reason == null ? REASON_OK : reason;
    }

    /**
     * 正常放行。
     *
     * @return 放行且未降级的决策
     */
    public static QuotaDecision allow() {
        return new QuotaDecision(true, false, REASON_OK);
    }

    /**
     * 账本未启用时的放行决策（默认配置下的唯一返回路径）。
     *
     * @return 放行、未降级、原因为“未启用”的决策
     */
    public static QuotaDecision disabled() {
        return new QuotaDecision(true, false, REASON_DISABLED);
    }

    /**
     * fail-open：放行请求但标记降级。
     *
     * @param reason 降级原因
     * @return 放行且降级的决策
     */
    public static QuotaDecision failOpen(final String reason) {
        return new QuotaDecision(true, true, reason);
    }

    /**
     * fail-closed：拒绝请求并标记降级。
     *
     * @param reason 降级原因
     * @return 拒绝且降级的决策
     */
    public static QuotaDecision failClosed(final String reason) {
        return new QuotaDecision(false, true, reason);
    }
}

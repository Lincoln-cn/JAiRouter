package org.unreal.modelrouter.auth.security.quota;

/**
 * 配额预留请求（一次即将发起的模型调用）。
 *
 * <p>PR-1 只做账本记账，不做限额判定：{@link QuotaLedgerService#reserve(QuotaRequest)}
 * 恒返回放行（除非配置为非 fail-open 且账本不可用），限额判定在后续 PR 引入。</p>
 *
 * @param dimension       请求维度，{@code null} 时归一化为 {@link QuotaDimension#empty()}
 * @param estimatedTokens 预估消耗 token 数（负数按 0 处理），结算时按实际值冲正
 * @author JAiRouter Team
 * @since 3.1.0
 */
public record QuotaRequest(QuotaDimension dimension, long estimatedTokens) {

    /**
     * 规范化构造：维度 {@code null} 折叠为空维度，预估 token 数负数钳制为 0。
     */
    public QuotaRequest {
        dimension = dimension == null ? QuotaDimension.empty() : dimension;
        estimatedTokens = Math.max(estimatedTokens, 0L);
    }

    /**
     * 构造一个不计 token 的预留请求（仅统计请求数）。
     *
     * @param dimension 请求维度
     * @return 预留请求
     */
    public static QuotaRequest of(final QuotaDimension dimension) {
        return new QuotaRequest(dimension, 0L);
    }

    /**
     * 构造带预估 token 的预留请求。
     *
     * @param dimension       请求维度
     * @param estimatedTokens 预估 token 数，负数按 0 处理
     * @return 预留请求
     */
    public static QuotaRequest of(final QuotaDimension dimension, final long estimatedTokens) {
        return new QuotaRequest(dimension, estimatedTokens);
    }
}

package org.unreal.modelrouter.auth.security.quota;

/**
 * 配额结算（一次模型调用结束后的冲正）。
 *
 * <p>冲正规则：</p>
 * <ul>
 *   <li>成功：token 冲正量 = {@code actualTokens - estimatedTokens}（按实际用量修正预留值），
 *       请求数不变；</li>
 *   <li>失败（{@link #failed()} 为 {@code true}）：整笔回滚，token 冲正量为
 *       {@code -estimatedTokens}、请求数冲正量为 {@code -1}。</li>
 * </ul>
 *
 * <p>注意：结算按“当前时刻所属窗口”冲正。若预留发生在上一分钟窗口、结算落在下一分钟窗口，
 * 则该笔冲正落在新窗口（旧窗口保留预留值）——单节点账本按窗口对齐，跨窗口冲正属已知偏差。</p>
 *
 * @param dimension       请求维度，{@code null} 时归一化为 {@link QuotaDimension#empty()}
 * @param estimatedTokens 预留时使用的预估 token 数（负数按 0 处理）
 * @param actualTokens    实际消耗 token 数（负数按 0 处理；失败时忽略）
 * @param failed          本次调用是否失败（失败时回滚整笔预留）
 * @author JAiRouter Team
 * @since 3.1.0
 */
public record QuotaSettlement(QuotaDimension dimension, long estimatedTokens, long actualTokens, boolean failed) {

    /**
     * 规范化构造：维度 {@code null} 折叠为空维度，token 数负数钳制为 0。
     */
    public QuotaSettlement {
        dimension = dimension == null ? QuotaDimension.empty() : dimension;
        estimatedTokens = Math.max(estimatedTokens, 0L);
        actualTokens = Math.max(actualTokens, 0L);
    }

    /**
     * token 冲正量。
     *
     * @return 失败时返回 {@code -estimatedTokens}，否则返回 {@code actualTokens - estimatedTokens}
     */
    public long tokenDelta() {
        return failed ? -estimatedTokens : actualTokens - estimatedTokens;
    }

    /**
     * 请求数冲正量。
     *
     * @return 失败时返回 {@code -1}，否则返回 {@code 0}
     */
    public long requestDelta() {
        return failed ? -1L : 0L;
    }
}

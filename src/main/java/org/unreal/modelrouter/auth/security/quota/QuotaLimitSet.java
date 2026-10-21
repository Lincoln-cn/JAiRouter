package org.unreal.modelrouter.auth.security.quota;

/**
 * 一次配额判定 / 预留所依据的限额集合（四个窗口，v3.3 TA3）。
 *
 * <p>把 {@code ApiKey} 的限额字段打包成一个不可变值对象，供
 * {@link QuotaLedgerService#reserveWithLimits(QuotaRequest, QuotaLimitSet)} 与
 * {@link QuotaEnforcementService} 共用：账本侧只关心「某个窗口的请求数 / token 上限是多少」，
 * 不需要依赖 API Key 配置类型，也不需要在每个窗口各开一个参数。</p>
 *
 * <p><b>语义</b>：所有取值 {@code <= 0} 表示该窗口不限制（与 {@code ApiKey} 既有语义一致，
 * 见 {@code ApiKey.dailyRequestLimit} 等字段的 javadoc）。{@link QuotaWindow#MINUTE} 只有请求数
 * 上限（既有 {@code rateLimitPerMinute} 没有对应的 token 额度），其余三个窗口请求数与 token
 * 各自独立。</p>
 *
 * <p><b>窗口未启用</b>：本记录只描述「限额是多少」，是否参与判定由
 * {@code jairouter.quota.windows}（{@link QuotaProperties#enabledWindows()}）决定；未启用的窗口
 * 无法度量，按 fail-open 跳过（见 {@link QuotaEnforcementService} 的 javadoc）。</p>
 *
 * @param minuteRequests 每分钟请求数上限（{@code ApiKey.rateLimitPerMinute}）
 * @param hourRequests   每小时请求数上限（{@code ApiKey.hourlyRequestLimit}）
 * @param hourTokens     每小时 token 上限（{@code ApiKey.hourlyTokenLimit}）
 * @param dayRequests    每日请求数上限（{@code ApiKey.dailyRequestLimit}）
 * @param dayTokens      每日 token 上限（{@code ApiKey.dailyTokenLimit}）
 * @param monthRequests  每月请求数上限（{@code ApiKey.monthlyRequestLimit}）
 * @param monthTokens    每月 token 上限（{@code ApiKey.monthlyTokenLimit}）
 * @author JAiRouter Team
 * @since 3.3.0
 */
public record QuotaLimitSet(long minuteRequests,
                            long hourRequests,
                            long hourTokens,
                            long dayRequests,
                            long dayTokens,
                            long monthRequests,
                            long monthTokens) {

    /** 全部窗口不限制（只累加用量，不判定超限）。 */
    public static final QuotaLimitSet UNLIMITED = new QuotaLimitSet(0L, 0L, 0L, 0L, 0L, 0L, 0L);

    /**
     * 取指定窗口的请求数上限。
     *
     * @param window 窗口
     * @return 请求数上限，{@code <= 0} 表示不限制
     */
    public long maxRequests(final QuotaWindow window) {
        return switch (window) {
            case MINUTE -> minuteRequests;
            case HOUR -> hourRequests;
            case DAY -> dayRequests;
            case MONTH -> monthRequests;
        };
    }

    /**
     * 取指定窗口的 token 上限。
     *
     * @param window 窗口
     * @return token 上限，{@code <= 0} 表示不限制；{@link QuotaWindow#MINUTE} 恒为 {@code 0}
     */
    public long maxTokens(final QuotaWindow window) {
        return switch (window) {
            // 速率限额只限请求数（既有 rateLimitPerMinute 无 token 对应项）
            case MINUTE -> 0L;
            case HOUR -> hourTokens;
            case DAY -> dayTokens;
            case MONTH -> monthTokens;
        };
    }

    /**
     * 两个窗口维度是否全部不限（{@code <= 0}）。
     *
     * @param window 窗口
     * @return 该窗口的请求数与 token 均不限制时返回 {@code true}
     */
    public boolean isUnlimited(final QuotaWindow window) {
        return maxRequests(window) <= 0L && maxTokens(window) <= 0L;
    }
}

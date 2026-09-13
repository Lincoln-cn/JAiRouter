package org.unreal.modelrouter.auth.security.quota;

/**
 * 配额超限判定结果（v3.1 PR-2）。
 *
 * <p>限额来自既有 {@code ApiKey} 配置，一次判定只报告“最先命中”的那一项限额（判定优先级：
 * 日请求数 → 日 token 数 → 每分钟请求数），因此本记录同时携带被命中的指标名与窗口，
 * 供 429 响应头（{@code X-Quota-Limit} / {@code X-Quota-Remaining} / {@code X-Quota-Window}）
 * 与日志使用。</p>
 *
 * @param metric            被命中的限额指标名，取值见 {@link #METRIC_DAILY_REQUESTS} 等常量
 * @param window            被命中的窗口（日限额对应 {@link QuotaWindow#DAY}，速率限额对应
 *                          {@link QuotaWindow#MINUTE}）
 * @param limit             该项限额的配置值（{@code > 0}）
 * @param used              判定时该窗口内的已用额度（{@link #METRIC_DAILY_REQUESTS} /
 *                          {@link #METRIC_RATE_PER_MINUTE} 为请求数，{@link #METRIC_DAILY_TOKENS}
 *                          为 token 数）
 * @param retryAfterSeconds 距离窗口重置（下一窗口边界）的秒数，最小为 1
 * @author JAiRouter Team
 * @since 3.1.0
 */
public record QuotaLimitViolation(String metric,
                                 QuotaWindow window,
                                 long limit,
                                 long used,
                                 long retryAfterSeconds) {

    /** 日请求数限额（{@code ApiKey.dailyRequestLimit}） */
    public static final String METRIC_DAILY_REQUESTS = "dailyRequestLimit";

    /** 日 token 限额（{@code ApiKey.dailyTokenLimit}） */
    public static final String METRIC_DAILY_TOKENS = "dailyTokenLimit";

    /** 每分钟请求限额（{@code ApiKey.rateLimitPerMinute}） */
    public static final String METRIC_RATE_PER_MINUTE = "rateLimitPerMinute";

    /**
     * 剩余额度（非负钳制）：已用额度超过配置值时返回 0。
     *
     * @return 剩余额度
     */
    public long remaining() {
        return Math.max(0L, limit - used);
    }

    /**
     * 生成 429 响应体中使用的说明文本（响应体本身仍由仓库既有错误响应惯例渲染）。
     *
     * @return 说明文本，如 {@code "API Key quota exceeded: metric=dailyTokenLimit, window=DAY, limit=1000, used=900"}
     */
    public String describe() {
        return "API Key quota exceeded: metric=" + metric + ", window=" + window.name()
            + ", limit=" + limit + ", used=" + used;
    }
}

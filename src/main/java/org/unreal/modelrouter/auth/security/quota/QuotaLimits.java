package org.unreal.modelrouter.auth.security.quota;

import org.unreal.modelrouter.common.exception.ApiException;

/**
 * 配额告警阈值的合法区间（单一事实来源）。
 *
 * <p>issue #96：该字段此前三处口径不一致 —— 创建对话框 {@code min=0}、配额抽屉 {@code min=0.05}、
 * 服务端 {@code (0,1)} 开区间，且创建接口完全没有校验。结果是「创建时能填 0，之后抽屉改不回去」。</p>
 *
 * <p>现统一为闭区间 {@code [0.05, 1.0]}：</p>
 * <ul>
 *   <li>{@code 0.05} 下限：避免 {@code threshold=0} 导致「一有用量就告警」的无意义行为</li>
 *   <li>{@code 1.0} 上限：允许「只在达到 100% 时告警」</li>
 * </ul>
 *
 * <p>创建、更新两条写入路径统一走 {@link #validateAlertThreshold(Double)}，前端 slider 的
 * {@code min}/{@code max} 需与此保持一致。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.2
 */
public final class QuotaLimits {

    /** 告警阈值下限（含）。 */
    public static final double MIN_ALERT_THRESHOLD = 0.05;

    /** 告警阈值上限（含）。 */
    public static final double MAX_ALERT_THRESHOLD = 1.0;

    private QuotaLimits() {
    }

    /**
     * 校验告警阈值是否落在允许区间内。
     *
     * @param threshold 待校验的阈值；{@code null} 表示不修改，直接放行
     * @throws ApiException 越界时抛出（错误码 {@code INVALID_REQUEST} → HTTP 400）
     */
    public static void validateAlertThreshold(final Double threshold) {
        if (threshold == null) {
            return;
        }
        if (threshold < MIN_ALERT_THRESHOLD || threshold > MAX_ALERT_THRESHOLD) {
            throw ApiException.of("INVALID_REQUEST",
                    "quotaAlertThreshold 必须在 [" + MIN_ALERT_THRESHOLD + ", "
                            + MAX_ALERT_THRESHOLD + "] 区间");
        }
    }
}

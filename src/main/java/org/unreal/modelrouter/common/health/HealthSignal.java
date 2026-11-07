package org.unreal.modelrouter.common.health;

/**
 * 单个探针的结论。
 *
 * @param status  探针自身的结论（UP / DOWN / UNKNOWN）
 * @param severity DOWN 时的影响程度：{@link Severity#FATAL} 会让实例整体判为
 *                 {@link InstanceHealth#UNHEALTHY}，{@link Severity#SOFT} 则只降到
 *                 {@link InstanceHealth#DEGRADED}
 * @param reason  人可读的原因，会原样出现在日志与 API 响应里；UP 时可为 {@code null}
 */
public record HealthSignal(Status status, Severity severity, String reason) {

    /** 探针结论。 */
    public enum Status {
        UP,
        DOWN,
        UNKNOWN
    }

    /**
     * DOWN 的影响程度。
     *
     * <p>这条区分是刻意的：适配器没配上、熔断开着都属于「实例本身没坏」，
     * 判成 UNHEALTHY 会让一个配置疏漏变成整条链路不可用。</p>
     */
    public enum Severity {
        /** 致命：实例真的不能服务。 */
        FATAL,
        /** 非致命：能服务但有已知降级因素。 */
        SOFT
    }

    public static HealthSignal up() {
        return new HealthSignal(Status.UP, Severity.SOFT, null);
    }

    public static HealthSignal up(final String reason) {
        return new HealthSignal(Status.UP, Severity.SOFT, reason);
    }

    public static HealthSignal down(final Severity severity, final String reason) {
        return new HealthSignal(Status.DOWN, severity, reason);
    }

    public static HealthSignal unknown(final String reason) {
        return new HealthSignal(Status.UNKNOWN, Severity.SOFT, reason);
    }

    public boolean isDown() {
        return status == Status.DOWN;
    }
}

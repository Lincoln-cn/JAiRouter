package org.unreal.modelrouter.auth.security.ratelimit;

/**
 * 登录防护（暴力破解频控）的生效参数快照.
 *
 * <p>来源有两层：启动时 {@code @Value} 从 YAML 读到默认值，随后 {@code LoginGuardConfigService}
 * 用数据库里那一行覆盖（见 {@code LoginGuardConfigEntity}）。热改时由服务层调
 * {@link LoginBruteForceGuard#applySettings(LoginGuardSettings)} 整体换掉，避免读到「一半新一半旧」的组合。</p>
 *
 * @param maxFailuresPerPrincipal 同一来源 IP + 同一用户名的失败阈值（≥ 1）
 * @param maxFailuresPerIp        同一来源 IP 跨用户名的失败阈值（≥ 1）
 * @param windowMinutes           失败计数窗口（分钟，≥ 0；0 表示不累计）
 * @param lockMinutes             达到阈值后的锁定时长（分钟，≥ 0；0 表示只记录不锁定）
 * @since 2026-11（issue #1176）
 */
public record LoginGuardSettings(
        int maxFailuresPerPrincipal,
        int maxFailuresPerIp,
        int windowMinutes,
        int lockMinutes) {

    /**
     * 构造并校验参数。
     *
     * <p>阈值必须 ≥ 1（0 会让第一次失败就永久锁定，语义上等于拒绝一切登录）；
     * 窗口与锁定时长允许 0，分别表示「不累计」与「只记录不锁定」——测试里也靠这个把时间压到 0。</p>
     */
    public LoginGuardSettings {
        if (maxFailuresPerPrincipal < 1) {
            throw new IllegalArgumentException("maxFailuresPerPrincipal 必须 ≥ 1: " + maxFailuresPerPrincipal);
        }
        if (maxFailuresPerIp < 1) {
            throw new IllegalArgumentException("maxFailuresPerIp 必须 ≥ 1: " + maxFailuresPerIp);
        }
        if (windowMinutes < 0) {
            throw new IllegalArgumentException("windowMinutes 不能为负: " + windowMinutes);
        }
        if (lockMinutes < 0) {
            throw new IllegalArgumentException("lockMinutes 不能为负: " + lockMinutes);
        }
    }
}

package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录防护（暴力破解频控）的持久化配置.
 *
 * <p><b>为什么落库而不是只读 yml</b>：这几个阈值是线上出了状况要立刻调的旋钮 —— 例如误把管理员锁在门外时，
 * 运维要先把「锁定时长」压到 1 分钟、或者放宽「同 IP 失败次数」，而不是改 yml 再重启一次网关。
 * 因此采用与 {@code jwt_accounts} 相同的口径：<b>YAML 只提供首次初始值，落库之后以数据库为准</b>
 * （见 {@code JpaDatabaseInitializer.initializeJwtAccounts()} 的同款做法）。</p>
 *
 * <p>表固定只有一行（{@link #DEFAULT_ID}）。单行而非键值表，是因为这四项永远整体读写，键值表只会让
 * 「当前生效值」的读取多一层拼装，还会引入「某一行缺失」的半配置状态。</p>
 *
 * <p>与 {@code LoginBruteForceGuard} 的计数状态（内存）分工：<b>这里存的是规则，那边存的是计数</b>。
 * 重启只会清掉计数，不会丢掉这里改过的阈值。</p>
 *
 * @since 2026-11（issue #1176）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "security_login_guard_config")
public class LoginGuardConfigEntity {

    /** 单行配置的固定主键。 */
    public static final String DEFAULT_ID = "default";

    /** 主键（固定 {@link #DEFAULT_ID}）。 */
    @Id
    @Column(name = "config_id", length = 32, nullable = false)
    private String configId;

    /** 同一来源 IP + 同一用户名的失败阈值。 */
    @Column(name = "max_failures_per_principal", nullable = false)
    private Integer maxFailuresPerPrincipal;

    /** 同一来源 IP 跨用户名的失败阈值。 */
    @Column(name = "max_failures_per_ip", nullable = false)
    private Integer maxFailuresPerIp;

    /** 失败计数窗口（分钟）。 */
    @Column(name = "window_minutes", nullable = false)
    private Integer windowMinutes;

    /** 达到阈值后的锁定时长（分钟）。 */
    @Column(name = "lock_minutes", nullable = false)
    private Integer lockMinutes;

    /** 最后一次修改者（控制台用户名；启动期自动初始化时为 {@code system}）。 */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    /** 最后一次修改时间。 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

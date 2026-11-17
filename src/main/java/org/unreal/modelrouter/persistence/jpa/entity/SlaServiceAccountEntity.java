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
 * SLA 服务账号（issue #1174）。
 *
 * <p>外部巡检工具（{@code sla/} 工具链）的运行主体。与 {@link JwtAccountEntity} 分开存：
 * 人账号有密码、可登录控制台，SLA 账号没有密码、只能靠一次性接入令牌换取访问凭据，
 * 两者的生命周期与「能不能登录」语义完全不同。</p>
 *
 * <p>角色模板只有两个：{@code SLA_READONLY}（只读观测面）与 {@code SLA_OPERATOR}
 * （只读 + 控制面配置写）。两者都由 {@code RolePermissionSeeder} 种入 {@code role_permissions}，
 * 因此访问令牌的权限码走既有解析链路，不需要第二套判权逻辑。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sla_service_accounts")
public class SlaServiceAccountEntity {

    /** 账号主键（UUID 字符串）。 */
    @Id
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 账号名（唯一），同时也是 JWT 主体名 {@code sla:<name>} 的后半段。 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 角色模板：{@code SLA_READONLY} 或 {@code SLA_OPERATOR}。 */
    @Column(name = "role_template", nullable = false, length = 32)
    private String roleTemplate;

    /** 是否启用；禁用后该账号的刷新凭据一律不再换取访问令牌。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人（管理员用户名）。 */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后一次成功换取访问令牌的时间。 */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}

package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SLA 一次性接入令牌（issue #1174）。
 *
 * <p>管理员在控制台签发，明文只返回一次、库里只存 SHA-256 哈希。SLA 工具在部署时把它交给
 * {@code POST /api/auth/sla/bootstrap}，换回访问令牌与刷新凭据；这枚令牌随即被标记
 * {@code usedAt}，再用即拒 —— 因此「部署到哪台机器」这件事只发生一次，不会因为落盘位置
 * 泄漏而长期可复用。</p>
 *
 * <p>可选绑定来源 IP（{@code allowedIp}）与固定 15 分钟 TTL 一起构成两道收敛：即使令牌被
 * 截获，也只能在使用方发起的那段时间窗口里、从那个地址换一次凭据。</p>
 *
 * <p>管理侧「吊销」会写 {@code revokedAt} 而不是复用 {@code usedAt}：两者在审计上语义不同 ——
 * 前者是管理员主动作废（issue #1210），后者是令牌真的被换取过。分开记才能回答
 * 「这枚令牌是被用了，还是被拔掉了」。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sla_bootstrap_tokens",
       indexes = @Index(name = "idx_sla_bootstrap_tokens_account", columnList = "account_id"),
       uniqueConstraints = @UniqueConstraint(
               name = "uk_sla_bootstrap_tokens_hash", columnNames = "token_hash"))
public class SlaBootstrapTokenEntity {

    /** 令牌主键（UUID 字符串）。 */
    @Id
    @Column(name = "token_id", nullable = false, length = 64)
    private String tokenId;

    /** 归属的 SLA 服务账号。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 令牌明文与盐拼串后的 SHA-256 十六进制摘要。 */
    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    /** 过期时间（签发时 + TTL）。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 被消费的时间；非空即视为已作废。 */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * 被吊销的时间；非空即视为作废（issue #1210）。
     *
     * <p>与 {@link #usedAt} 区分：吊销是管理动作，消费是使用方动作。两者互不影响，
     * 校验时任一非空都拒绝。</p>
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** 允许使用该令牌的来源 IP（为空表示不限制）。 */
    @Column(name = "allowed_ip", length = 64)
    private String allowedIp;

    /** 签发人（管理员用户名）。 */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** 签发时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 乐观锁版本号。
     *
     * <p>一次性令牌的「只能用一次」靠这一列兜住：两个并发请求同时读到 {@code usedAt == null}，
     * 后提交的那一方会在 flush 时撞上版本冲突，从而被拒 —— 审计表里的「双花」痕迹就只有一次。</p>
     */
    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}

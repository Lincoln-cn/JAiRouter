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
 * SLA 长期刷新凭据（issue #1174）。
 *
 * <p>一次性接入令牌换来的长期凭据，只存哈希。SLA 工具用它定期（默认 45 分钟）换新的访问
 * 令牌，刷新时滚动续期并记录 {@code lastUsedAt}/{@code clientIp} —— 这两列是排查「凭据是否
 * 被搬到别的机器上跑」的唯一线索。吊销即写 {@code revokedAt}，之后任何刷新请求一律拒绝。</p>
 *
 * <p>与访问令牌的分工：访问令牌是 60 分钟的 JWT（无状态、能被既有黑名单机制即时作废），
 * 刷新凭据是库里的行（能被精确吊销、可以按账号一次清空）。两者都不含密码。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sla_refresh_tokens")
public class SlaRefreshTokenEntity {

    /** 凭据主键（UUID 字符串）。 */
    @Id
    @Column(name = "token_id", nullable = false, length = 64)
    private String tokenId;

    /** 归属的 SLA 服务账号。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 凭据明文与盐拼串后的 SHA-256 十六进制摘要。 */
    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    /** 签发时间。 */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    /** 过期时间（滚动续期时重算）。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 吊销时间；非空即视为失效。 */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** 最后一次成功刷新的时间。 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 最后一次刷新的来源 IP。 */
    @Column(name = "client_ip", length = 64)
    private String clientIp;
}

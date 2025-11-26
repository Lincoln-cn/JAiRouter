package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * JWT 黑名单实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("jwt_blacklist")
public class JwtBlacklistEntity {

    @Id
    private Long id;

    @Column("token_hash")
    private String tokenHash;

    @Column("user_id")
    private String userId;

    @Column("revoked_at")
    private LocalDateTime revokedAt;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("reason")
    private String reason;

    @Column("revoked_by")
    private String revokedBy;

    @Column("created_at")
    private LocalDateTime createdAt;
}

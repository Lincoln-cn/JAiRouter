package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JWT 账户实体 (JPA 版本)
 * v1.5.2: 从 R2DBC 迁移到 JPA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jwt_accounts")
public class JwtAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "roles")
    private String roles; // JSON 格式存储 ["ADMIN", "USER"]

    @Column(name = "enabled")
    private Boolean enabled;

    /**
     * 软删除标记（issue #789）。
     *
     * <p>控制台「删除账户」只能置位本列、不能物理删除行：默认 {@code admin} 同时存在于 YAML
     * 静态账户（{@code config/auth/jwt.yml}）与 {@code jwt_accounts} 表里，物理删除会让登录与
     * 令牌解析回落到 YAML 分支，表现为「控制台删掉了却还能登录」。保留行并置 {@code deleted=true}
     * 才能让删除持续生效，也避免同名账户重建时撞 {@code username} 唯一键。</p>
     */
    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 该行是否已被控制台软删除（issue #789）。
     *
     * @return {@code true} 表示已删除（视为账户不存在）
     */
    public boolean isDeletedAccount() {
        return Boolean.TRUE.equals(deleted);
    }
}

package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 可逆脱敏的占位符仓库（#987）。
 *
 * <p>一次请求内的每个被替换的 PII 片段在这里留一行：占位符是发给上游的
 * {@code [PII_x]} 文案，{@code ciphertext} 是对应原文经
 * {@code RecordContentCipher} 加密后的结果。回包时按 {@code scope_id} 取回整批映射，
 * 只做精确占位符替换。</p>
 *
 * <p>{@code scope_id} 由调用方生成（当前是请求级 UUID），它是隔离边界：
 * 同一次请求的占位符编号从 1 开始，不同请求之间互不可见。</p>
 *
 * <p>{@code expires_at} 用于清理：映射只在「这次请求的响应还没回来」的窗口内有用，
 * 过期后删掉即可。清理逻辑见 {@code PiiTokenVaultCleanupScheduler}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pii_token_vault",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pii_vault_scope_placeholder",
                columnNames = {"scope_id", "placeholder"}),
        indexes = @Index(name = "idx_pii_vault_expires_at", columnList = "expires_at"))
public class PiiTokenVaultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "scope_id", nullable = false, length = 64)
    private String scopeId;

    /** 发给上游的占位符，例如 {@code [PII_1]} */
    @Column(name = "placeholder", nullable = false, length = 64)
    private String placeholder;

    /** 原文的密文（AES-256-GCM，Base64） */
    @Column(name = "ciphertext", nullable = false, length = 4096)
    private String ciphertext;

    /** 命中规则的名称，用于审计与排障 */
    @Column(name = "rule_name", length = 128)
    private String ruleName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}

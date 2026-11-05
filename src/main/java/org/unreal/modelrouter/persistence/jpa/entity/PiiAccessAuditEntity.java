package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 可逆脱敏访问审计（#987）。
 *
 * <p>只记录「哪个 scope 还原了几处、由谁、走哪个路径」，不记录任何原值。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pii_access_audit",
        indexes = {
                @Index(name = "idx_pii_audit_scope", columnList = "scope_id"),
                @Index(name = "idx_pii_audit_created_at", columnList = "created_at")
        })
public class PiiAccessAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_id", nullable = false, length = 64)
    private String scopeId;

    @Column(name = "caller", length = 128)
    private String caller;

    @Column(name = "path", length = 512)
    private String path;

    @Column(name = "restored_count", nullable = false)
    private Integer restoredCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

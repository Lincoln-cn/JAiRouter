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
 * 语义缓存跨用户命中审计记录.
 *
 * <p>只记元数据：命中发生在哪个 scope、条目存在了多久、桶里有多少条候选。
 * 不记命中的正文 —— 正文可能含敏感内容，审计只需要能回答
 * 「这个 scope 有没有被非本租户的请求读到」。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cache_cross_tenant_audit", indexes = {
        @Index(name = "idx_cache_audit_scope", columnList = "scope_id"),
        @Index(name = "idx_cache_audit_created_at", columnList = "created_at")
})
public class CacheCrossTenantAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 命中的 scope（{@code p|模型} 或 {@code t|租户|user|模型}）. */
    @Column(name = "scope_id", nullable = false, length = 255)
    private String scopeId;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    /** 命中条目从写入到被读到的毫秒数. */
    @Column(name = "entry_age_ms", nullable = false)
    private Long entryAgeMs;

    /** 命中时同 scope 下的候选条数. */
    @Column(name = "bucket_size", nullable = false)
    private Integer bucketSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

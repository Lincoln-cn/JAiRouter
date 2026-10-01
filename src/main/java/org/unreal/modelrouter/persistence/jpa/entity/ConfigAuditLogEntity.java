package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.Instant;

/**
 * Configuration audit log entity for tracking config changes.
 *
 * @since v2.6.12
 */
@Entity
@Table(name = "config_audit_log", indexes = {
    @Index(name = "idx_audit_service_type", columnList = "serviceType"),
    // 索引名不能与其他表重复：PostgreSQL 的 relation 名（含索引）是 schema 级命名空间，
    // 而 H2/MySQL 是表级。security_audit 表已占用 idx_audit_user_id / idx_audit_timestamp，
    // 重名在 PG 上会导致 CREATE INDEX 失败且 Hibernate 只记 WARN——索引被静默丢失。
    @Index(name = "idx_config_audit_user_id", columnList = "userId"),
    @Index(name = "idx_config_audit_timestamp", columnList = "timestamp")
})
@Data
public class ConfigAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String changeType;

    @Column(length = 100)
    private String serviceType;

    @Column(length = 100)
    private String userId;

    @Column(columnDefinition = "TEXT")
    private String oldConfig;

    @Column(columnDefinition = "TEXT")
    private String newConfig;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 50)
    private String entityType;

    @Column(length = 100)
    private String entityId;

    @Column(length = 500)
    private String description;
}

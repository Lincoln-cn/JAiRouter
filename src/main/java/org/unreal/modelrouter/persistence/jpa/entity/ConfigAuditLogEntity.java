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
    @Index(name = "idx_audit_user_id", columnList = "userId"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
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

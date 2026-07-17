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
 * Configuration version history entity for version tracking.
 *
 * @since v2.6.12
 */
@Entity
@Table(name = "config_version_history", indexes = {
    @Index(name = "idx_version_service_type", columnList = "serviceType"),
    @Index(name = "idx_version_number", columnList = "versionNumber"),
    @Index(name = "idx_version_timestamp", columnList = "timestamp")
})
@Data
public class ConfigVersionHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String versionNumber;

    @Column(length = 100)
    private String serviceType;

    @Column(length = 100)
    private String userId;

    @Column(columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 20)
    private String status;
}

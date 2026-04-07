package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * API Key 实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id")
    private String keyId;

    @Column(name = "key_value")
    private String keyValue;

    @Column(name = "description")
    private String description;

    @Column(name = "permissions")
    private String permissions; // JSON 格式存储

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "metadata")
    private String metadata; // JSON 格式存储

    @Column(name = "usage_statistics")
    private String usageStatistics; // JSON 格式存储
}

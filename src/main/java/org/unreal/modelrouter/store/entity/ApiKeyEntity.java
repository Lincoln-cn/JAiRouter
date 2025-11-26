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
 * API Key 实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("api_keys")
public class ApiKeyEntity {

    @Id
    private Long id;

    @Column("key_id")
    private String keyId;

    @Column("key_value")
    private String keyValue;

    @Column("description")
    private String description;

    @Column("permissions")
    private String permissions; // JSON 格式存储

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("enabled")
    private Boolean enabled;

    @Column("metadata")
    private String metadata; // JSON 格式存储

    @Column("usage_statistics")
    private String usageStatistics; // JSON 格式存储
}

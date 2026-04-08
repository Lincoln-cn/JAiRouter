package org.unreal.modelrouter.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 服务实例实体 (JPA 版本)
 * v1.5.2: 从 R2DBC 迁移到 JPA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "service_instance")
public class ServiceInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_config_id", nullable = false)
    private Long serviceConfigId;

    @Column(name = "instance_name", nullable = false)
    private String instanceName;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "path")
    private String path;

    @Column(name = "weight")
    private Integer weight;

    @Column(name = "status")
    private String status; // ACTIVE, INACTIVE, ERROR

    @Column(name = "health_status")
    private String healthStatus; // HEALTHY, UNHEALTHY, UNKNOWN

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

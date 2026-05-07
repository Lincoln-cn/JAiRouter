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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 服务实例实体 (JPA 版本)
 * v1.5.2: 从 R2DBC 迁移到 JPA
 * v1.7.1: 添加 adapter 和 headers 字段
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

    @Column(name = "instance_id")
    private String instanceId; // UUID 格式的唯一标识符

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

    /**
     * 实例级别适配器配置（可选，覆盖服务级别配置）
     */
    @Column(name = "adapter")
    private String adapter;

    /**
     * 自定义请求头配置（JSON 格式存储）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "JSON")
    private Map<String, String> headers;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

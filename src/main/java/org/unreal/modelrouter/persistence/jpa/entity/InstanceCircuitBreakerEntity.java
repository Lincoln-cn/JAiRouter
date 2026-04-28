package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 实例熔断器配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "instance_circuit_breaker")
public class InstanceCircuitBreakerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, unique = true)
    private Long instanceId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "failure_threshold")
    private Integer failureThreshold = 5;

    @Column(name = "timeout_ms")
    private Integer timeoutMs = 60000;

    @Column(name = "success_threshold")
    private Integer successThreshold = 2;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 实例限流配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "instance_rate_limit")
public class InstanceRateLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, unique = true)
    private Long instanceId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "algorithm")
    private String algorithm = "token-bucket";

    @Column(name = "capacity")
    private Integer capacity = 100;

    @Column(name = "rate")
    private Integer rate = 10;

    @Column(name = "scope")
    private String scope = "instance";

    @Column(name = "rate_limit_key")
    private String rateLimitKey;

    @Column(name = "client_ip_enable")
    private Boolean clientIpEnable = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
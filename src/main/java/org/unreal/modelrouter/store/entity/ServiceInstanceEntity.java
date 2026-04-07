package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import jakarta.persistence.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 服务实例表实体 - 存储每个服务的具体实例信息
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
    @Column(name = "id")
    private Long id;

    @Column(name = "service_config_id")
    private Long serviceConfigId;

    @Column(name = "instance_name")
    private String instanceName;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "path")
    private String path;

    @Column(name = "weight")
    private Integer weight;

    @Column(name = "headers")
    private String headers; // JSON 格式的 headers

    @Column(name = "rate_limit_enabled")
    private Boolean rateLimitEnabled;

    @Column(name = "rate_limit_algorithm")
    private String rateLimitAlgorithm;

    @Column(name = "rate_limit_capacity")
    private Integer rateLimitCapacity;

    @Column(name = "rate_limit_rate")
    private Integer rateLimitRate;

    @Column(name = "rate_limit_scope")
    private String rateLimitScope;

    @Column(name = "rate_limit_key")
    private String rateLimitKey;

    @Column(name = "rate_limit_client_ip_enable")
    private Boolean rateLimitClientIpEnable;

    @Column(name = "circuit_breaker_enabled")
    private Boolean circuitBreakerEnabled;

    @Column(name = "circuit_breaker_failure_threshold")
    private Integer circuitBreakerFailureThreshold;

    @Column(name = "circuit_breaker_timeout")
    private Integer circuitBreakerTimeout;

    @Column(name = "circuit_breaker_success_threshold")
    private Integer circuitBreakerSuccessThreshold;

    @Column(name = "status")
    private String status; // ACTIVE, INACTIVE, ERROR

    @Column(name = "last_health_check")
    private LocalDateTime lastHealthCheck;

    @Column(name = "health_status")
    private String healthStatus; // HEALTHY, UNHEALTHY, UNKNOWN

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

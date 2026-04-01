package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 服务实例表实体 - 存储每个服务的具体实例信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("service_instance")
public class ServiceInstanceEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("service_config_id")
    private Long serviceConfigId;

    @Column("instance_name")
    private String instanceName;

    @Column("base_url")
    private String baseUrl;

    @Column("path")
    private String path;

    @Column("weight")
    private Integer weight;

    @Column("headers")
    private String headers; // JSON 格式的 headers

    @Column("rate_limit_enabled")
    private Boolean rateLimitEnabled;

    @Column("rate_limit_algorithm")
    private String rateLimitAlgorithm;

    @Column("rate_limit_capacity")
    private Integer rateLimitCapacity;

    @Column("rate_limit_rate")
    private Integer rateLimitRate;

    @Column("rate_limit_scope")
    private String rateLimitScope;

    @Column("rate_limit_key")
    private String rateLimitKey;

    @Column("rate_limit_client_ip_enable")
    private Boolean rateLimitClientIpEnable;

    @Column("circuit_breaker_enabled")
    private Boolean circuitBreakerEnabled;

    @Column("circuit_breaker_failure_threshold")
    private Integer circuitBreakerFailureThreshold;

    @Column("circuit_breaker_timeout")
    private Integer circuitBreakerTimeout;

    @Column("circuit_breaker_success_threshold")
    private Integer circuitBreakerSuccessThreshold;

    @Column("status")
    private String status; // ACTIVE, INACTIVE, ERROR

    @Column("last_health_check")
    private LocalDateTime lastHealthCheck;

    @Column("health_status")
    private String healthStatus; // HEALTHY, UNHEALTHY, UNKNOWN

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column("updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

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
 * 服务配置表实体 - 存储每个服务的配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "service_config")
public class ServiceConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key")
    private String configKey;

    @Column(name = "service_type")
    private String serviceType; // chat, embedding, rerank, tts, stt, imgGen, imgEdit

    @Column(name = "load_balance_type")
    private String loadBalanceType; // random, round-robin, least-connections, ip-hash

    @Column(name = "load_balance_hash_algorithm")
    private String loadBalanceHashAlgorithm;

    @Column(name = "adapter")
    private String adapter;

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

    @Column(name = "fallback_enabled")
    private Boolean fallbackEnabled;

    @Column(name = "fallback_strategy")
    private String fallbackStrategy;

    @Column(name = "fallback_cache_size")
    private Integer fallbackCacheSize;

    @Column(name = "fallback_cache_ttl")
    private Integer fallbackCacheTtl;

    @Column(name = "version")
    private Integer version;

    @Column(name = "is_latest")
    private Boolean isLatest;

    @Column(name = "created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

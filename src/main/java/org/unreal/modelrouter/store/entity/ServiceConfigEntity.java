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
 * 服务配置表实体 - 存储每个服务的配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("service_config")
public class ServiceConfigEntity {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("service_type")
    private String serviceType; // chat, embedding, rerank, tts, stt, imgGen, imgEdit

    @Column("load_balance_type")
    private String loadBalanceType; // random, round-robin, least-connections, ip-hash

    @Column("load_balance_hash_algorithm")
    private String loadBalanceHashAlgorithm;

    @Column("adapter")
    private String adapter;

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

    @Column("fallback_enabled")
    private Boolean fallbackEnabled;

    @Column("fallback_strategy")
    private String fallbackStrategy;

    @Column("fallback_cache_size")
    private Integer fallbackCacheSize;

    @Column("fallback_cache_ttl")
    private Integer fallbackCacheTtl;

    @Column("version")
    private Integer version;

    @Column("is_latest")
    private Boolean isLatest;

    @Column("created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column("updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

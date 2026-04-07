package org.unreal.modelrouter.store.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ServiceConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 服务配置表数据仓库
 */
@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfigEntity, Long> {

    /**
     * 根据配置键和服务类型查找最新版本
     */
    @Query("SELECT * FROM service_config WHERE config_key = :configKey AND service_type = :serviceType AND is_latest = true")
    Mono<ServiceConfigEntity> findLatestByConfigKeyAndServiceType(@Param("configKey") String configKey, 
                                                                   @Param("serviceType") String serviceType);

    /**
     * 根据配置键查找所有服务的最新版本
     */
    @Query("SELECT * FROM service_config WHERE config_key = :configKey AND is_latest = true")
    Flux<ServiceConfigEntity> findAllLatestByConfigKey(@Param("configKey") String configKey);

    /**
     * 根据配置键和服务类型查找所有版本
     */
    @Query("SELECT * FROM service_config WHERE config_key = :configKey AND service_type = :serviceType ORDER BY version DESC")
    Flux<ServiceConfigEntity> findAllByConfigKeyAndServiceType(@Param("configKey") String configKey, 
                                                                @Param("serviceType") String serviceType);

    /**
     * 检查服务配置是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM service_config WHERE config_key = :configKey AND service_type = :serviceType AND is_latest = true")
    Mono<Boolean> existsByConfigKeyAndServiceType(@Param("configKey") String configKey, 
                                                   @Param("serviceType") String serviceType);

    /**
     * 将指定配置键的所有服务配置标记为非最新
     */
    @Modifying
    @Query("UPDATE service_config SET is_latest = false WHERE config_key = :configKey")
    Mono<Integer> markAllAsNotLatest(@Param("configKey") String configKey);

    /**
     * 将指定服务配置标记为非最新
     */
    @Modifying
    @Query("UPDATE service_config SET is_latest = false WHERE config_key = :configKey AND service_type = :serviceType")
    Mono<Integer> markServiceAsNotLatest(@Param("configKey") String configKey, 
                                          @Param("serviceType") String serviceType);

    /**
     * 删除指定服务配置的所有版本
     */
    @Modifying
    @Query("DELETE FROM service_config WHERE config_key = :configKey AND service_type = :serviceType")
    Mono<Integer> deleteByConfigKeyAndServiceType(@Param("configKey") String configKey, 
                                                   @Param("serviceType") String serviceType);

    /**
     * 根据配置键删除所有服务配置
     */
    @Modifying
    @Query("DELETE FROM service_config WHERE config_key = :configKey")
    Mono<Integer> deleteByConfigKey(@Param("configKey") String configKey);

    /**
     * 更新服务配置
     */
    @Modifying
    @Query("""
        UPDATE service_config SET 
            load_balance_type = :#{#entity.loadBalanceType},
            load_balance_hash_algorithm = :#{#entity.loadBalanceHashAlgorithm},
            adapter = :#{#entity.adapter},
            rate_limit_enabled = :#{#entity.rateLimitEnabled},
            rate_limit_algorithm = :#{#entity.rateLimitAlgorithm},
            rate_limit_capacity = :#{#entity.rateLimitCapacity},
            rate_limit_rate = :#{#entity.rateLimitRate},
            rate_limit_scope = :#{#entity.rateLimitScope},
            rate_limit_client_ip_enable = :#{#entity.rateLimitClientIpEnable},
            circuit_breaker_enabled = :#{#entity.circuitBreakerEnabled},
            circuit_breaker_failure_threshold = :#{#entity.circuitBreakerFailureThreshold},
            circuit_breaker_timeout = :#{#entity.circuitBreakerTimeout},
            circuit_breaker_success_threshold = :#{#entity.circuitBreakerSuccessThreshold},
            fallback_enabled = :#{#entity.fallbackEnabled},
            fallback_strategy = :#{#entity.fallbackStrategy},
            fallback_cache_size = :#{#entity.fallbackCacheSize},
            fallback_cache_ttl = :#{#entity.fallbackCacheTtl},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :#{#entity.id}
        """)
    Mono<Integer> updateServiceConfig(@Param("entity") ServiceConfigEntity entity);

    /**
     * 获取所有服务类型
     */
    @Query("SELECT DISTINCT service_type FROM service_config WHERE config_key = :configKey AND is_latest = true")
    Flux<String> findAllServiceTypes(@Param("configKey") String configKey);
}

package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ServiceInstanceEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 服务实例表数据仓库
 */
@Repository
public interface ServiceInstanceRepository extends R2dbcRepository<ServiceInstanceEntity, Long> {

    /**
     * 根据服务配置 ID 查找所有实例
     */
    @Query("SELECT * FROM service_instance WHERE service_config_id = :serviceConfigId ORDER BY id ASC")
    Flux<ServiceInstanceEntity> findAllByServiceConfigId(@Param("serviceConfigId") Long serviceConfigId);

    /**
     * 根据服务配置 ID 查找所有活跃实例
     */
    @Query("SELECT * FROM service_instance WHERE service_config_id = :serviceConfigId AND status = 'ACTIVE' ORDER BY id ASC")
    Flux<ServiceInstanceEntity> findActiveInstancesByServiceConfigId(@Param("serviceConfigId") Long serviceConfigId);

    /**
     * 根据服务配置 ID 查找所有健康实例
     */
    @Query("SELECT * FROM service_instance WHERE service_config_id = :serviceConfigId AND health_status = 'HEALTHY' ORDER BY id ASC")
    Flux<ServiceInstanceEntity> findHealthyInstancesByServiceConfigId(@Param("serviceConfigId") Long serviceConfigId);

    /**
     * 根据实例名称查找
     */
    @Query("SELECT * FROM service_instance WHERE service_config_id = :serviceConfigId AND instance_name = :instanceName")
    Mono<ServiceInstanceEntity> findByServiceConfigIdAndInstanceName(@Param("serviceConfigId") Long serviceConfigId, 
                                                                       @Param("instanceName") String instanceName);

    /**
     * 更新实例健康状态
     */
    @Modifying
    @Query("UPDATE service_instance SET health_status = :healthStatus, last_health_check = CURRENT_TIMESTAMP, error_message = :errorMessage WHERE id = :id")
    Mono<Integer> updateHealthStatus(@Param("id") Long id, 
                                      @Param("healthStatus") String healthStatus, 
                                      @Param("errorMessage") String errorMessage);

    /**
     * 更新实例状态
     */
    @Modifying
    @Query("UPDATE service_instance SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    Mono<Integer> updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 根据服务配置 ID 删除所有实例
     */
    @Modifying
    @Query("DELETE FROM service_instance WHERE service_config_id = :serviceConfigId")
    Mono<Integer> deleteAllByServiceConfigId(@Param("serviceConfigId") Long serviceConfigId);

    /**
     * 删除指定实例
     */
    @Modifying
    @Query("DELETE FROM service_instance WHERE id = :id")
    Mono<Integer> deleteInstanceById(@Param("id") Long id);

    /**
     * 统计服务配置下的实例数量
     */
    @Query("SELECT COUNT(*) FROM service_instance WHERE service_config_id = :serviceConfigId")
    Mono<Long> countByServiceConfigId(@Param("serviceConfigId") Long serviceConfigId);

    /**
     * 统计健康实例数量
     */
    @Query("SELECT COUNT(*) FROM service_instance WHERE service_config_id = :serviceConfigId AND health_status = 'HEALTHY'")
    Mono<Long> countHealthyInstancesByServiceConfigId(@Param("serviceConfigId") Long serviceConfigId);

    /**
     * 更新实例状态和基本信息
     */
    @Modifying
    @Query("""
        UPDATE SERVICE_INSTANCE SET 
            "STATUS" = :status,
            "INSTANCE_NAME" = :instanceName,
            "BASE_URL" = :baseUrl,
            "PATH" = :path,
            "WEIGHT" = :weight,
            "UPDATED_AT" = CURRENT_TIMESTAMP
        WHERE "ID" = :id
        """)
    Mono<Integer> updateInstanceStatus(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("instanceName") String instanceName,
        @Param("baseUrl") String baseUrl,
        @Param("path") String path,
        @Param("weight") Integer weight
    );
}

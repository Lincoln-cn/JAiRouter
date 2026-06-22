package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;

import java.util.List;
import java.util.Optional;

/**
 * 服务实例仓库 (JPA 版本)
 * v1.5.2: 从 R2DBC 迁移到 JPA
 */
@Repository
public interface ServiceInstanceRepository extends JpaRepository<ServiceInstanceEntity, Long> {

    /**
     * 根据服务配置ID查找实例
     */
    List<ServiceInstanceEntity> findByServiceConfigId(Long serviceConfigId);

    /**
     * 根据状态查找实例
     */
    List<ServiceInstanceEntity> findByStatus(String status);

    /**
     * 根据实例名称查找
     */
    Optional<ServiceInstanceEntity> findByInstanceName(String instanceName);

    /**
     * 根据实例名称查找所有匹配实例
     * v2.x: 用于负载均衡场景下同名实例的健康状态更新
     */
    List<ServiceInstanceEntity> findAllByInstanceName(String instanceName);

    /**
     * 根据实例ID（UUID）查找
     * v2.x: 用于健康状态更新，避免同名实例导致的重复记录错误
     */
    Optional<ServiceInstanceEntity> findByInstanceId(String instanceId);
}

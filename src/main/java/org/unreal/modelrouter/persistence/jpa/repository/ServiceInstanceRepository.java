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
}

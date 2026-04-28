package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.InstanceCircuitBreakerEntity;

import java.util.Optional;

/**
 * 实例熔断器配置仓库
 */
@Repository
public interface InstanceCircuitBreakerRepository extends JpaRepository<InstanceCircuitBreakerEntity, Long> {

    /**
     * 根据实例ID查找熔断器配置
     */
    Optional<InstanceCircuitBreakerEntity> findByInstanceId(Long instanceId);

    /**
     * 根据实例ID删除熔断器配置
     */
    void deleteByInstanceId(Long instanceId);

    /**
     * 检查实例是否存在熔断器配置
     */
    boolean existsByInstanceId(Long instanceId);
}
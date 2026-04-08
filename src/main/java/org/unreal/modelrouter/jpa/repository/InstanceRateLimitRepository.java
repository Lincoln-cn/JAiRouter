package org.unreal.modelrouter.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.jpa.entity.InstanceRateLimitEntity;

import java.util.Optional;

/**
 * 实例限流配置仓库
 */
@Repository
public interface InstanceRateLimitRepository extends JpaRepository<InstanceRateLimitEntity, Long> {

    /**
     * 根据实例ID查找限流配置
     */
    Optional<InstanceRateLimitEntity> findByInstanceId(Long instanceId);

    /**
     * 根据实例ID删除限流配置
     */
    void deleteByInstanceId(Long instanceId);

    /**
     * 检查实例是否存在限流配置
     */
    boolean existsByInstanceId(Long instanceId);
}
package org.unreal.modelrouter.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;

import java.util.List;
import java.util.Optional;

/**
 * 服务配置仓库 (JPA 版本)
 * v1.5.2: 从 R2DBC 迁移到 JPA
 */
@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfigEntity, Long> {

    /**
     * 根据服务类型查找最新版本
     */
    Optional<ServiceConfigEntity> findFirstByServiceTypeAndIsLatestTrue(String serviceType);

    /**
     * 查找所有最新版本的服务配置
     */
    List<ServiceConfigEntity> findAllByIsLatestTrue();

    /**
     * 根据配置键查找
     */
    List<ServiceConfigEntity> findByConfigKey(String configKey);

    /**
     * 根据服务类型删除所有配置
     */
    @Modifying
    @Query("DELETE FROM ServiceConfigEntity c WHERE c.serviceType = :serviceType")
    void deleteAllByServiceType(@Param("serviceType") String serviceType);
}

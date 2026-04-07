package org.unreal.modelrouter.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.jpa.entity.ConfigEntity;

import java.util.List;
import java.util.Optional;

/**
 * 配置数据仓库 (JPA 版本)
 * v1.5.1: 替代 R2DBC 的 ConfigRepository
 */
@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntity, Long> {

    /**
     * 根据配置键查找最新版本
     */
    Optional<ConfigEntity> findFirstByConfigKeyAndIsLatestTrue(String configKey);

    /**
     * 根据配置键和版本号查找
     */
    Optional<ConfigEntity> findByConfigKeyAndVersion(String configKey, Integer version);

    /**
     * 查找配置键的所有版本号
     */
    @Query("SELECT c.version FROM ConfigEntity c WHERE c.configKey = :configKey ORDER BY c.version ASC")
    List<Integer> findAllVersionsByConfigKey(@Param("configKey") String configKey);

    /**
     * 查找配置键的所有版本实体（按版本号升序）
     */
    List<ConfigEntity> findAllByConfigKeyOrderByVersionAsc(String configKey);

    /**
     * 查找所有最新版本的配置键
     */
    @Query("SELECT DISTINCT c.configKey FROM ConfigEntity c WHERE c.isLatest = true")
    List<String> findAllLatestConfigKeys();

    /**
     * 删除指定配置键的所有版本
     */
    @Modifying
    @Query("DELETE FROM ConfigEntity c WHERE c.configKey = :configKey")
    void deleteAllByConfigKey(@Param("configKey") String configKey);

    /**
     * 删除指定配置键的指定版本
     */
    @Modifying
    @Query("DELETE FROM ConfigEntity c WHERE c.configKey = :configKey AND c.version = :version")
    void deleteByConfigKeyAndVersion(@Param("configKey") String configKey, @Param("version") Integer version);

    /**
     * 将指定配置键的所有版本标记为非最新
     */
    @Modifying
    @Query("UPDATE ConfigEntity c SET c.isLatest = false WHERE c.configKey = :configKey")
    void markAllAsNotLatest(@Param("configKey") String configKey);

    /**
     * 检查配置键是否存在
     */
    boolean existsByConfigKeyAndIsLatestTrue(String configKey);

    /**
     * 检查指定版本是否存在
     */
    boolean existsByConfigKeyAndVersion(String configKey, Integer version);
}

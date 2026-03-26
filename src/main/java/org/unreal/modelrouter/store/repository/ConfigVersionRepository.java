package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ConfigVersionEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置版本表数据仓库
 */
@Repository
public interface ConfigVersionRepository extends R2dbcRepository<ConfigVersionEntity, Long> {

    /**
     * 根据配置键和版本号查找
     */
    @Query("SELECT * FROM config_version WHERE config_key = :configKey AND version = :version")
    Mono<ConfigVersionEntity> findByConfigKeyAndVersion(@Param("configKey") String configKey, 
                                                         @Param("version") Integer version);

    /**
     * 查找配置键的当前版本
     */
    @Query("SELECT * FROM config_version WHERE config_key = :configKey AND is_current = true")
    Mono<ConfigVersionEntity> findCurrentVersion(@Param("configKey") String configKey);

    /**
     * 查找配置键的所有版本（按版本号升序）
     */
    @Query("SELECT * FROM config_version WHERE config_key = :configKey ORDER BY version ASC")
    Flux<ConfigVersionEntity> findAllVersions(@Param("configKey") String configKey);

    /**
     * 查找配置键的所有版本号
     */
    @Query("SELECT version FROM config_version WHERE config_key = :configKey ORDER BY version ASC")
    Flux<Integer> findAllVersionNumbers(@Param("configKey") String configKey);

    /**
     * 查找未归档的版本
     */
    @Query("SELECT * FROM config_version WHERE config_key = :configKey AND is_archived = false ORDER BY version ASC")
    Flux<ConfigVersionEntity> findNonArchivedVersions(@Param("configKey") String configKey);

    /**
     * 查找指定时间范围之前的版本（用于归档）
     */
    @Query("SELECT * FROM config_version WHERE config_key = :configKey AND created_at < :cutoffDate AND is_archived = false ORDER BY version ASC")
    Flux<ConfigVersionEntity> findVersionsForArchiving(@Param("configKey") String configKey, 
                                                        @Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * 检查版本是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM config_version WHERE config_key = :configKey AND version = :version")
    Mono<Boolean> existsByVersion(@Param("configKey") String configKey, @Param("version") Integer version);

    /**
     * 获取最大版本号
     */
    @Query("SELECT COALESCE(MAX(version), 0) FROM config_version WHERE config_key = :configKey")
    Mono<Integer> findMaxVersion(@Param("configKey") String configKey);

    /**
     * 将指定版本标记为当前版本
     */
    @Modifying
    @Query("UPDATE config_version SET is_current = true WHERE config_key = :configKey AND version = :version")
    Mono<Integer> markAsCurrent(@Param("configKey") String configKey, @Param("version") Integer version);

    /**
     * 将所有版本标记为非当前版本
     */
    @Modifying
    @Query("UPDATE config_version SET is_current = false WHERE config_key = :configKey")
    Mono<Integer> markAllAsNotCurrent(@Param("configKey") String configKey);

    /**
     * 标记版本为已归档
     */
    @Modifying
    @Query("UPDATE config_version SET is_archived = true, archive_path = :archivePath WHERE id = :id")
    Mono<Integer> markAsArchived(@Param("id") Long id, @Param("archivePath") String archivePath);

    /**
     * 删除指定版本
     */
    @Modifying
    @Query("DELETE FROM config_version WHERE config_key = :configKey AND version = :version")
    Mono<Integer> deleteByVersion(@Param("configKey") String configKey, @Param("version") Integer version);

    /**
     * 删除指定时间范围之前的已归档版本
     */
    @Modifying
    @Query("DELETE FROM config_version WHERE config_key = :configKey AND is_archived = true AND created_at < :cutoffDate")
    Mono<Integer> deleteOldArchivedVersions(@Param("configKey") String configKey, 
                                             @Param("cutoffDate") java.time.LocalDateTime cutoffDate);
}

package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ConfigVersionEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 配置版本表数据仓库
 * 使用 R2DBC 进行响应式数据库访问
 * 注意：R2DBC H2 使用位置参数 $1, $2 而不是命名参数
 */
@Repository
public interface ConfigVersionRepository extends R2dbcRepository<ConfigVersionEntity, Long> {

    /**
     * 根据配置键和版本号查找
     */
    @Query("SELECT * FROM config_version WHERE config_key = $1 AND version = $2")
    Mono<ConfigVersionEntity> findByConfigKeyAndVersion(String configKey, Integer version);

    /**
     * 查找配置键的当前版本
     */
    @Query("SELECT * FROM config_version WHERE config_key = $1 AND is_current = true")
    Mono<ConfigVersionEntity> findCurrentVersion(String configKey);

    /**
     * 查找配置键的所有版本（按版本号升序）
     */
    @Query("SELECT * FROM config_version WHERE config_key = $1 ORDER BY version ASC")
    Flux<ConfigVersionEntity> findAllVersions(String configKey);

    /**
     * 查找配置键的所有版本号
     */
    @Query("SELECT version FROM config_version WHERE config_key = $1 ORDER BY version ASC")
    Flux<Integer> findAllVersionNumbers(String configKey);

    /**
     * 查找未归档的版本
     */
    @Query("SELECT * FROM config_version WHERE config_key = $1 AND is_archived = false ORDER BY version ASC")
    Flux<ConfigVersionEntity> findNonArchivedVersions(String configKey);

    /**
     * 查找指定时间范围之前的版本（用于归档）
     */
    @Query("SELECT * FROM config_version WHERE config_key = $1 AND created_at < $2 AND is_archived = false ORDER BY version ASC")
    Flux<ConfigVersionEntity> findVersionsForArchiving(String configKey, LocalDateTime cutoffDate);

    /**
     * 检查版本是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM config_version WHERE config_key = $1 AND version = $2")
    Mono<Boolean> existsByVersion(String configKey, Integer version);

    /**
     * 获取最大版本号
     */
    @Query("SELECT COALESCE(MAX(version), 0) FROM config_version WHERE config_key = $1")
    Mono<Integer> findMaxVersion(String configKey);

    /**
     * 将指定版本标记为当前版本
     */
    @Modifying
    @Query("UPDATE config_version SET is_current = true WHERE config_key = $1 AND version = $2")
    Mono<Integer> markAsCurrent(String configKey, Integer version);

    /**
     * 将所有版本标记为非当前版本
     */
    @Modifying
    @Query("UPDATE config_version SET is_current = false WHERE config_key = $1")
    Mono<Integer> markAllAsNotCurrent(String configKey);

    /**
     * 标记版本为已归档
     */
    @Modifying
    @Query("UPDATE config_version SET is_archived = true, archive_path = $2 WHERE id = $1")
    Mono<Integer> markAsArchived(Long id, String archivePath);

    /**
     * 删除指定版本
     */
    @Modifying
    @Query("DELETE FROM config_version WHERE config_key = $1 AND version = $2")
    Mono<Integer> deleteByVersion(String configKey, Integer version);

    /**
     * 删除指定时间范围之前的已归档版本
     */
    @Modifying
    @Query("DELETE FROM config_version WHERE config_key = $1 AND is_archived = true AND created_at < $2")
    Mono<Integer> deleteOldArchivedVersions(String configKey, LocalDateTime cutoffDate);
}

package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ConfigArchiveEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置归档表数据仓库
 */
@Repository
public interface ConfigArchiveRepository extends R2dbcRepository<ConfigArchiveEntity, Long> {

    /**
     * 根据配置键查找所有归档
     */
    @Query("SELECT * FROM config_archive WHERE config_key = :configKey ORDER BY archived_at DESC")
    Flux<ConfigArchiveEntity> findAllByConfigKey(@Param("configKey") String configKey);

    /**
     * 查找活跃归档
     */
    @Query("SELECT * FROM config_archive WHERE config_key = :configKey AND status = 'ACTIVE' ORDER BY archived_at DESC")
    Flux<ConfigArchiveEntity> findActiveArchives(@Param("configKey") String configKey);

    /**
     * 查找即将过期的归档
     */
    @Query("SELECT * FROM config_archive WHERE config_key = :configKey AND status = 'ACTIVE' AND expiry_date < :cutoffDate ORDER BY expiry_date ASC")
    Flux<ConfigArchiveEntity> findExpiringArchives(@Param("configKey") String configKey, 
                                                    @Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * 查找已过期的归档
     */
    @Query("SELECT * FROM config_archive WHERE status = 'EXPIRED' ORDER BY expiry_date ASC")
    Flux<ConfigArchiveEntity> findExpiredArchives();

    /**
     * 根据归档路径查找
     */
    @Query("SELECT * FROM config_archive WHERE archive_path = :archivePath")
    Mono<ConfigArchiveEntity> findByArchivePath(@Param("archivePath") String archivePath);

    /**
     * 更新归档状态
     */
    @Modifying
    @Query("UPDATE config_archive SET status = :status WHERE id = :id")
    Mono<Integer> updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 标记归档为已过期
     */
    @Modifying
    @Query("UPDATE config_archive SET status = 'EXPIRED' WHERE expiry_date < :cutoffDate AND status = 'ACTIVE'")
    Mono<Integer> markExpiredArchives(@Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * 删除指定归档
     */
    @Modifying
    @Query("DELETE FROM config_archive WHERE id = :id")
    Mono<Integer> deleteArchiveById(@Param("id") Long id);

    /**
     * 删除已过期的归档
     */
    @Modifying
    @Query("DELETE FROM config_archive WHERE status = 'EXPIRED'")
    Mono<Integer> deleteExpiredArchives();

    /**
     * 统计归档文件总大小
     */
    @Query("SELECT COALESCE(SUM(file_size_bytes), 0) FROM config_archive WHERE config_key = :configKey AND status = 'ACTIVE'")
    Mono<Long> getTotalArchiveSize(@Param("configKey") String configKey);
}

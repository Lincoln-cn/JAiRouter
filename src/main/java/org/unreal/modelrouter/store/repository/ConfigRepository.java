package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置数据仓库
 */
@Repository
public interface ConfigRepository extends R2dbcRepository<ConfigEntity, Long> {

    /**
     * 根据配置键查找最新版本
     */
    @Query("SELECT * FROM config_data WHERE config_key = :configKey AND is_latest = true")
    Mono<ConfigEntity> findLatestByConfigKey(@Param("configKey") String configKey);

    /**
     * 根据配置键和版本号查找
     */
    @Query("SELECT * FROM config_data WHERE config_key = :configKey AND version = :version")
    Mono<ConfigEntity> findByConfigKeyAndVersion(@Param("configKey") String configKey, @Param("version") Integer version);

    /**
     * 查找配置键的所有版本
     */
    @Query("SELECT * FROM config_data WHERE config_key = :configKey ORDER BY version ASC")
    Flux<ConfigEntity> findAllByConfigKey(@Param("configKey") String configKey);

    /**
     * 查找所有最新版本的配置键
     */
    @Query("SELECT DISTINCT config_key FROM config_data WHERE is_latest = true")
    Flux<String> findAllLatestConfigKeys();

    /**
     * 删除指定配置键的所有版本
     */
    @Query("DELETE FROM config_data WHERE config_key = :configKey")
    Mono<Void> deleteAllByConfigKey(@Param("configKey") String configKey);

    /**
     * 删除指定配置键的指定版本
     */
    @Query("DELETE FROM config_data WHERE config_key = :configKey AND version = :version")
    Mono<Void> deleteByConfigKeyAndVersion(@Param("configKey") String configKey, @Param("version") Integer version);

    /**
     * 将指定配置键的所有版本标记为非最新
     */
    @Query("UPDATE config_data SET is_latest = false WHERE config_key = :configKey")
    Mono<Void> markAllAsNotLatest(@Param("configKey") String configKey);

    /**
     * 检查配置键是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM config_data WHERE config_key = :configKey AND is_latest = true")
    Mono<Boolean> existsByConfigKey(@Param("configKey") String configKey);

    /**
     * 检查指定版本是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM config_data WHERE config_key = :configKey AND version = :version")
    Mono<Boolean> existsByConfigKeyAndVersion(@Param("configKey") String configKey, @Param("version") Integer version);
}

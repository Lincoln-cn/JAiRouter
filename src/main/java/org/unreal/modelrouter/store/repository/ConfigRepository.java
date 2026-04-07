package org.unreal.modelrouter.store.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置数据仓库
 * 使用 R2DBC 进行响应式数据库访问
 * 注意：R2DBC H2 使用位置参数 $1, $2 而不是命名参数
 */
@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntity, Long> {

    /**
     * 根据配置键查找最新版本
     */
    @Query("SELECT * FROM config_data WHERE config_key = $1 AND is_latest = true LIMIT 1")
    Mono<ConfigEntity> findLatestByConfigKey(String configKey);

    /**
     * 根据配置键和版本号查找
     */
    @Query("SELECT * FROM config_data WHERE config_key = $1 AND version = $2 LIMIT 1")
    Mono<ConfigEntity> findByConfigKeyAndVersion(String configKey, Integer version);

    /**
     * 查找配置键的所有版本号
     */
    @Query("SELECT version FROM config_data WHERE config_key = $1 ORDER BY version ASC")
    Flux<Integer> findAllVersionsByConfigKey(String configKey);

    /**
     * 测试方法 - 直接使用字符串参数
     */
    @Query("SELECT version FROM config_data WHERE config_key = 'model-router-config' ORDER BY version ASC")
    Flux<Integer> testQuery();

    /**
     * 查找配置键的所有版本实体（按版本号升序）
     */
    @Query("SELECT * FROM config_data WHERE config_key = $1 ORDER BY version ASC")
    Flux<ConfigEntity> findAllByConfigKeyOrderByVersionAsc(String configKey);

    /**
     * 查找所有最新版本的配置键
     */
    @Query("SELECT DISTINCT config_key FROM config_data WHERE is_latest = true")
    Flux<String> findAllLatestConfigKeys();

    /**
     * 删除指定配置键的所有版本
     */
    @Query("DELETE FROM config_data WHERE config_key = $1")
    Mono<Void> deleteAllByConfigKey(String configKey);

    /**
     * 删除指定配置键的指定版本
     */
    @Query("DELETE FROM config_data WHERE config_key = $1 AND version = $2")
    Mono<Void> deleteByConfigKeyAndVersion(String configKey, Integer version);

    /**
     * 将指定配置键的所有版本标记为非最新
     */
    @Query("UPDATE config_data SET is_latest = false WHERE config_key = $1")
    Mono<Void> markAllAsNotLatest(String configKey);

    /**
     * 检查配置键是否存在
     */
    @Query("SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM config_data WHERE config_key = $1 AND is_latest = true")
    Mono<Boolean> existsByConfigKey(String configKey);

    /**
     * 检查指定版本是否存在
     */
    @Query("SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM config_data WHERE config_key = $1 AND version = $2")
    Mono<Boolean> existsByConfigKeyAndVersion(String configKey, Integer version);
}

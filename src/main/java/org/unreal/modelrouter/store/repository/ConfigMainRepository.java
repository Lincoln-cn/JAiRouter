package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ConfigMainEntity;
import reactor.core.publisher.Mono;

/**
 * 配置主表数据仓库
 */
@Repository
public interface ConfigMainRepository extends R2dbcRepository<ConfigMainEntity, Long> {

    /**
     * 根据配置键查找
     */
    @Query("SELECT * FROM config_main WHERE config_key = :configKey")
    Mono<ConfigMainEntity> findByConfigKey(@Param("configKey") String configKey);

    /**
     * 更新当前版本号
     */
    @Modifying
    @Query("UPDATE config_main SET current_version = :version, updated_at = CURRENT_TIMESTAMP, updated_by = :updatedBy WHERE config_key = :configKey")
    Mono<Integer> updateCurrentVersion(@Param("configKey") String configKey, 
                                       @Param("version") Integer version, 
                                       @Param("updatedBy") String updatedBy);

    /**
     * 更新描述信息
     */
    @Modifying
    @Query("UPDATE config_main SET description = :description, updated_at = CURRENT_TIMESTAMP, updated_by = :updatedBy WHERE config_key = :configKey")
    Mono<Integer> updateDescription(@Param("configKey") String configKey, 
                                    @Param("description") String description, 
                                    @Param("updatedBy") String updatedBy);
}

package org.unreal.modelrouter.store.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ConfigChangeHistoryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置变更历史表数据仓库
 */
@Repository
public interface ConfigChangeHistoryRepository extends JpaRepository<ConfigChangeHistoryEntity, Long> {

    /**
     * 根据配置键查找变更历史（按时间倒序）
     */
    @Query("SELECT * FROM config_change_history WHERE config_key = :configKey ORDER BY changed_at DESC LIMIT :limit")
    Flux<ConfigChangeHistoryEntity> findRecentChanges(@Param("configKey") String configKey, 
                                                       @Param("limit") Integer limit);

    /**
     * 根据配置键和操作类型查找变更历史
     */
    @Query("SELECT * FROM config_change_history WHERE config_key = :configKey AND operation_type = :operationType ORDER BY changed_at DESC")
    Flux<ConfigChangeHistoryEntity> findByOperationType(@Param("configKey") String configKey, 
                                                         @Param("operationType") String operationType);

    /**
     * 根据配置键和目标类型查找变更历史
     */
    @Query("SELECT * FROM config_change_history WHERE config_key = :configKey AND target_type = :targetType ORDER BY changed_at DESC")
    Flux<ConfigChangeHistoryEntity> findByTargetType(@Param("configKey") String configKey, 
                                                      @Param("targetType") String targetType);

    /**
     * 根据用户查找变更历史
     */
    @Query("SELECT * FROM config_change_history WHERE changed_by = :changedBy ORDER BY changed_at DESC LIMIT :limit")
    Flux<ConfigChangeHistoryEntity> findByChangedBy(@Param("changedBy") String changedBy, 
                                                     @Param("limit") Integer limit);

    /**
     * 根据时间范围查找变更历史
     */
    @Query("SELECT * FROM config_change_history WHERE config_key = :configKey AND changed_at BETWEEN :startTime AND :endTime ORDER BY changed_at DESC")
    Flux<ConfigChangeHistoryEntity> findByTimeRange(@Param("configKey") String configKey, 
                                                     @Param("startTime") java.time.LocalDateTime startTime, 
                                                     @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * 根据请求 ID 查找变更历史
     */
    @Query("SELECT * FROM config_change_history WHERE request_id = :requestId")
    Flux<ConfigChangeHistoryEntity> findByRequestId(@Param("requestId") String requestId);

    /**
     * 统计指定时间范围内的变更次数
     */
    @Query("SELECT COUNT(*) FROM config_change_history WHERE config_key = :configKey AND changed_at BETWEEN :startTime AND :endTime")
    Mono<Long> countChangesInTimeRange(@Param("configKey") String configKey, 
                                        @Param("startTime") java.time.LocalDateTime startTime, 
                                        @Param("endTime") java.time.LocalDateTime endTime);
}

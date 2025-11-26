package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.SecurityAuditEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 安全审计数据仓库
 */
@Repository
public interface SecurityAuditRepository extends R2dbcRepository<SecurityAuditEntity, Long> {

    /**
     * 根据事件ID查找
     */
    @Query("SELECT * FROM security_audit WHERE event_id = :eventId")
    Mono<SecurityAuditEntity> findByEventId(@Param("eventId") String eventId);

    /**
     * 根据时间范围查询
     */
    @Query("SELECT * FROM security_audit WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC LIMIT :limit")
    Flux<SecurityAuditEntity> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit
    );

    /**
     * 根据用户ID查询
     */
    @Query("SELECT * FROM security_audit WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit")
    Flux<SecurityAuditEntity> findByUserId(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 根据事件类型查询
     */
    @Query("SELECT * FROM security_audit WHERE event_type = :eventType ORDER BY timestamp DESC LIMIT :limit")
    Flux<SecurityAuditEntity> findByEventType(@Param("eventType") String eventType, @Param("limit") int limit);

    /**
     * 根据客户端IP查询
     */
    @Query("SELECT * FROM security_audit WHERE client_ip = :clientIp ORDER BY timestamp DESC LIMIT :limit")
    Flux<SecurityAuditEntity> findByClientIp(@Param("clientIp") String clientIp, @Param("limit") int limit);

    /**
     * 复杂查询
     */
    @Query("SELECT * FROM security_audit WHERE " +
           "timestamp BETWEEN :startTime AND :endTime " +
           "AND (:eventType IS NULL OR event_type = :eventType) " +
           "AND (:userId IS NULL OR user_id = :userId) " +
           "AND (:clientIp IS NULL OR client_ip = :clientIp) " +
           "AND (:success IS NULL OR success = :success) " +
           "ORDER BY timestamp DESC LIMIT :limit")
    Flux<SecurityAuditEntity> findByMultipleConditions(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("eventType") String eventType,
            @Param("userId") String userId,
            @Param("clientIp") String clientIp,
            @Param("success") Boolean success,
            @Param("limit") int limit
    );

    /**
     * 删除指定时间之前的记录
     */
    @Query("DELETE FROM security_audit WHERE timestamp < :cutoffTime")
    Mono<Long> deleteByTimestampBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 统计指定时间范围内的事件数量
     */
    @Query("SELECT COUNT(*) FROM security_audit WHERE timestamp BETWEEN :startTime AND :endTime")
    Mono<Long> countByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 统计指定事件类型的数量
     */
    @Query("SELECT COUNT(*) FROM security_audit WHERE event_type = :eventType AND timestamp BETWEEN :startTime AND :endTime")
    Mono<Long> countByEventType(
            @Param("eventType") String eventType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 根据时间范围查询（不限制数量）
     */
    @Query("SELECT * FROM security_audit WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Flux<SecurityAuditEntity> findByTimestampBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 根据用户ID和时间范围查询
     */
    @Query("SELECT * FROM security_audit WHERE user_id = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Flux<SecurityAuditEntity> findByUserIdAndTimestampBetween(
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 根据客户端IP和时间范围查询
     */
    @Query("SELECT * FROM security_audit WHERE client_ip = :clientIp AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Flux<SecurityAuditEntity> findByClientIpAndTimestampBetween(
            @Param("clientIp") String clientIp,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 根据事件类型列表和时间范围查询
     */
    @Query("SELECT * FROM security_audit WHERE event_type IN (:eventTypes) AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Flux<SecurityAuditEntity> findByEventTypeInAndTimestampBetween(
            @Param("eventTypes") java.util.List<String> eventTypes,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 根据事件类型和时间范围查询
     */
    @Query("SELECT * FROM security_audit WHERE event_type = :eventType AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Flux<SecurityAuditEntity> findByEventTypeAndTimestampBetween(
            @Param("eventType") String eventType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 根据事件类型、用户ID和时间范围查询
     */
    @Query("SELECT * FROM security_audit WHERE event_type = :eventType AND user_id = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Flux<SecurityAuditEntity> findByEventTypeAndUserIdAndTimestampBetween(
            @Param("eventType") String eventType,
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}

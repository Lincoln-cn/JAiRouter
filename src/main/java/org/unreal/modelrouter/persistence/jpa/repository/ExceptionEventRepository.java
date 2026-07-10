package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.ExceptionEventEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 异常事件仓库接口
 *
 * @author JAiRouter Team
 * @since 1.9.1
 */
@Repository
public interface ExceptionEventRepository extends JpaRepository<ExceptionEventEntity, Long> {

    // ========== 基本查询方法 ==========

    /**
     * 根据事件 ID 查找
     */
    Optional<ExceptionEventEntity> findByEventId(String eventId);

    /**
     * 根据异常类型查找
     */
    List<ExceptionEventEntity> findByExceptionType(String exceptionType);

    /**
     * 根据操作名称查找
     */
    List<ExceptionEventEntity> findByOperation(String operation);

    /**
     * 根据错误代码查找
     */
    List<ExceptionEventEntity> findByErrorCode(String errorCode);

    /**
     * 根据错误分类查找
     */
    List<ExceptionEventEntity> findByErrorCategory(String errorCategory);

    /**
     * 根据追踪 ID 查找
     */
    List<ExceptionEventEntity> findByTraceId(String traceId);

    /**
     * 根据客户端 IP 查找
     */
    List<ExceptionEventEntity> findByClientIp(String clientIp);

    /**
     * 根据聚合状态查找
     */
    List<ExceptionEventEntity> findByIsAggregated(Boolean isAggregated);

    // ========== 分页查询方法 ==========

    /**
     * 根据异常类型分页查找
     */
    Page<ExceptionEventEntity> findByExceptionType(String exceptionType, Pageable pageable);

    /**
     * 根据操作名称分页查找
     */
    Page<ExceptionEventEntity> findByOperation(String operation, Pageable pageable);

    /**
     * 根据错误分类分页查找
     */
    Page<ExceptionEventEntity> findByErrorCategory(String errorCategory, Pageable pageable);

    /**
     * 根据聚合状态分页查找
     */
    Page<ExceptionEventEntity> findByIsAggregated(Boolean isAggregated, Pageable pageable);

    // ========== 时间范围查询 ==========

    /**
     * 根据时间范围查询
     */
    @Query("SELECT e FROM ExceptionEventEntity e WHERE e.occurredAt BETWEEN :startTime AND :endTime ORDER BY e.occurredAt DESC")
    List<ExceptionEventEntity> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 根据时间范围分页查询
     */
    @Query("SELECT e FROM ExceptionEventEntity e WHERE e.occurredAt BETWEEN :startTime AND :endTime ORDER BY e.occurredAt DESC")
    Page<ExceptionEventEntity> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 统计时间范围内的异常数量
     */
    @Query("SELECT COUNT(e) FROM ExceptionEventEntity e WHERE e.occurredAt BETWEEN :startTime AND :endTime")
    long countByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // ========== 多条件组合查询 ==========

    /**
     * 根据条件分页查询
     */
    @Query("SELECT e FROM ExceptionEventEntity e WHERE "
           + "(:startTime IS NULL OR e.occurredAt >= :startTime) AND "
           + "(:endTime IS NULL OR e.occurredAt <= :endTime) AND "
           + "(:exceptionType IS NULL OR e.exceptionType LIKE %:exceptionType%) AND "
           + "(:operation IS NULL OR e.operation LIKE %:operation%) AND "
           + "(:errorCode IS NULL OR e.errorCode = :errorCode) AND "
           + "(:errorCategory IS NULL OR e.errorCategory = :errorCategory) AND "
           + "(:traceId IS NULL OR e.traceId = :traceId) AND "
           + "(:clientIp IS NULL OR e.clientIp = :clientIp) AND "
           + "(:isAggregated IS NULL OR e.isAggregated = :isAggregated) AND "
           + "(:serviceType IS NULL OR e.serviceType = :serviceType) AND "
           + "(:modelName IS NULL OR e.modelName LIKE %:modelName%) "
           + "ORDER BY e.occurredAt DESC")
    Page<ExceptionEventEntity> findByConditions(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("exceptionType") String exceptionType,
            @Param("operation") String operation,
            @Param("errorCode") String errorCode,
            @Param("errorCategory") String errorCategory,
            @Param("traceId") String traceId,
            @Param("clientIp") String clientIp,
            @Param("isAggregated") Boolean isAggregated,
            @Param("serviceType") String serviceType,
            @Param("modelName") String modelName,
            Pageable pageable);

    /**
     * 统计符合条件的异常数量
     */
    @Query("SELECT COUNT(e) FROM ExceptionEventEntity e WHERE "
           + "(:startTime IS NULL OR e.occurredAt >= :startTime) AND "
           + "(:endTime IS NULL OR e.occurredAt <= :endTime) AND "
           + "(:exceptionType IS NULL OR e.exceptionType LIKE %:exceptionType%) AND "
           + "(:operation IS NULL OR e.operation LIKE %:operation%) AND "
           + "(:errorCode IS NULL OR e.errorCode = :errorCode) AND "
           + "(:errorCategory IS NULL OR e.errorCategory = :errorCategory) AND "
           + "(:traceId IS NULL OR e.traceId = :traceId) AND "
           + "(:clientIp IS NULL OR e.clientIp = :clientIp) AND "
           + "(:isAggregated IS NULL OR e.isAggregated = :isAggregated)")
    long countByConditions(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("exceptionType") String exceptionType,
            @Param("operation") String operation,
            @Param("errorCode") String errorCode,
            @Param("errorCategory") String errorCategory,
            @Param("traceId") String traceId,
            @Param("clientIp") String clientIp,
            @Param("isAggregated") Boolean isAggregated);

    // ========== 统计查询 ==========

    /**
     * 按异常类型统计
     */
    @Query("SELECT e.exceptionType, COUNT(e) FROM ExceptionEventEntity e "
           + "WHERE e.occurredAt BETWEEN :startTime AND :endTime "
           + "GROUP BY e.exceptionType ORDER BY COUNT(e) DESC")
    List<Object[]> countByExceptionType(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按错误分类统计
     */
    @Query("SELECT e.errorCategory, COUNT(e) FROM ExceptionEventEntity e "
           + "WHERE e.occurredAt BETWEEN :startTime AND :endTime "
           + "GROUP BY e.errorCategory ORDER BY COUNT(e) DESC")
    List<Object[]> countByErrorCategory(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按操作统计
     */
    @Query("SELECT e.operation, COUNT(e) FROM ExceptionEventEntity e "
           + "WHERE e.occurredAt BETWEEN :startTime AND :endTime "
           + "GROUP BY e.operation ORDER BY COUNT(e) DESC")
    List<Object[]> countByOperation(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    /**
     * 按客户端 IP 统计
     */
    @Query("SELECT e.clientIp, COUNT(e) FROM ExceptionEventEntity e "
           + "WHERE e.occurredAt BETWEEN :startTime AND :endTime "
           + "AND e.clientIp IS NOT NULL "
           + "GROUP BY e.clientIp ORDER BY COUNT(e) DESC")
    List<Object[]> countByClientIp(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    /**
     * 按 HTTP 状态码统计
     */
    @Query("SELECT e.httpStatus, COUNT(e) FROM ExceptionEventEntity e "
           + "WHERE e.occurredAt BETWEEN :startTime AND :endTime "
           + "AND e.httpStatus IS NOT NULL "
           + "GROUP BY e.httpStatus ORDER BY COUNT(e) DESC")
    List<Object[]> countByHttpStatus(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按小时分布统计
     */
    @Query("SELECT FUNCTION('HOUR', e.occurredAt), COUNT(e) FROM ExceptionEventEntity e "
           + "WHERE e.occurredAt BETWEEN :startTime AND :endTime "
           + "GROUP BY FUNCTION('HOUR', e.occurredAt) ORDER BY FUNCTION('HOUR', e.occurredAt)")
    List<Object[]> countByHour(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计聚合的异常数量
     */
    @Query("SELECT COUNT(e) FROM ExceptionEventEntity e WHERE e.isAggregated = true")
    long countAggregatedEvents();

    /**
     * 统计未聚合的异常数量
     */
    @Query("SELECT COUNT(e) FROM ExceptionEventEntity e WHERE e.isAggregated = false")
    long countUnaggregatedEvents();

    // ========== 最近事件查询 ==========

    /**
     * 获取最近的异常事件
     */
    @Query("SELECT e FROM ExceptionEventEntity e ORDER BY e.occurredAt DESC LIMIT :limit")
    List<ExceptionEventEntity> findRecentEvents(@Param("limit") int limit);

    /**
     * 获取指定异常类型的最近事件
     */
    @Query("SELECT e FROM ExceptionEventEntity e WHERE e.exceptionType = :exceptionType ORDER BY e.occurredAt DESC LIMIT :limit")
    List<ExceptionEventEntity> findRecentEventsByType(
            @Param("exceptionType") String exceptionType,
            @Param("limit") int limit);

    // ========== 清理过期数据 ==========

    /**
     * 删除指定时间之前的异常事件
     */
    @Modifying
    @Query("DELETE FROM ExceptionEventEntity e WHERE e.occurredAt < :cutoffTime")
    int deleteByOccurredAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 删除已聚合的过期异常事件
     */
    @Modifying
    @Query("DELETE FROM ExceptionEventEntity e WHERE e.occurredAt < :cutoffTime AND e.isAggregated = true")
    int deleteAggregatedEventsBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    // ========== 聚合相关查询 ==========

    /**
     * 查找相同类型的未聚合异常
     */
    @Query("SELECT e FROM ExceptionEventEntity e WHERE "
           + "e.exceptionType = :exceptionType AND "
           + "e.operation = :operation AND "
           + "e.isAggregated = false "
           + "ORDER BY e.occurredAt ASC")
    List<ExceptionEventEntity> findUnaggregatedEventsByTypeAndOperation(
            @Param("exceptionType") String exceptionType,
            @Param("operation") String operation);

    /**
     * 查找需要聚合的异常（超过阈值）
     */
    @Query("SELECT e.exceptionType, e.operation, COUNT(e) as cnt, MIN(e.occurredAt) as first, MAX(e.occurredAt) as last "
           + "FROM ExceptionEventEntity e WHERE e.isAggregated = false "
           + "GROUP BY e.exceptionType, e.operation "
           + "HAVING COUNT(e) >= :threshold")
    List<Object[]> findEventsForAggregation(@Param("threshold") int threshold);
}
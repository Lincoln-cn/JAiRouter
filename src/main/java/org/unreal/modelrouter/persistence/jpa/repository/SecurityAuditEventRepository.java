package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 安全审计事件仓库
 */
@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEventEntity, Long>,
        JpaSpecificationExecutor<SecurityAuditEventEntity> {

    // ========== 基本查询方法 ==========

    Optional<SecurityAuditEventEntity> findByEventId(String eventId);

    List<SecurityAuditEventEntity> findByEventType(AuditEventType eventType);

    List<SecurityAuditEventEntity> findByUserId(String userId);

    List<SecurityAuditEventEntity> findByResourceId(String resourceId);

    List<SecurityAuditEventEntity> findByClientIp(String clientIp);

    List<SecurityAuditEventEntity> findBySuccess(Boolean success);

    List<SecurityAuditEventEntity> findByEventCategory(String eventCategory);

    List<SecurityAuditEventEntity> findByRiskLevel(RiskLevel riskLevel);

    // ========== 分页查询方法 ==========

    Page<SecurityAuditEventEntity> findByEventType(AuditEventType eventType, Pageable pageable);

    Page<SecurityAuditEventEntity> findByUserId(String userId, Pageable pageable);

    Page<SecurityAuditEventEntity> findByEventCategory(String eventCategory, Pageable pageable);

    Page<SecurityAuditEventEntity> findBySuccess(Boolean success, Pageable pageable);

    // ========== 时间范围查询 ==========

    @Query("SELECT e FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime "
           + "ORDER BY e.timestamp DESC")
    List<SecurityAuditEventEntity> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT e FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime "
           + "ORDER BY e.timestamp DESC")
    Page<SecurityAuditEventEntity> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    @Query("SELECT COUNT(e) FROM SecurityAuditEventEntity e WHERE e.timestamp BETWEEN :startTime AND :endTime")
    long countByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // ========== 统计查询 ==========

    @Query("SELECT e.eventType, COUNT(e) FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime "
           + "GROUP BY e.eventType ORDER BY COUNT(e) DESC")
    List<Object[]> countByEventType(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT e.eventCategory, COUNT(e) FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime "
           + "GROUP BY e.eventCategory ORDER BY COUNT(e) DESC")
    List<Object[]> countByEventCategory(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT e.userId, COUNT(e) FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime "
           + "AND e.userId IS NOT NULL "
           + "GROUP BY e.userId ORDER BY COUNT(e) DESC")
    List<Object[]> countByUserId(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    @Query("SELECT e.clientIp, COUNT(e) FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime "
           + "AND e.clientIp IS NOT NULL "
           + "GROUP BY e.clientIp ORDER BY COUNT(e) DESC")
    List<Object[]> countByClientIp(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    @Query("SELECT COUNT(e) FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime AND e.success = false")
    long countFailedEvents(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(e) FROM SecurityAuditEventEntity e "
           + "WHERE e.timestamp BETWEEN :startTime AND :endTime AND e.success = true")
    long countSuccessEvents(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // ========== 告警阈值检查 ==========

    @Query("SELECT COUNT(e) FROM SecurityAuditEventEntity e WHERE "
           + "e.eventType = :eventType AND "
           + "e.timestamp >= :windowStart AND "
           + "e.timestamp <= :windowEnd")
    long countEventsInTimeWindow(
            @Param("eventType") AuditEventType eventType,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd);

    @Query("SELECT COUNT(e) FROM SecurityAuditEventEntity e WHERE "
           + "e.eventType IN :eventTypes AND "
           + "e.timestamp >= :windowStart AND "
           + "e.timestamp <= :windowEnd AND "
           + "e.success = false")
    long countFailedEventsInTimeWindow(
            @Param("eventTypes") List<AuditEventType> eventTypes,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd);

    // ========== 清理过期日志 ==========

    @Modifying
    @Query("DELETE FROM SecurityAuditEventEntity e WHERE e.timestamp < :cutoffTime")
    int deleteByTimestampBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Modifying
    @Query("DELETE FROM SecurityAuditEventEntity e WHERE e.timestamp < :cutoffTime AND e.riskLevel IN :lowRiskLevels")
    int deleteLowRiskEventsBefore(
            @Param("cutoffTime") LocalDateTime cutoffTime,
            @Param("lowRiskLevels") List<RiskLevel> lowRiskLevels);

    // ========== 获取最近事件 ==========

    @Query("SELECT e FROM SecurityAuditEventEntity e ORDER BY e.timestamp DESC LIMIT :limit")
    List<SecurityAuditEventEntity> findRecentEvents(@Param("limit") int limit);

    @Query("SELECT e FROM SecurityAuditEventEntity e WHERE e.userId = :userId ORDER BY e.timestamp DESC LIMIT :limit")
    List<SecurityAuditEventEntity> findRecentEventsByUser(
            @Param("userId") String userId,
            @Param("limit") int limit);

    @Query("SELECT e FROM SecurityAuditEventEntity e "
           + "WHERE e.clientIp = :clientIp "
           + "ORDER BY e.timestamp DESC LIMIT :limit")
    List<SecurityAuditEventEntity> findRecentEventsByIp(
            @Param("clientIp") String clientIp,
            @Param("limit") int limit);

    // ========== 高风险事件查询 ==========

    @Query("SELECT e FROM SecurityAuditEventEntity e WHERE "
           + "e.riskLevel IN :highRiskLevels "
           + "AND e.timestamp >= :startTime "
           + "ORDER BY e.timestamp DESC")
    List<SecurityAuditEventEntity> findHighRiskEvents(
            @Param("highRiskLevels") List<RiskLevel> highRiskLevels,
            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(e) FROM SecurityAuditEventEntity e WHERE "
           + "e.riskLevel IN :highRiskLevels AND "
           + "e.timestamp BETWEEN :startTime AND :endTime")
    long countHighRiskEvents(
            @Param("highRiskLevels") List<RiskLevel> highRiskLevels,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
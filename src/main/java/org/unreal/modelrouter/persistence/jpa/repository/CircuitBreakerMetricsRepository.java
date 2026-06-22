package org.unreal.modelrouter.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.CircuitBreakerMetricsEntity;

/**
 * 熔断器统计数据 Repository
 * 
 * @author JAiRouter Team
 * @since v2.6.12
 */
@Repository
public interface CircuitBreakerMetricsRepository extends JpaRepository<CircuitBreakerMetricsEntity, Long> {

    /**
     * 按实例 ID 查找最新统计数据
     */
    Optional<CircuitBreakerMetricsEntity> findFirstByInstanceIdOrderByWindowEndDesc(String instanceId);

    /**
     * 查找指定时间范围内的统计数据
     */
    List<CircuitBreakerMetricsEntity> findByWindowEndBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 按实例 ID 和时间范围查找统计数据
     */
    List<CircuitBreakerMetricsEntity> findByInstanceIdAndWindowEndBetween(
            String instanceId, LocalDateTime start, LocalDateTime end);

    /**
     * 查找所有待应用的调整
     */
    @Query("SELECT m FROM CircuitBreakerMetricsEntity m WHERE m.adjustmentApplied = false AND m.adjustedFailureThreshold IS NOT NULL")
    List<CircuitBreakerMetricsEntity> findPendingAdjustments();

    /**
     * 统计指定时间范围内的总调用数据
     */
    @Query("SELECT SUM(m.totalCalls), SUM(m.failureCalls), SUM(m.successCalls) " +
           "FROM CircuitBreakerMetricsEntity m " +
           "WHERE m.instanceId = :instanceId AND m.windowEnd BETWEEN :start AND :end")
    Object[] sumCallsByInstanceIdAndTimeRange(
            @Param("instanceId") String instanceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 删除指定时间之前的统计数据
     */
    void deleteByWindowEndBefore(LocalDateTime before);

    /**
     * 查找所有实例的最新统计数据（每个实例一条）
     */
    @Query("SELECT m FROM CircuitBreakerMetricsEntity m " +
           "WHERE m.windowEnd = (SELECT MAX(m2.windowEnd) FROM CircuitBreakerMetricsEntity m2 WHERE m2.instanceId = m.instanceId)")
    List<CircuitBreakerMetricsEntity> findLatestMetricsForAllInstances();
}

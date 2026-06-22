package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.CircuitBreakerStateHistoryEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 熔断器状态变化历史记录仓库
 * 
 * v2.6.13: 新增
 */
@Repository
public interface CircuitBreakerStateHistoryRepository extends JpaRepository<CircuitBreakerStateHistoryEntity, Long> {

    /**
     * 按实例 ID 查询历史记录（分页）
     */
    Page<CircuitBreakerStateHistoryEntity> findByInstanceIdOrderByChangedAtDesc(
            String instanceId, Pageable pageable);

    /**
     * 按实例 ID 查询最近的 N 条历史记录
     */
    List<CircuitBreakerStateHistoryEntity> findTop100ByInstanceIdOrderByChangedAtDesc(String instanceId);

    /**
     * 查询所有历史记录（分页，按时间倒序）
     */
    Page<CircuitBreakerStateHistoryEntity> findAllByOrderByChangedAtDesc(Pageable pageable);

    /**
     * 按服务类型查询历史记录（分页）
     */
    Page<CircuitBreakerStateHistoryEntity> findByServiceTypeOrderByChangedAtDesc(
            String serviceType, Pageable pageable);

    /**
     * 按时间范围查询历史记录
     */
    @Query("SELECT h FROM CircuitBreakerStateHistoryEntity h WHERE h.changedAt BETWEEN :startTime AND :endTime ORDER BY h.changedAt DESC")
    List<CircuitBreakerStateHistoryEntity> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 删除指定时间之前的历史记录
     */
    @Modifying
    @Query("DELETE FROM CircuitBreakerStateHistoryEntity h WHERE h.changedAt < :beforeTime")
    int deleteByChangedAtBefore(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 统计指定实例的历史记录数量
     */
    long countByInstanceId(String instanceId);

    /**
     * 统计总记录数
     */
    long count();
}

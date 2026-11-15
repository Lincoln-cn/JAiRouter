package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigAuditLogEntity;

import java.time.Instant;
import java.util.List;

/**
 * Repository for ConfigAuditLogEntity.
 *
 * @since v2.6.12
 */
@Repository
public interface ConfigAuditLogRepository extends JpaRepository<ConfigAuditLogEntity, Long> {

    List<ConfigAuditLogEntity> findByServiceType(String serviceType);

    List<ConfigAuditLogEntity> findByUserId(String userId);

    List<ConfigAuditLogEntity> findByTimestampBetween(Instant start, Instant end);

    @Query("SELECT a FROM ConfigAuditLogEntity a WHERE a.serviceType = :serviceType ORDER BY a.timestamp DESC")
    List<ConfigAuditLogEntity> findByServiceTypeOrderByTimestampDesc(String serviceType);

    @Query("SELECT a FROM ConfigAuditLogEntity a WHERE a.changeType = :changeType")
    List<ConfigAuditLogEntity> findByChangeType(String changeType);

    /** 按 traceId 批量反查，供「操作记录」页把耗时/结果接上链路（#1160） */
    List<ConfigAuditLogEntity> findByTraceIdIn(java.util.Collection<String> traceIds);

    /** 最近的控制台写操作，倒序 */
    @Query("SELECT a FROM ConfigAuditLogEntity a ORDER BY a.timestamp DESC")
    List<ConfigAuditLogEntity> findRecent(org.springframework.data.domain.Pageable pageable);
}

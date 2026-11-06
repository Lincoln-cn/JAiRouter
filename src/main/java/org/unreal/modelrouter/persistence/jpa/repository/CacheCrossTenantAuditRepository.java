package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.CacheCrossTenantAuditEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 语义缓存跨用户命中审计的仓储.
 */
@Repository
public interface CacheCrossTenantAuditRepository extends JpaRepository<CacheCrossTenantAuditEntity, Long> {

    /**
     * 最近若干条审计记录.
     *
     * @return 按时间倒序
     */
    List<CacheCrossTenantAuditEntity> findTop100ByOrderByCreatedAtDesc();

    /**
     * 某个时间之后的记录（按 scope 过滤）.
     *
     * @param scopeId 命中 scope
     * @param since   起始时间
     * @return 记录列表
     */
    List<CacheCrossTenantAuditEntity> findByScopeIdAndCreatedAtAfter(String scopeId, LocalDateTime since);

    /**
     * 删除过期记录.
     *
     * @param cutoff 早于该时间的记录会被删除
     * @return 删除条数
     */
    @Modifying
    @Query("DELETE FROM CacheCrossTenantAuditEntity e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}

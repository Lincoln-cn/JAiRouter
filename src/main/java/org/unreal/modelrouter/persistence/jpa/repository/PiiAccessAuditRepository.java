package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.PiiAccessAuditEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可逆脱敏访问审计仓库（#987）。
 */
@Repository
public interface PiiAccessAuditRepository extends JpaRepository<PiiAccessAuditEntity, Long> {

    List<PiiAccessAuditEntity> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime from);

    List<PiiAccessAuditEntity> findTop100ByOrderByCreatedAtDesc();

    @Modifying
    @Query("DELETE FROM PiiAccessAuditEntity e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}

package org.unreal.modelrouter.persistence.jpa.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.persistence.jpa.entity.TraceArchiveEntity;

/**
 * 异常链路归档仓库（#1155）。
 *
 * @author JAiRouter Team
 * @since 3.8.8
 */
@Repository
public interface TraceArchiveRepository extends JpaRepository<TraceArchiveEntity, String> {

    /** 按时间窗取归档链路（新到旧）。 */
    List<TraceArchiveEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(
        Instant from, Instant to, Pageable pageable);

    /** 清理过期归档，返回删除行数。 */
    @Modifying
    @Transactional
    @Query("DELETE FROM TraceArchiveEntity t WHERE t.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    long countByCreatedAtAfter(Instant since);
}

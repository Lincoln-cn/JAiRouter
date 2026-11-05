package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.PiiTokenVaultEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可逆脱敏占位符仓库（#987）。
 */
@Repository
public interface PiiTokenVaultRepository extends JpaRepository<PiiTokenVaultEntity, Long> {

    /**
     * 取回一次请求的全部占位符映射。
     *
     * <p>按 {@code scope_id} 精确查询，不取过期行 —— 过期的映射不该参与还原，
     * 否则一个长时间挂起的请求可能拿到已失效的原文。</p>
     */
    List<PiiTokenVaultEntity> findByScopeIdAndExpiresAtAfter(String scopeId, LocalDateTime now);

    /**
     * 删除过期映射，返回删除条数。
     *
     * <p>用批量删除而不是「先查再逐条删」：这张表在高流量下增长很快，
     * 逐条删会把一次清理变成 N 次往返。</p>
     */
    @Modifying
    @Query("DELETE FROM PiiTokenVaultEntity e WHERE e.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);
}

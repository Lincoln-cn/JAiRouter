package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.UiPageViewEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制台页面访问记录仓储（B4，2026-11-18）.
 *
 * <p>聚合查询用 JPQL 投影成 {@code Object[]}（路径、角色、访问次数、去重用户数），
 * 与 {@code SecurityAuditEventRepository} 的既有写法保持一致 —— 这层结果只在这一处消费，
 * 不值得为它建一个额外的投影接口。</p>
 */
@Repository
public interface UiPageViewRepository extends JpaRepository<UiPageViewEntity, Long> {

    /**
     * 按路径 × 角色聚合访问热度。
     *
     * @param since 统计起点
     * @param page  行数上限（排序已在语句内固定为访问次数降序）
     * @return 每行为 {@code [path, role, views, users]}
     */
    @Query("""
            SELECT v.path, v.role, COUNT(v), COUNT(DISTINCT v.userId)
            FROM UiPageViewEntity v
            WHERE v.viewedAt >= :since
            GROUP BY v.path, v.role
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> summarize(@Param("since") LocalDateTime since, Pageable page);

    /**
     * 删除早于给定时间的记录（保留期清理）。
     *
     * @param cutoff 截止时间
     * @return 删除行数
     */
    @Modifying
    @Query("DELETE FROM UiPageViewEntity v WHERE v.viewedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** 统计窗口内的总访问次数（页面顶部的口径之一）。 */
    long countByViewedAtGreaterThanEqual(LocalDateTime since);
}

package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.SlaServiceAccountEntity;

import java.util.Optional;

/**
 * SLA 服务账号仓库（issue #1174）。
 */
@Repository
public interface SlaServiceAccountRepository extends JpaRepository<SlaServiceAccountEntity, String> {

    /**
     * 按账号名查找（账号名唯一，同时也是 JWT 主体名 {@code sla:<name>} 的后半段）。
     *
     * @param name 账号名
     * @return 账号
     */
    Optional<SlaServiceAccountEntity> findByName(String name);

    /**
     * 检查账号名是否已被占用。
     *
     * @param name 账号名
     * @return 存在则为 {@code true}
     */
    boolean existsByName(String name);

    /**
     * 按创建时间倒序分页列出账号，供控制台列表使用（issue #1219）。
     *
     * @param pageable 分页参数
     * @return 当前页账号
     */
    Page<SlaServiceAccountEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 统计处于启用状态的账号数，供列表顶部的汇总指标使用（issue #1219）。
     *
     * @return 启用账号数
     */
    long countByEnabledTrue();

    /**
     * 按名称模糊匹配并分页列出账号（名称不含大小写差异）。
     *
     * @param keyword  名称关键字
     * @param pageable 分页参数
     * @return 当前页账号
     */
    Page<SlaServiceAccountEntity> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);
}

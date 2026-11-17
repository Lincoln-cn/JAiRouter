package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.SlaRefreshTokenEntity;

import java.util.List;
import java.util.Optional;

/**
 * SLA 长期刷新凭据仓库（issue #1174）。
 */
@Repository
public interface SlaRefreshTokenRepository extends JpaRepository<SlaRefreshTokenEntity, String> {

    /**
     * 按凭据哈希查找（明文不落库）。
     *
     * @param tokenHash SHA-256 十六进制摘要
     * @return 凭据行
     */
    Optional<SlaRefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * 列出某账号下仍有效的凭据（未吊销）。
     *
     * <p>注意：这里不按过期时间过滤 —— 过期凭据也应当出现在「凭据数」里，让运维看到
     * 「这条已经没人用了但还挂着」，而不是让它凭空消失。</p>
     *
     * @param accountId 账号主键
     * @return 有效凭据列表
     */
    List<SlaRefreshTokenEntity> findByAccountIdAndRevokedAtIsNull(String accountId);

    /**
     * 列出某账号的全部凭据（含已吊销），用于账号详情与清理。
     *
     * @param accountId 账号主键
     * @return 凭据列表
     */
    List<SlaRefreshTokenEntity> findByAccountId(String accountId);

    /**
     * 统计某账号下仍有效的凭据数。
     *
     * @param accountId 账号主键
     * @return 有效凭据数
     */
    long countByAccountIdAndRevokedAtIsNull(String accountId);
}

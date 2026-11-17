package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.SlaBootstrapTokenEntity;

import java.util.List;
import java.util.Optional;

/**
 * SLA 一次性接入令牌仓库（issue #1174）。
 */
@Repository
public interface SlaBootstrapTokenRepository extends JpaRepository<SlaBootstrapTokenEntity, String> {

    /**
     * 按令牌哈希查找（明文不落库）。
     *
     * @param tokenHash SHA-256 十六进制摘要
     * @return 令牌行
     */
    Optional<SlaBootstrapTokenEntity> findByTokenHash(String tokenHash);

    /**
     * 列出某账号的全部令牌行（含已消费的），用于吊销与「未使用凭据数」统计。
     *
     * @param accountId 账号主键
     * @return 令牌行列表
     */
    List<SlaBootstrapTokenEntity> findByAccountId(String accountId);

    /**
     * 统计某账号下尚未被消费的令牌数。
     *
     * @param accountId 账号主键
     * @return 未消费数量
     */
    long countByAccountIdAndUsedAtIsNull(String accountId);
}

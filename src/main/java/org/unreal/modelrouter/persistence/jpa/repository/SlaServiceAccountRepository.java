package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.SlaServiceAccountEntity;

import java.util.List;
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
     * 按创建时间倒序列出全部账号，供控制台列表使用。
     *
     * @return 账号列表
     */
    List<SlaServiceAccountEntity> findAllByOrderByCreatedAtDesc();
}

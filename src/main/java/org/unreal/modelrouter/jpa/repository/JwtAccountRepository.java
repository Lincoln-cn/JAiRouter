package org.unreal.modelrouter.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.jpa.entity.JwtAccountEntity;

import java.util.Optional;

/**
 * JWT 账户仓库 (JPA 版本)
 * v1.5.2: 从 R2DBC 迁移到 JPA
 */
@Repository
public interface JwtAccountRepository extends JpaRepository<JwtAccountEntity, Long> {

    /**
     * 根据用户名查找
     */
    Optional<JwtAccountEntity> findByUsername(String username);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
}

package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.LoginGuardConfigEntity;

/**
 * 登录防护配置仓储（单行表）.
 *
 * @since 2026-11（issue #1176）
 */
@Repository
public interface LoginGuardConfigRepository extends JpaRepository<LoginGuardConfigEntity, String> {
}

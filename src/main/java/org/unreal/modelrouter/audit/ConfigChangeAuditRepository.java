package org.unreal.modelrouter.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 配置变更审计仓库
 * v1.5.1: JPA 版本
 */
@Repository
public interface ConfigChangeAuditRepository extends JpaRepository<ConfigChangeAuditEntity, Long> {
}

package org.unreal.modelrouter.auth.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 配置变更审计仓库
 * v1.5.1: JPA 版本
 */
@Repository
public interface ConfigChangeAuditRepository extends JpaRepository<ConfigChangeAuditEntity, Long> {

    /**
     * 按追踪 ID 查变更记录（#1154）。
     *
     * <p>控制台操作页用 traceId 把「追踪里的耗时」与「审计里的操作人」对上。</p>
     *
     * @param traceId 追踪 ID
     * @return 该请求产生的变更记录，通常 0 或 1 条
     */
    /**
     * 按追踪 ID 批量查变更记录（#1154）。
     *
     * <p>控制台操作页一次要渲染多条链路，逐条查会变成 N+1。</p>
     *
     * @param traceIds 追踪 ID 集合
     * @return 命中的变更记录
     */
    List<ConfigChangeAuditEntity> findByTraceIdIn(Collection<String> traceIds);
}

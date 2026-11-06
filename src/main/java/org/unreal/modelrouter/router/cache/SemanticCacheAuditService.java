package org.unreal.modelrouter.router.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.persistence.jpa.entity.CacheCrossTenantAuditEntity;
import org.unreal.modelrouter.persistence.jpa.repository.CacheCrossTenantAuditRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 语义缓存命中审计.
 *
 * <p>只记录「命中了哪个 scope、条目多旧、桶多大」。写入失败只打日志，
 * 不影响请求 —— 审计是旁路，不能因为审计库不可用就让缓存整体不可用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheAuditService {

    /** 审计记录保留天数. */
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final CacheCrossTenantAuditRepository repository;

    /**
     * 记录一次命中.
     *
     * @param scopeId     命中的 scope
     * @param modelName   模型名
     * @param entryAgeMs  条目从写入到被读到的毫秒数
     * @param bucketSize  命中时桶内候选条数
     */
    public void record(final String scopeId,
                       final String modelName,
                       final long entryAgeMs,
                       final int bucketSize) {
        try {
            repository.save(CacheCrossTenantAuditEntity.builder()
                    .scopeId(scopeId)
                    .modelName(modelName)
                    .entryAgeMs(Math.max(0L, entryAgeMs))
                    .bucketSize(Math.max(0, bucketSize))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (final RuntimeException e) {
            log.warn("记录语义缓存命中审计失败（不影响请求）: scope={}, model={}", scopeId, modelName, e);
        }
    }

    /**
     * 最近一百条审计记录.
     *
     * @return 按时间倒序
     */
    @Transactional(readOnly = true)
    public List<CacheCrossTenantAuditEntity> recent() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }

    /**
     * 删除过期审计记录.
     *
     * @return 删除条数
     */
    @Transactional
    public int cleanupExpired() {
        final LocalDateTime cutoff = LocalDateTime.now().minusDays(DEFAULT_RETENTION_DAYS);
        return repository.deleteByCreatedAtBefore(cutoff);
    }
}

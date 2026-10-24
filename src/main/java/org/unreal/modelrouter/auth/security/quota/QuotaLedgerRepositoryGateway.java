package org.unreal.modelrouter.auth.security.quota;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 配额账本的数据库访问层（快照读 / 累加写 / 过期清理）。
 *
 * <p>从 {@code QuotaLedgerService} 抽出（issue #604 批次 B）。这里只做「仓库调用 + 兜底语义」，
 * 不持有本地计数槽位、不判断降级、不做失败重试——那些留给账本服务编排。抽出的理由：原来
 * 读写数据库的 6 个方法（约 120 行）与热路径的计数逻辑混在同一个类里，改动任何一边都要
 * 通读另一边。</p>
 *
 * <p>所有写入方法都只记录日志并返回失败标志，不向调用方抛异常（账本是旁路能力，
 * 落库失败不得影响请求）：{@code accumulate} 的异常语义由调用方决定，见各方法说明。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.1
 */
@Slf4j
final class QuotaLedgerRepositoryGateway {

    private final QuotaLedgerRepository repository;

    /**
     * 构造访问层。
     *
     * @param repository 账本仓库
     */
    QuotaLedgerRepositoryGateway(final QuotaLedgerRepository repository) {
        this.repository = repository;
    }

    /**
     * 按计数键查询快照行。
     *
     * @param key 计数键
     * @return 快照行；不存在时为空
     */
    Optional<QuotaLedgerEntity> findByKey(final QuotaCounterKey key) {
        final QuotaDimension dimension = key.dimension();
        return repository.findByTenantIdAndApiKeyIdAndUserIdAndServiceTypeAndModelAndWindowTypeAndWindowStart(
            dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
            dimension.model(), key.window().name(), key.windowStart());
    }

    /**
     * 读出某计数键的数据库快照。
     *
     * @param key 计数键
     * @return 用量；无记录时为空
     */
    Optional<QuotaUsage> findUsage(final QuotaCounterKey key) {
        return findByKey(key).map(row -> QuotaUsageMapper.toUsage(key, row));
    }

    /**
     * 按增量累加快照行。
     *
     * <p>读库异常原样抛出：冷启动基线读取失败要由 {@code reserve} 按 fail-open 处理，
     * 不能折叠成「零用量」。</p>
     *
     * @param key      计数键
     * @param requests 请求数增量
     * @param tokens   token 增量
     * @param now      当前时间
     * @return 受影响行数（0 表示行不存在）
     */
    int accumulate(final QuotaCounterKey key, final long requests, final long tokens, final LocalDateTime now) {
        final QuotaDimension dimension = key.dimension();
        return repository.accumulate(
            dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
            dimension.model(), key.window().name(), key.windowStart(), requests, tokens, now);
    }

    /**
     * 插入快照行（首次落库写入绝对值）。
     *
     * <p>并发插入撞唯一约束时返回 {@code false}（下一轮以累加方式落库），其余异常同样只告警。</p>
     *
     * @param key      计数键
     * @param requests 累计请求数
     * @param tokens   累计 token 数
     * @param now      当前时间
     * @return 是否插入成功
     */
    boolean insert(final QuotaCounterKey key, final long requests, final long tokens, final LocalDateTime now) {
        final QuotaDimension dimension = key.dimension();
        try {
            repository.save(QuotaLedgerEntity.builder()
                .tenantId(dimension.tenantId())
                .apiKeyId(dimension.apiKeyId())
                .userId(dimension.userId())
                .serviceType(dimension.serviceType())
                .model(dimension.model())
                .windowType(key.window().name())
                .windowStart(key.windowStart())
                .requestCount(requests)
                .tokenCount(tokens)
                .updatedAt(now)
                .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("配额账本行已存在（并发插入），下轮以累加方式落库: apiKeyId={}", dimension.apiKeyId());
            return false;
        } catch (Exception e) {
            log.warn("配额账本行插入失败: apiKeyId={}, window={}, error={}",
                dimension.apiKeyId(), key.window(), e.toString());
            return false;
        }
    }

    /**
     * 删除某个 API Key 的全部快照行。
     *
     * @param apiKeyId API Key ID
     * @return 删除行数；读库异常时抛出，由调用方决定是否吞掉
     */
    int deleteByApiKeyId(final String apiKeyId) {
        return repository.deleteByApiKeyId(apiKeyId);
    }

    /**
     * 列出某个 API Key 的全部快照行（管理侧视图）。
     *
     * @param apiKeyId API Key ID
     * @return 快照行列表
     */
    List<QuotaLedgerEntity> findByApiKeyId(final String apiKeyId) {
        return repository.findByApiKeyId(apiKeyId);
    }

    /**
     * 删除指定窗口的过期行。
     *
     * @param window 窗口类型
     * @param cutoff 保留期截止时间
     * @return 删除行数；异常时返回 0 并告警
     */
    int deleteExpired(final QuotaWindow window, final LocalDateTime cutoff) {
        try {
            return repository.deleteExpired(window.name(), cutoff);
        } catch (Exception e) {
            log.warn("配额账本过期行清理失败: window={}, error={}", window, e.toString());
            return 0;
        }
    }
}

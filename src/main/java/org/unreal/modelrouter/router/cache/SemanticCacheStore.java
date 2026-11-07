package org.unreal.modelrouter.router.cache;

import java.util.List;

/**
 * 语义缓存的存储后端抽象（issue #1002）。
 *
 * <p>判定逻辑（SimHash 相似度）留在 {@link SemanticCacheService}，本接口只负责条目的存取：
 * 内存实现（{@link InMemorySemanticCacheStore}，默认）与 Redis 实现（{@link RedisSemanticCacheStore}）
 * 互换后，多副本可以共享同一份候选集，命中率不再按副本数摊薄。</p>
 *
 * <p><b>失败语义（全接口统一）</b>：任何后端异常都不阻断请求 —— 查找按未命中、写入按丢弃、
 * 清除按空操作处理，由实现方自行记录告警日志。</p>
 *
 * @since v3.8.0
 */
public interface SemanticCacheStore {

    /**
     * 拉取某 scope 下的全部未过期候选（比对仍在 {@link SemanticCacheService} 内存中进行）。
     *
     * @param scope 缓存 scope（见 {@link SemanticCacheScope}）
     * @return 未过期候选列表（按写入顺序，旧的在前）；后端异常时返回空列表
     */
    List<SemanticCacheEntry> candidates(String scope);

    /**
     * 写入一个条目（容量与 TTL 淘汰由实现方负责）。
     *
     * @param scope 缓存 scope
     * @param entry 条目
     */
    void put(String scope, SemanticCacheEntry entry);

    /**
     * 清除某 scope 的全部条目。
     *
     * @param scope scope；{@code null} 表示全清
     * @return 清除的条目数（后端异常时返回 0）
     */
    int evict(String scope);

    /**
     * 按租户清除全部 L2 条目（scope 以 {@code t|<apiKeyId>|} 开头的桶）。
     *
     * @param apiKeyId 租户标识
     * @return 清除的条目数（后端异常时返回 0）
     */
    int evictTenant(String apiKeyId);

    /**
     * 当前条目总数（供测试与运维观测）。
     *
     * @return 条目数
     */
    int size();
}

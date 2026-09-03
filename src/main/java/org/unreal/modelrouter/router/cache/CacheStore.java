package org.unreal.modelrouter.router.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * v2.9.9: 响应缓存存储抽象.
 *
 * <p>屏蔽具体缓存实现（默认 Caffeine 内存实现，Redis 实现属 P1）。
 * 命中与否由 {@link Optional} 表达，null 值约定不缓存。
 *
 * @author JAiRouter Team
 * @since 2.9.9
 */
public interface CacheStore {

    /**
     * 按键读取缓存.
     *
     * @param key 缓存键（三段式: rc:{serviceType}:{model}:{sha256}）
     * @return 命中返回缓存值，未命中或键为空返回 {@link Optional#empty()}
     */
    Optional<Object> get(String key);

    /**
     * 写入缓存.
     *
     * <p>键为空或数据为 null 时不做任何操作。
     *
     * @param key 缓存键
     * @param data 缓存数据
     * @param ttl 有效期（由实现可加防雪崩抖动）
     */
    void put(String key, Object data, Duration ttl);

    /**
     * 按键删除缓存条目.
     *
     * @param key 缓存键（为 null 或空白时不做操作）
     */
    void delete(String key);

    /**
     * 按前缀批量删除缓存条目（用于 serviceType / model 级失效）.
     *
     * <p>匹配所有以 {@code prefix} 开头的缓存键并删除。
     * 实现应先清理过期条目以保证计数精确。
     *
     * @param prefix 键前缀（如 {@code rc:chat:} 或 {@code rc:chat:gpt-4:}）
     */
    void deleteByPrefix(String prefix);

    /**
     * 清空全部缓存条目.
     */
    void clear();

    /**
     * 当前缓存条目数（估算值）.
     *
     * @return 条目数
     */
    long size();
}

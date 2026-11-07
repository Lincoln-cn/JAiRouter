package org.unreal.modelrouter.router.cache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 语义缓存的进程内存储实现（issue #1002，默认后端）。
 *
 * <p>语义与容量策略和抽取前的 {@link SemanticCacheService} 内嵌实现完全一致：
 * scope → 条目队列（新条目在尾部、队首最旧），每 scope 容量 {@code maxEntriesPerModel}、
 * 全局容量 {@code globalMaxEntries}，过期条目在读写时惰性清理。</p>
 *
 * @since v3.8.0
 */
public final class InMemorySemanticCacheStore implements SemanticCacheStore {

    private final SemanticCacheProperties properties;

    /** scope → 条目队列（新条目在尾部，队首最旧，便于淘汰）。 */
    private final ConcurrentHashMap<String, Deque<SemanticCacheEntry>> buckets = new ConcurrentHashMap<>();

    private final AtomicInteger totalEntries = new AtomicInteger();

    public InMemorySemanticCacheStore(final SemanticCacheProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<SemanticCacheEntry> candidates(final String scope) {
        final Deque<SemanticCacheEntry> bucket = buckets.get(scope);
        if (bucket == null) {
            return List.of();
        }
        synchronized (bucket) {
            purgeExpired(bucket, System.currentTimeMillis());
            return new ArrayList<>(bucket);
        }
    }

    @Override
    public void put(final String scope, final SemanticCacheEntry entry) {
        final Deque<SemanticCacheEntry> bucket = buckets.computeIfAbsent(scope, key -> new ArrayDeque<>());
        synchronized (bucket) {
            purgeExpired(bucket, System.currentTimeMillis());
            bucket.addLast(entry);
            totalEntries.incrementAndGet();
            while (bucket.size() > Math.max(1, properties.getMaxEntriesPerModel())) {
                bucket.removeFirst();
                totalEntries.decrementAndGet();
            }
        }
        evictIfOverGlobalLimit();
    }

    @Override
    public int evict(final String scope) {
        if (scope == null) {
            final int removed = size();
            buckets.clear();
            totalEntries.set(0);
            return removed;
        }
        final Deque<SemanticCacheEntry> bucket = buckets.remove(scope);
        if (bucket == null) {
            return 0;
        }
        synchronized (bucket) {
            final int removed = bucket.size();
            totalEntries.addAndGet(-removed);
            return removed;
        }
    }

    @Override
    public int evictTenant(final String apiKeyId) {
        final Iterator<Map.Entry<String, Deque<SemanticCacheEntry>>> it = buckets.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            final Map.Entry<String, Deque<SemanticCacheEntry>> entry = it.next();
            if (!SemanticCacheScope.belongsToTenant(entry.getKey(), apiKeyId)) {
                continue;
            }
            synchronized (entry.getValue()) {
                removed += entry.getValue().size();
                totalEntries.addAndGet(-entry.getValue().size());
            }
            it.remove();
        }
        return removed;
    }

    @Override
    public int size() {
        return Math.max(0, totalEntries.get());
    }

    private void purgeExpired(final Deque<SemanticCacheEntry> bucket, final long now) {
        final Iterator<SemanticCacheEntry> it = bucket.iterator();
        while (it.hasNext()) {
            if (it.next().expireAt() <= now) {
                it.remove();
                totalEntries.decrementAndGet();
            }
        }
    }

    private void evictIfOverGlobalLimit() {
        final int limit = Math.max(1, properties.getGlobalMaxEntries());
        while (totalEntries.get() > limit) {
            Deque<SemanticCacheEntry> oldestBucket = null;
            String oldestScope = null;
            for (final Map.Entry<String, Deque<SemanticCacheEntry>> entry : buckets.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    oldestBucket = entry.getValue();
                    oldestScope = entry.getKey();
                    break;
                }
            }
            if (oldestBucket == null) {
                totalEntries.set(0);
                return;
            }
            synchronized (oldestBucket) {
                if (oldestBucket.isEmpty()) {
                    buckets.remove(oldestScope, oldestBucket);
                    continue;
                }
                oldestBucket.removeFirst();
                totalEntries.decrementAndGet();
            }
        }
    }
}

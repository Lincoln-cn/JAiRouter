package org.unreal.modelrouter.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 轻量级过期缓存
 *
 * 提供类似 Caffeine 的过期清理功能，但无需额外依赖。
 * 用于客户端IP限流器缓存、会话缓存等场景。
 *
 * 特点：
 * - 基于时间的自动过期
 * - 后台定时清理过期条目
 * - 并发安全
 * - 支持最大容量限制
 *
 * @author JAiRouter Team
 * @since 2.7.3
 */
public class SimpleExpiringCache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleExpiringCache.class);

    /**
     * 缓存条目
     */
    private static class CacheEntry<V> {
        final V value;
        final long expireTime;
        volatile long lastAccessTime;

        CacheEntry(V value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<K, CacheEntry<V>> cache;
    private final long expireAfterMs;
    private final int maxSize;
    private final ScheduledExecutorService cleanupExecutor;
    private final String cacheName;

    // 统计
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    /**
     * 创建过期缓存
     *
     * @param cacheName 缓存名称（用于日志）
     * @param expireAfterMs 过期时间（毫秒）
     * @param maxSize 最大容量（超过则 LRU 淘汰）
     * @param cleanupIntervalMs 清理间隔（毫秒）
     */
    public SimpleExpiringCache(final String cacheName, final long expireAfterMs,
                               final int maxSize, final long cleanupIntervalMs) {
        this.cacheName = cacheName;
        this.expireAfterMs = expireAfterMs;
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize > 0 ? maxSize : 16);

        // 启动后台清理任务
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-cleanup-" + cacheName);
            t.setDaemon(true);
            return t;
        });

        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredEntries,
                cleanupIntervalMs,
                cleanupIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("创建过期缓存: name={}, expireAfterMs={}, maxSize={}, cleanupIntervalMs={}",
                cacheName, expireAfterMs, maxSize, cleanupIntervalMs);
    }

    /**
     * 使用默认配置创建
     *
     * @param cacheName 缓存名称
     * @param expireAfterMs 过期时间（毫秒）
     */
    public SimpleExpiringCache(final String cacheName, final long expireAfterMs) {
        this(cacheName, expireAfterMs, 10000, 60000);
    }

    /**
     * 获取缓存值
     *
     * @param key 键
     * @return 值，不存在或过期返回 null
     */
    public V get(final K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key, entry);
            missCount.incrementAndGet();
            evictionCount.incrementAndGet();
            return null;
        }

        entry.touch();
        hitCount.incrementAndGet();
        return entry.value;
    }

    /**
     * 获取缓存值，不存在则通过 loader 加载
     *
     * @param key 键
     * @param loader 加载函数
     * @return 值
     */
    public V get(final K key, final Function<K, V> loader) {
        CacheEntry<V> entry = cache.computeIfAbsent(key, k -> {
            V value = loader.apply(k);
            if (value == null) {
                return null;
            }
            return new CacheEntry<>(value, System.currentTimeMillis() + expireAfterMs);
        });

        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }

        if (entry.isExpired()) {
            // 重新加载
            V value = loader.apply(key);
            if (value == null) {
                cache.remove(key);
                missCount.incrementAndGet();
                return null;
            }
            entry = new CacheEntry<>(value, System.currentTimeMillis() + expireAfterMs);
            cache.put(key, entry);
        }

        entry.touch();
        hitCount.incrementAndGet();
        return entry.value;
    }

    /**
     * 放入缓存
     *
     * @param key 键
     * @param value 值
     */
    public void put(final K key, final V value) {
        if (value == null) {
            cache.remove(key);
            return;
        }

        // 容量检查
        if (maxSize > 0 && cache.size() >= maxSize) {
            evictLruEntries();
        }

        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + expireAfterMs));
    }

    /**
     * 放入缓存，指定过期时间
     *
     * @param key 键
     * @param value 值
     * @param customExpireMs 自定义过期时间（毫秒）
     */
    public void put(final K key, final V value, final long customExpireMs) {
        if (value == null) {
            cache.remove(key);
            return;
        }

        // 容量检查
        if (maxSize > 0 && cache.size() >= maxSize) {
            evictLruEntries();
        }

        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + customExpireMs));
    }

    /**
     * 移除缓存
     *
     * @param key 键
     */
    public void remove(final K key) {
        cache.remove(key);
    }

    /**
     * 检查是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public boolean containsKey(final K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return false;
        }
        return true;
    }

    /**
     * 获取缓存大小
     *
     * @return 大小
     */
    public int size() {
        return cache.size();
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
    }

    /**
     * 关闭缓存
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("缓存已关闭: name={}", cacheName);
    }

    /**
     * 获取统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
                cacheName,
                cache.size(),
                hitCount.get(),
                missCount.get(),
                evictionCount.get()
        );
    }

    /**
     * 清理过期条目
     */
    private void cleanupExpiredEntries() {
        int removed = 0;
        Iterator<Map.Entry<K, CacheEntry<V>>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, CacheEntry<V>> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            evictionCount.addAndGet(removed);
            logger.debug("缓存清理: name={}, removed={}", cacheName, removed);
        }
    }

    /**
     * LRU 淘汰
     */
    private void evictLruEntries() {
        // 淘汰 10% 最少使用的条目
        int toEvict = Math.max(1, maxSize / 10);

        cache.entrySet().stream()
                .sorted((a, b) -> Long.compare(a.getValue().lastAccessTime, b.getValue().lastAccessTime))
                .limit(toEvict)
                .forEach(entry -> {
                    cache.remove(entry.getKey(), entry.getValue());
                    evictionCount.incrementAndGet();
                });
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final String cacheName;
        private final int size;
        private final long hitCount;
        private final long missCount;
        private final long evictionCount;

        public CacheStats(final String cacheName, final int size,
                         final long hitCount, final long missCount, final long evictionCount) {
            this.cacheName = cacheName;
            this.size = size;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
        }

        public String getCacheName() { return cacheName; }
        public int getSize() { return size; }
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public long getEvictionCount() { return evictionCount; }

        public double getHitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{name=%s, size=%d, hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d}",
                    cacheName, size, hitCount, missCount, getHitRate() * 100, evictionCount);
        }
    }
}

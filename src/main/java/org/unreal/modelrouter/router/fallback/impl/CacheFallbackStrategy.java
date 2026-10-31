/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.router.fallback.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 基于缓存的降级策略：同一个请求成功过、后来上游不可用时，回放上一次的成功响应.
 *
 * <p>#823：缓存必须按请求键隔离。修改前 {@code fallback(Exception)} 完全忽略请求，
 * 直接返回 {@code cache.values().iterator().next()} —— 一旦真的接入缓存，就会把
 * 别的调用方的响应发给当前调用方。现在的语义是：</p>
 *
 * <ul>
 *   <li>键为 {@code null}（请求体不可序列化）⇒ 不缓存、不命中，直接 503；</li>
 *   <li>键命中 ⇒ 回放该条响应，并加上 {@code X-Fallback: cache} 响应头，命中计数 +1；</li>
 *   <li>键未命中 ⇒ 503 + 结构化 JSON 错误体。</li>
 * </ul>
 *
 * @author JAiRouter Team
 */
public class CacheFallbackStrategy implements FallbackStrategy<ResponseEntity<?>> {

    private static final Logger logger = LoggerFactory.getLogger(CacheFallbackStrategy.class);

    /** 未显式配置 TTL 时的默认值，与 {@code FallbackConfig#cacheTtl} 的默认值一致. */
    public static final long DEFAULT_TTL_MILLIS = 300_000L;

    private final String serviceType;

    private final int maxCacheSize;

    /** 条目存活时间；{@code <= 0} 表示永不过期（仅在显式配置时出现）. */
    private final long ttlMillis;

    private final LongSupplier clock;

    private final ConcurrentMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    /**
     * 写入顺序（#890）：淘汰时按它挑最早的条目，而不是从 hash 表里随便挑一个.
     *
     * <p>并发下顺序表可能落后于缓存（条目已被覆盖或删除），淘汰循环会跳过这种空条目，
     * 顺序表耗尽时退回「任意一条」，保证容量一定收敛。</p>
     */
    private final Queue<String> insertionOrder = new ConcurrentLinkedQueue<>();

    private final AtomicLong hits = new AtomicLong();

    private final AtomicLong expired = new AtomicLong();

    public CacheFallbackStrategy(final String serviceType, final int maxCacheSize) {
        this(serviceType, maxCacheSize, DEFAULT_TTL_MILLIS);
    }

    public CacheFallbackStrategy(final String serviceType, final int maxCacheSize, final long ttlMillis) {
        this(serviceType, maxCacheSize, ttlMillis, System::currentTimeMillis);
    }

    CacheFallbackStrategy(final String serviceType, final int maxCacheSize, final long ttlMillis,
                          final LongSupplier clock) {
        this.serviceType = serviceType;
        this.maxCacheSize = maxCacheSize;
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /** 一条缓存的成功响应与其写入时间. */
    private record CachedResponse(ResponseEntity<?> response, long cachedAtMillis) {
    }

    @Override
    public ResponseEntity<?> fallback(final FallbackRequestKey key, final Exception cause) {
        logger.warn("Service {} fallback triggered due to: {} (key={})",
                serviceType, cause == null ? null : cause.getMessage(), key);

        final CachedResponse entry = key == null ? null : cache.get(key.asCacheKey());
        if (entry != null && isExpired(entry)) {
            cache.remove(key.asCacheKey());
            expired.incrementAndGet();
            logger.info("缓存的降级响应已过期，按未命中处理: service={}, key={}, ttl={}ms",
                    serviceType, key, ttlMillis);
        }
        final ResponseEntity<?> cached = entry == null || isExpired(entry) ? null : entry.response();
        if (cached == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(DefaultFallbackStrategy.degradedBody());
        }
        hits.incrementAndGet();
        logger.info("Returning cached response for service {} (key={})", serviceType, key);
        return ResponseEntity.status(cached.getStatusCode())
                .headers(headers -> {
                    headers.putAll(cached.getHeaders());
                    headers.set(FallbackStrategy.FALLBACK_HEADER, FallbackStrategy.FALLBACK_HEADER_CACHE_VALUE);
                })
                .body(cached.getBody());
    }

    /**
     * 缓存一次成功响应.
     *
     * <p>只缓存 2xx 且带响应体的结果；键为 {@code null} 时直接跳过（该请求不参与降级缓存）。</p>
     *
     * @param key      请求键
     * @param response 成功响应
     */
    public void cacheResponse(final FallbackRequestKey key, final ResponseEntity<?> response) {
        // #890：容量配成 0 或负数时的语义是「关闭降级缓存」，此前会被当成「不设上限」而不限增长
        if (maxCacheSize <= 0) {
            return;
        }
        if (key == null || response == null || response.getBody() == null
                || !response.getStatusCode().is2xxSuccessful()) {
            return;
        }
        final String cacheKey = key.asCacheKey();
        cache.put(cacheKey, new CachedResponse(response, clock.getAsLong()));
        insertionOrder.add(cacheKey);
        evictIfNeeded();
    }

    /**
     * 当前是否真正启用了缓存（容量 &gt; 0 且 TTL 未显式关闭）.
     *
     * <p>#890：{@code cache-size: 0} 表示关闭缓存，此时 {@link #cacheResponse} 不写入任何条目。</p>
     *
     * @return 是否启用
     */
    public boolean isCacheEnabled() {
        return maxCacheSize > 0;
    }

    /**
     * 已缓存条目数（测试与指标用）.
     *
     * @return 条目数
     */
    public int getCachedCount() {
        return cache.size();
    }

    /**
     * 降级缓存命中次数.
     *
     * @return 累计命中次数
     */
    public long getHitCount() {
        return hits.get();
    }

    /**
     * 因过期而被丢弃的条目数（测试与指标用）.
     *
     * @return 累计过期条数
     */
    public long getExpiredCount() {
        return expired.get();
    }

    /**
     * 当前生效的条目存活时间（毫秒）.
     *
     * @return TTL；{@code <= 0} 表示永不过期
     */
    public long getTtlMillis() {
        return ttlMillis;
    }

    /**
     * 清空缓存（配置热更新 / 测试用）.
     */
    public void clear() {
        cache.clear();
        insertionOrder.clear();
    }

    private boolean isExpired(final CachedResponse entry) {
        return ttlMillis > 0 && clock.getAsLong() - entry.cachedAtMillis() > ttlMillis;
    }

    /**
     * 超出容量时按写入顺序淘汰最早的条目（#890）.
     *
     * <p>修改前用 {@code cache.keySet().iterator().next()} 当作「最早」，ConcurrentHashMap 的遍历顺序
     * 与写入顺序无关，因此淘汰的可能是任意条目；而且容量为 0/负数时直接 return，等于关闭上限。</p>
     */
    private void evictIfNeeded() {
        while (cache.size() > maxCacheSize) {
            final String evicted = insertionOrder.poll();
            if (evicted != null) {
                cache.remove(evicted);
                continue;
            }
            // 顺序表已用完（条目由更早的实例写入）时退回任意一条，保证不会死循环
            final String fallbackKey = cache.keySet().stream().findFirst().orElse(null);
            if (fallbackKey == null) {
                return;
            }
            cache.remove(fallbackKey);
        }
    }
}

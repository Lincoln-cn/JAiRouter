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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

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


    private final String serviceType;

    private final int maxCacheSize;

    private final ConcurrentMap<String, ResponseEntity<?>> cache = new ConcurrentHashMap<>();

    private final AtomicLong hits = new AtomicLong();

    public CacheFallbackStrategy(final String serviceType, final int maxCacheSize) {
        this.serviceType = serviceType;
        this.maxCacheSize = maxCacheSize;
    }

    @Override
    public ResponseEntity<?> fallback(final FallbackRequestKey key, final Exception cause) {
        logger.warn("Service {} fallback triggered due to: {} (key={})",
                serviceType, cause == null ? null : cause.getMessage(), key);

        final ResponseEntity<?> cached = key == null ? null : cache.get(key.asCacheKey());
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
        if (key == null || response == null || response.getBody() == null
                || !response.getStatusCode().is2xxSuccessful()) {
            return;
        }
        evictIfFull();
        cache.put(key.asCacheKey(), response);
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
     * 清空缓存（配置热更新 / 测试用）.
     */
    public void clear() {
        cache.clear();
    }

    private void evictIfFull() {
        if (maxCacheSize <= 0) {
            return;
        }
        while (cache.size() >= maxCacheSize) {
            final String oldest = cache.keySet().iterator().next();
            cache.remove(oldest);
        }
    }
}

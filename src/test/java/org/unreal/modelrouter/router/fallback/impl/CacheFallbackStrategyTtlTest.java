package org.unreal.modelrouter.router.fallback.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #872：降级缓存的 cache-ttl 必须真的生效。
 *
 * <p>修改前 {@code FallbackConfig#cacheTtl}（默认 300000，rerank 在 services.yml 里显式配 600000）
 * 没有任何读取方，条目永不过期；这里用可控时钟验证「过期即按未命中处理」与「TTL 从配置接进策略」。
 */
class CacheFallbackStrategyTtlTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long TTL_MILLIS = 1_000L;

    private FallbackRequestKey key(final String body) {
        return FallbackRequestKey.of("chat", "qwen3:4b", "key-1",
                Map.of("messages", List.of(Map.of("role", "user", "content", body))), MAPPER);
    }

    private ResponseEntity<?> ok() {
        return ResponseEntity.ok(Map.of("choices", List.of()));
    }

    @Test
    @DisplayName("872-001 TTL 内命中，超过 TTL 后按未命中处理并清掉条目")
    void entryExpiresAfterTtl() {
        final AtomicLong now = new AtomicLong(0L);
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 10, TTL_MILLIS, now::get);
        final FallbackRequestKey requestKey = key("hello");

        strategy.cacheResponse(requestKey, ok());
        assertEquals(1, strategy.getCachedCount(), "写入后应有一条缓存");

        now.set(TTL_MILLIS - 1);
        final ResponseEntity<?> hit = strategy.fallback(requestKey, new RuntimeException("upstream down"));
        assertEquals(HttpStatus.OK, hit.getStatusCode(), "TTL 内应命中缓存");
        assertEquals(FallbackStrategy.FALLBACK_HEADER_CACHE_VALUE,
                hit.getHeaders().getFirst(FallbackStrategy.FALLBACK_HEADER), "命中要带 X-Fallback 头");
        assertEquals(1L, strategy.getHitCount(), "命中计数应为 1");

        now.set(TTL_MILLIS + 1);
        final ResponseEntity<?> miss = strategy.fallback(requestKey, new RuntimeException("upstream down"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, miss.getStatusCode(), "超过 TTL 应按未命中处理");
        assertEquals(0, strategy.getCachedCount(), "过期条目应被清掉");
        assertEquals(1L, strategy.getExpiredCount(), "过期计数应为 1");
        assertNull(miss.getHeaders().getFirst(FallbackStrategy.FALLBACK_HEADER), "未命中不应带 X-Fallback 头");
    }

    @Test
    @DisplayName("872-002 TTL 为 0 时表示永不过期（显式配置才出现）")
    void zeroTtlMeansNeverExpire() {
        final AtomicLong now = new AtomicLong(0L);
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 10, 0L, now::get);
        final FallbackRequestKey requestKey = key("world");

        strategy.cacheResponse(requestKey, ok());
        now.set(10_000_000L);
        assertEquals(HttpStatus.OK, strategy.fallback(requestKey, new RuntimeException("down")).getStatusCode(),
                "TTL=0 表示永不过期");
    }

    @Test
    @DisplayName("872-003 工厂把 FallbackConfig 的 cache-ttl 接进策略")
    void factoryWiresConfiguredTtl() {
        final org.unreal.modelrouter.router.factory.ComponentFactory factory =
                new org.unreal.modelrouter.router.factory.ComponentFactory(
                        new org.unreal.modelrouter.config.core.ConfigurationValidator(), null);
        final org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig config =
                new org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig();
        config.setEnabled(true);
        config.setStrategy("cache");
        config.setCacheSize(50);
        config.setCacheTtl(600_000L);

        final FallbackStrategy<ResponseEntity<?>> strategy = factory.createFallbackStrategy(config, "rerank");
        assertTrue(strategy instanceof CacheFallbackStrategy, "strategy=cache 应创建 CacheFallbackStrategy");
        assertEquals(600_000L, ((CacheFallbackStrategy) strategy).getTtlMillis(),
                "配置里的 cache-ttl 必须原样传给策略（此前完全没有消费方）");

        config.setCacheTtl(null);
        final FallbackStrategy<ResponseEntity<?>> defaulted = factory.createFallbackStrategy(config, "rerank");
        assertEquals(CacheFallbackStrategy.DEFAULT_TTL_MILLIS,
                ((CacheFallbackStrategy) defaulted).getTtlMillis(), "未配置时应回落到默认 TTL");
    }
}

package org.unreal.modelrouter.router.fallback.impl;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.factory.ComponentFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #890：降级缓存的容量语义必须可预期。
 *
 * <p>修改前有两处问题：① {@code cache-size: 0}（或负数）本意是「关掉缓存」，代码却直接跳过淘汰，
 * 等于「不设上限」；② 淘汰时用 {@code cache.keySet().iterator().next()} 当成「最早」，
 * 而 ConcurrentHashMap 的遍历顺序与写入顺序无关，被淘汰的可能是任意条目。</p>
 */
class CacheFallbackStrategyCapacityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FallbackRequestKey key(final String body) {
        return FallbackRequestKey.of("chat", "qwen3:4b", "key-1",
                Map.of("messages", List.of(Map.of("role", "user", "content", body))), MAPPER);
    }

    private ResponseEntity<?> ok(final String body) {
        return ResponseEntity.ok(Map.of("body", body));
    }

    @Test
    @DisplayName("890-001 容量为 0 表示关闭降级缓存：不写入任何条目")
    void zeroCapacityDisablesCache() {
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 0);

        strategy.cacheResponse(key("hello"), ok("hello"));

        assertEquals(0, strategy.getCachedCount(), "容量为 0 时不应缓存任何条目");
        assertFalse(strategy.isCacheEnabled(), "容量为 0 应被视为未启用缓存");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                strategy.fallback(key("hello"), new RuntimeException("upstream down")).getStatusCode(),
                "未启用缓存时应按未命中返回 503");
    }

    @Test
    @DisplayName("890-002 超出容量时淘汰最早写入的条目，保留较新的条目")
    void evictsOldestEntryWhenFull() {
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 2);
        assertTrue(strategy.isCacheEnabled(), "容量为 2 应被视为启用缓存");

        strategy.cacheResponse(key("first"), ok("first"));
        strategy.cacheResponse(key("second"), ok("second"));
        strategy.cacheResponse(key("third"), ok("third"));

        assertEquals(2, strategy.getCachedCount(), "容量上限必须守住");

        final ResponseEntity<?> last = strategy.fallback(key("third"), new RuntimeException("down"));
        assertEquals(HttpStatus.OK, last.getStatusCode(), "最后写入的条目必须还在");
        assertEquals("third", ((Map<?, ?>) last.getBody()).get("body"), "回放的应是自己的响应");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                strategy.fallback(key("first"), new RuntimeException("down")).getStatusCode(),
                "最早写入的条目应被淘汰（修改前淘汰的是任意条目）");

        final ResponseEntity<?> second = strategy.fallback(key("second"), new RuntimeException("down"));
        assertEquals(HttpStatus.OK, second.getStatusCode(), "次早的条目应仍在缓存里");
    }

    @Test
    @DisplayName("890-003 重复写入同一个键不额外占用容量")
    void rewritingSameKeyDoesNotConsumeExtraSlots() {
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 2);

        strategy.cacheResponse(key("same"), ok("v1"));
        strategy.cacheResponse(key("same"), ok("v2"));
        strategy.cacheResponse(key("other"), ok("other"));

        assertEquals(2, strategy.getCachedCount(), "同一个键重复写入只应占一个名额");
        final ResponseEntity<?> hit = strategy.fallback(key("same"), new RuntimeException("down"));
        assertEquals("v2", ((Map<?, ?>) hit.getBody()).get("body"), "应回放最后一次写入的内容");
    }

    @Test
    @DisplayName("890-004 配置缺省的策略字段不抛异常，按默认策略 / 默认容量处理")
    void componentFactoryToleratesNullOrBlankFallbackFields() {
        final ComponentFactory factory = new ComponentFactory(null, null);

        final org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig noStrategy =
                new org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig();
        noStrategy.setEnabled(true);
        noStrategy.setStrategy(null);
        assertNotNull(factory.createFallbackStrategy(noStrategy, "chat"),
                "strategy 为 null 时应回落默认策略，而不是在降级路径上抛 NPE");

        final org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig blankStrategy =
                new org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig();
        blankStrategy.setEnabled(true);
        blankStrategy.setStrategy("   ");
        assertNotNull(factory.createFallbackStrategy(blankStrategy, "chat"), "空白 strategy 同样应回落默认策略");

        final org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig noCacheSize =
                new org.unreal.modelrouter.router.model.ModelRouterProperties.FallbackConfig();
        noCacheSize.setEnabled(true);
        noCacheSize.setStrategy("cache");
        noCacheSize.setCacheSize(null);
        final var created = factory.createFallbackStrategy(noCacheSize, "chat");
        assertTrue(created instanceof CacheFallbackStrategy, "strategy=cache 应创建缓存策略");
        final CacheFallbackStrategy cacheStrategy = (CacheFallbackStrategy) created;
        assertTrue(cacheStrategy.isCacheEnabled(), "cache-size 缺省时应回落默认容量而不是关闭缓存");
        assertTrue(ComponentFactory.DEFAULT_FALLBACK_CACHE_SIZE > 0, "默认容量常量必须为正数");
    }
}

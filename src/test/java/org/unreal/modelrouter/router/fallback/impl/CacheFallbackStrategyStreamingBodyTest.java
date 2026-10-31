package org.unreal.modelrouter.router.fallback.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * #892：流式响应不能进降级缓存。
 *
 * <p>流式响应的 body 是 {@code Flux<DataBuffer>}，只能被订阅一次；缓存它以后，降级命中时
 * 客户端会拿到 {@code 200} + 空流（比 {@code 503} 更糟）。这里守两件事：写入侧拒收流式体，
 * 读取侧把历史遗留的流式条目按未命中处理并清掉。</p>
 */
class CacheFallbackStrategyStreamingBodyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FallbackRequestKey key(final String body) {
        return FallbackRequestKey.of("chat", "qwen3:4b", "key-1",
                Map.of("messages", List.of(Map.of("role", "user", "content", body)), "stream", true), MAPPER);
    }

    @Test
    @DisplayName("892-001 流式体的成功响应不写入降级缓存，降级时返回 503")
    void streamingResponseIsNotCached() {
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 10);
        final ResponseEntity<?> streaming = ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(Flux.just("data: {\"delta\":\"hi\"}"));

        strategy.cacheResponse(key("hi"), streaming);

        assertEquals(0, strategy.getCachedCount(), "流式响应不应进入降级缓存");
        final ResponseEntity<?> fallback = strategy.fallback(key("hi"), new RuntimeException("upstream down"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, fallback.getStatusCode(),
                "没有可回放的条目时必须给出 503，而不是 200 空流");
    }

    @Test
    @DisplayName("892-002 普通 JSON 响应仍能命中（回归保护）")
    void jsonResponseIsStillCached() {
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 10);
        strategy.cacheResponse(key("hi"), ResponseEntity.ok(Map.of("choices", List.of())));

        assertEquals(1, strategy.getCachedCount(), "非流式响应应正常缓存");
        final ResponseEntity<?> hit = strategy.fallback(key("hi"), new RuntimeException("down"));
        assertEquals(HttpStatus.OK, hit.getStatusCode(), "应命中缓存");
        assertEquals(FallbackStrategy.FALLBACK_HEADER_CACHE_VALUE,
                hit.getHeaders().getFirst(FallbackStrategy.FALLBACK_HEADER), "命中要带 X-Fallback 头");
    }

    @Test
    @DisplayName("892-003 历史遗留的流式条目按未命中处理并清掉")
    @SuppressWarnings("unchecked")
    void legacyStreamingEntryIsDropped() throws Exception {
        final CacheFallbackStrategy strategy = new CacheFallbackStrategy("chat", 10);
        final FallbackRequestKey requestKey = key("hi");

        // 直接往内部缓存塞一条流式条目，模拟修复前写入的数据
        final Field cacheField = CacheFallbackStrategy.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        final ConcurrentMap<String, Object> cache = (ConcurrentMap<String, Object>) cacheField.get(strategy);
        final Class<?> entryClass = Class.forName(
                "org.unreal.modelrouter.router.fallback.impl.CacheFallbackStrategy$CachedResponse");
        final Constructor<?> constructor = entryClass.getDeclaredConstructor(ResponseEntity.class, long.class);
        constructor.setAccessible(true);
        // 用当前时间写入，避免条目因 TTL 过期而被清掉 —— 这里要验证的是「流式体不可回放」，
        // 而不是「过期被丢弃」
        cache.put(requestKey.asCacheKey(), constructor.newInstance(
                ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(Flux.just("data: x")),
                System.currentTimeMillis()));
        assertNotNull(cache.get(requestKey.asCacheKey()), "前置条件：缓存里确实有一条流式条目");

        final ResponseEntity<?> fallback = strategy.fallback(requestKey, new RuntimeException("down"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, fallback.getStatusCode(),
                "流式条目无法回放，必须按未命中处理");
        assertEquals(0, strategy.getCachedCount(), "遗留的流式条目应被清掉，避免一直占位");
    }
}

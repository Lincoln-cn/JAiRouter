package org.unreal.modelrouter.router.fallback.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #823：降级缓存必须按请求隔离，并且命中时给出可观测的标记。
 *
 * <p>修改前 {@code fallback(Exception)} 忽略请求，直接取 {@code cache.values().iterator().next()}；
 * 这些用例在旧实现下会因为「不同请求互相命中」与「缺少 X-Fallback 头」而变红。</p>
 */
@DisplayName("#823 降级缓存按请求键隔离")
class CacheFallbackStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CacheFallbackStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CacheFallbackStrategy("chat", 10);
    }

    @Test
    @DisplayName("823-001 没有缓存时返回 503 与结构化 JSON 错误体")
    void noCacheReturnsStructuredServiceUnavailable() {
        final ResponseEntity<?> response = strategy.fallback(
                FallbackRequestKey.of("chat", "gpt-4", "key-a", request("hello"), objectMapper),
                new RuntimeException("upstream down"));

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        final String body = String.valueOf(response.getBody());
        assertTrue(body.contains("\"type\":\"service_unavailable\""), body);
        assertTrue(body.contains("\"code\":\"503\""), body);
    }

    @Test
    @DisplayName("823-002 同键命中：回放响应体并带上 X-Fallback: cache")
    void sameKeyHitsAndMarksResponse() {
        final FallbackRequestKey key = key("key-a", request("hello"));
        strategy.cacheResponse(key, ResponseEntity.ok("cached data"));

        final ResponseEntity<?> response = strategy.fallback(key, new RuntimeException("upstream down"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("cached data", response.getBody());
        assertEquals(FallbackStrategy.FALLBACK_HEADER_CACHE_VALUE,
                response.getHeaders().getFirst(FallbackStrategy.FALLBACK_HEADER));
        assertEquals(1L, strategy.getHitCount());
    }

    @Test
    @DisplayName("823-003 不同请求体不互相命中")
    void differentRequestBodyDoesNotHit() {
        strategy.cacheResponse(key("key-a", request("hello")), ResponseEntity.ok("answer-for-hello"));

        final ResponseEntity<?> response = strategy.fallback(
                key("key-a", request("something else")), new RuntimeException("upstream down"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode(),
                "别的请求的缓存响应不能发给当前请求");
        assertEquals(0L, strategy.getHitCount());
    }

    @Test
    @DisplayName("823-004 不同调用方不互相命中")
    void differentCallerDoesNotHit() {
        strategy.cacheResponse(key("key-a", request("hello")), ResponseEntity.ok("answer-for-key-a"));

        final ResponseEntity<?> response = strategy.fallback(
                key("key-b", request("hello")), new RuntimeException("upstream down"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode(),
                "跨租户复用降级响应等于泄漏别人拿到的答案");
    }

    @Test
    @DisplayName("823-005 键为 null 时既不缓存也不命中")
    void nullKeyIsNeverCachedNorHit() {
        strategy.cacheResponse(null, ResponseEntity.ok("should not be cached"));
        assertEquals(0, strategy.getCachedCount());

        final FallbackRequestKey key = key("key-a", request("hello"));
        strategy.cacheResponse(key, ResponseEntity.ok("cached"));

        final ResponseEntity<?> response = strategy.fallback(null, new RuntimeException("upstream down"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(0L, strategy.getHitCount());
    }

    @Test
    @DisplayName("823-006 超过容量时按写入顺序淘汰最旧条目")
    void evictsOldestWhenFull() {
        final CacheFallbackStrategy small = new CacheFallbackStrategy("chat", 2);
        small.cacheResponse(key("key-a", request("one")), ResponseEntity.ok("v1"));
        small.cacheResponse(key("key-b", request("two")), ResponseEntity.ok("v2"));
        small.cacheResponse(key("key-c", request("three")), ResponseEntity.ok("v3"));

        assertEquals(2, small.getCachedCount());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                small.fallback(key("key-a", request("one")), new RuntimeException("down")).getStatusCode(),
                "最旧条目应已被淘汰");
        assertEquals(HttpStatus.OK,
                small.fallback(key("key-c", request("three")), new RuntimeException("down")).getStatusCode());
    }

    private FallbackRequestKey key(final String callerId, final Object request) {
        return FallbackRequestKey.of("chat", "gpt-4", callerId, request, objectMapper);
    }

    private Map<String, Object> request(final String content) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        return body;
    }
}

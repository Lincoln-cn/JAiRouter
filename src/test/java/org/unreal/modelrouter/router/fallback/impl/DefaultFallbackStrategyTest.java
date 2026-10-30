package org.unreal.modelrouter.router.fallback.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultFallbackStrategy 单元测试。
 *
 * <p>#823 起 {@code fallback} 需要请求键（键为 null 表示该请求不参与降级缓存），
 * 错误体改为结构化 JSON，便于客户端按 {@code type}/{@code code} 分支处理。</p>
 */
@DisplayName("DefaultFallbackStrategy 测试")
class DefaultFallbackStrategyTest {

    private DefaultFallbackStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DefaultFallbackStrategy("chat");
    }

    @Test
    @DisplayName("测试 1: 降级返回 503 状态码")
    void testFallbackReturns503() {
        final ResponseEntity<?> response = strategy.fallback(null, new RuntimeException("Service unavailable"));

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 2: 降级响应包含正确的 Content-Type")
    void testFallbackResponseContentType() {
        final ResponseEntity<?> response = strategy.fallback(null, new RuntimeException("Service unavailable"));

        assertNotNull(response.getHeaders().get("Content-Type"));
        assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
    }

    @Test
    @DisplayName("测试 3: 降级响应是结构化 JSON 错误体")
    void testFallbackResponseBody() {
        final ResponseEntity<?> response = strategy.fallback(null, new RuntimeException("Service unavailable"));

        assertNotNull(response.getBody());
        final String body = response.getBody().toString();
        assertEquals(DefaultFallbackStrategy.degradedBody(), body);
        assertTrue(body.contains("\"type\":\"service_unavailable\""), body);
        assertTrue(body.contains("\"code\":\"503\""), body);
    }

    @Test
    @DisplayName("测试 4: 不同服务类型的降级")
    void testDifferentServiceTypes() {
        final DefaultFallbackStrategy chatStrategy = new DefaultFallbackStrategy("chat");
        final DefaultFallbackStrategy embeddingStrategy = new DefaultFallbackStrategy("embedding");

        final ResponseEntity<?> chatResponse = chatStrategy.fallback(null, new RuntimeException("Error"));
        final ResponseEntity<?> embeddingResponse = embeddingStrategy.fallback(null, new RuntimeException("Error"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, chatResponse.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, embeddingResponse.getStatusCode());
    }

    @Test
    @DisplayName("测试 5: 空异常消息的降级")
    void testFallbackWithNullExceptionMessage() {
        final ResponseEntity<?> response = strategy.fallback(null, new RuntimeException());

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 6: 多次调用降级策略")
    void testMultipleFallbackCalls() {
        for (int i = 0; i < 5; i++) {
            final ResponseEntity<?> response = strategy.fallback(null, new RuntimeException("Error " + i));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        }
    }
}

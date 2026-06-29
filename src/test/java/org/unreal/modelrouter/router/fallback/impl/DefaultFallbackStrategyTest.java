package org.unreal.modelrouter.router.fallback.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultFallbackStrategy 单元测试
 *
 * 测试内容：
 * 1. 降级响应测试
 * 2. 响应状态码测试
 * 3. 响应内容测试
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
        ResponseEntity<?> response = strategy.fallback(new RuntimeException("Service unavailable"));

        assertNotNull(response);
        assertEquals(503, response.getStatusCodeValue());
    }

    @Test
    @DisplayName("测试 2: 降级响应包含正确的 Content-Type")
    void testFallbackResponseContentType() {
        ResponseEntity<?> response = strategy.fallback(new RuntimeException("Service unavailable"));

        assertNotNull(response.getHeaders().get("Content-Type"));
        assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
    }

    @Test
    @DisplayName("测试 3: 降级响应包含降级消息")
    void testFallbackResponseBody() {
        ResponseEntity<?> response = strategy.fallback(new RuntimeException("Service unavailable"));

        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("degraded"));
    }

    @Test
    @DisplayName("测试 4: 不同服务类型的降级")
    void testDifferentServiceTypes() {
        DefaultFallbackStrategy chatStrategy = new DefaultFallbackStrategy("chat");
        DefaultFallbackStrategy embeddingStrategy = new DefaultFallbackStrategy("embedding");

        ResponseEntity<?> chatResponse = chatStrategy.fallback(new RuntimeException("Error"));
        ResponseEntity<?> embeddingResponse = embeddingStrategy.fallback(new RuntimeException("Error"));

        assertEquals(503, chatResponse.getStatusCodeValue());
        assertEquals(503, embeddingResponse.getStatusCodeValue());
    }

    @Test
    @DisplayName("测试 5: 空异常消息的降级")
    void testFallbackWithNullExceptionMessage() {
        ResponseEntity<?> response = strategy.fallback(new RuntimeException());

        assertNotNull(response);
        assertEquals(503, response.getStatusCodeValue());
    }

    @Test
    @DisplayName("测试 6: 多次调用降级策略")
    void testMultipleFallbackCalls() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<?> response = strategy.fallback(new RuntimeException("Error " + i));
            assertEquals(503, response.getStatusCodeValue());
        }
    }
}

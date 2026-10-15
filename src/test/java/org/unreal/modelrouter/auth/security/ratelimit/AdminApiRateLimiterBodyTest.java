package org.unreal.modelrouter.auth.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.constants.ErrorCode;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理面限流器的 429 响应体（issue #404：拆码后本路径**保留** {@code RATE_LIMIT_EXCEEDED}）.
 *
 * <p>拆码的裁定是「两条路径都给语义正确的 429，但码分开」：管理面限流器
 * （{@code /api/auth/api-keys} 前缀）不经异常，直接在过滤器里写
 * {@code {"code":"RATE_LIMIT_EXCEEDED","message":...}} 扁平体；API Key 校验链改用
 * {@code RATE_LIMITED_API_KEY}。本类把管理面这一半钉住 —— 否则「拆码」时很容易顺手把它也改掉
 * （客户端解析扁平体的分支会静默失效）。</p>
 *
 * @author JAiRouter Team
 * @since v3.3.3
 */
@DisplayName("issue #404: AdminApiRateLimiter 429 扁平体（码保留 RATE_LIMIT_EXCEEDED）")
class AdminApiRateLimiterBodyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final String ADMIN_PATH = "/api/auth/api-keys";

    @Test
    @DisplayName("RATE-006: 超限 ⇒ 429 + 扁平体 code=RATE_LIMIT_EXCEEDED，且不放行过滤器链")
    void exceeded_shouldWriteFlat429BodyWithRateLimitExceededCode() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        // 阈值调小到 0：第一次 POST 立即超限，无需打满真实的 10 次/小时
        ReflectionTestUtils.setField(limiter, "createLimitPerHour", 0);
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        final MockServerWebExchange exchange = exchangeOf(
                MockServerHttpRequest.post(ADMIN_PATH).header("X-Forwarded-For", "10.9.9.1"));
        limiter.filter(exchange, chain).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final String body = bodyText(exchange);
        assertEquals("{\"code\":\"" + ErrorCode.RATE_LIMIT_EXCEEDED.code()
                        + "\",\"message\":\"创建操作过于频繁，请稍后再试\"}",
                body, "管理面 429 的扁平体形状与码必须保持不变");
        assertTrue(body.contains(ErrorCode.RATE_LIMIT_EXCEEDED.code()));
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("RATE-007: 同一限流器对非管理面路径不生效（即便创建配额已耗尽）")
    void otherPaths_shouldPassThroughEvenWhenQuotaIsExhausted() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        ReflectionTestUtils.setField(limiter, "createLimitPerHour", 0);
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        final MockServerWebExchange exchange = exchangeOf(
                MockServerHttpRequest.post("/api/auth/jwt/login").header("X-Forwarded-For", "10.9.9.2"));
        limiter.filter(exchange, chain).block(TIMEOUT);

        assertNull(exchange.getResponse().getStatusCode(), "非管理面路径不得被写成 429");
        verify(chain).filter(any());
    }

    // ==================== 辅助方法 ====================

    private static MockServerWebExchange exchangeOf(final MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.build());
    }

    private static String bodyText(final MockServerWebExchange exchange) {
        final DataBuffer buffer = exchange.getResponse().getBody().blockLast(TIMEOUT);
        assertTrue(buffer != null, "响应体不应为空");
        final byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

package org.unreal.modelrouter.auth.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理端 API 限流读写分桶：控制台列表 GET 不应与写操作共用配额.
 */
@DisplayName("AdminApiRateLimiter: 读写分桶")
class AdminApiRateLimiterReadWriteTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static MockServerWebExchange exchange(final MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.build());
    }

    private static void assertAllowed(final MockServerWebExchange exchange) {
        assertNull(exchange.getResponse().getStatusCode(),
                "未超限时不应写入 429 状态码，实际=" + exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("GET 列表 40 次仍可通过（默认读配额 120/min，旧逻辑 30/min 会 429）")
    void manyGetListRequests_shouldNotHitMutateBucket() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange last = null;
        for (int i = 0; i < 40; i++) {
            last = exchange(MockServerHttpRequest.get("/api/auth/api-keys")
                    .header("X-Forwarded-For", "10.1.1.1"));
            limiter.filter(last, chain).block(TIMEOUT);
        }
        assertAllowed(last);
    }

    @Test
    @DisplayName("大量 GET 后仍可执行写操作（读写计数器隔离）")
    void getTraffic_shouldNotStarveMutateQuota() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 50; i++) {
            final MockServerWebExchange get = exchange(MockServerHttpRequest.get("/api/auth/api-keys")
                    .header("X-Forwarded-For", "10.1.1.2"));
            limiter.filter(get, chain).block(TIMEOUT);
        }

        final MockServerWebExchange put = exchange(MockServerHttpRequest.put("/api/auth/api-keys/k1/quota")
                .header("X-Forwarded-For", "10.1.1.2"));
        limiter.filter(put, chain).block(TIMEOUT);
        assertAllowed(put);
    }

    @Test
    @DisplayName("POST 创建第 11 次触发小时创建限额 429")
    void createLimit_shouldStillApply() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exceeded = null;
        for (int i = 0; i < 11; i++) {
            final MockServerWebExchange post = exchange(MockServerHttpRequest.post("/api/auth/api-keys")
                    .header("X-Forwarded-For", "10.1.1.3"));
            limiter.filter(post, chain).block(TIMEOUT);
            if (HttpStatus.TOO_MANY_REQUESTS.equals(post.getResponse().getStatusCode())) {
                exceeded = post;
                break;
            }
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                exceeded != null ? exceeded.getResponse().getStatusCode() : null,
                "创建操作仍应受每小时 10 次限制");
    }

    @Test
    @DisplayName("写操作超过每分钟 30 次触发 429（读流量不影响）")
    void mutateMinuteLimit_shouldApply() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exceeded = null;
        for (int i = 0; i < 31; i++) {
            // PUT 不计入 create 额外桶
            final MockServerWebExchange put = exchange(MockServerHttpRequest.put("/api/auth/api-keys/k1/quota")
                    .header("X-Forwarded-For", "10.1.1.4"));
            limiter.filter(put, chain).block(TIMEOUT);
            if (HttpStatus.TOO_MANY_REQUESTS.equals(put.getResponse().getStatusCode())) {
                exceeded = put;
                break;
            }
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                exceeded != null ? exceeded.getResponse().getStatusCode() : null,
                "写操作应受每分钟限制");
    }
}

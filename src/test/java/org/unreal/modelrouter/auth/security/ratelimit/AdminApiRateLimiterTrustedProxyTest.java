package org.unreal.modelrouter.auth.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #124: AdminApiRateLimiter 原先无条件信任 X-Forwarded-For 首跳，
 * 任意客户端轮换伪造 XFF 即可绕过管理接口限流并无界制造计数器。
 * 本测试验证：非可信代理对端忽略转发头；可信代理采用 XFF 最后一跳；
 * 伪造 XFF 不会使计数器 map 无界增长。
 */
@DisplayName("AdminApiRateLimiter: 可信代理与 XFF 防绕过")
class AdminApiRateLimiterTrustedProxyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static void setTrustedProxies(final AdminApiRateLimiter limiter, final String raw) {
        try {
            java.lang.reflect.Field field = AdminApiRateLimiter.class.getDeclaredField("trustedProxies");
            field.setAccessible(true);
            field.set(limiter, raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> counters(final AdminApiRateLimiter limiter) {
        try {
            java.lang.reflect.Field field = AdminApiRateLimiter.class.getDeclaredField("counters");
            field.setAccessible(true);
            return (Map<String, ?>) field.get(limiter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static MockServerWebExchange exchange(final MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.build());
    }

    private static void assertAllowed(final MockServerWebExchange exchange) {
        assertNull(exchange.getResponse().getStatusCode(),
                "未超限时不应写入 429 状态码，实际=" + exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("伪造 XFF 的请求仍按 remoteAddress 计数（默认不信任任何代理）")
    void forgedXffFromUntrustedPeer_mustCountAgainstRemoteAddress() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // 对端 203.0.113.10，轮换伪造 XFF 试图换新计数桶
        MockServerWebExchange exceeded = null;
        for (int i = 0; i < 31; i++) {
            final MockServerWebExchange put = exchange(MockServerHttpRequest
                    .put("/api/auth/api-keys/k1/quota")
                    .remoteAddress(new InetSocketAddress("203.0.113.10", 40000 + i))
                    .header("X-Forwarded-For", "198.51.100." + i));
            limiter.filter(put, chain).block(TIMEOUT);
            if (HttpStatus.TOO_MANY_REQUESTS.equals(put.getResponse().getStatusCode())) {
                exceeded = put;
                break;
            }
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                exceeded != null ? exceeded.getResponse().getStatusCode() : null,
                "伪造 XFF 不应绕过写操作每分钟限额");
        // 只应存在 remoteAddress 一个计数桶
        assertEquals(1, counters(limiter).size(), "伪造 XFF 不应制造额外计数桶");
    }

    @Test
    @DisplayName("可信代理请求采用 XFF 最后一跳作为限流键")
    void trustedProxy_shouldUseXffLastHop() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        setTrustedProxies(limiter, "10.0.0.1");
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // 经可信代理 10.0.0.1 转发，XFF 前缀可伪造，最后一跳是代理追加的真实客户端
        MockServerWebExchange exceeded = null;
        for (int i = 0; i < 31; i++) {
            final MockServerWebExchange put = exchange(MockServerHttpRequest
                    .put("/api/auth/api-keys/k1/quota")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 50000 + i))
                    .header("X-Forwarded-For", "spoofed-" + i + ", 198.51.100.7"));
            limiter.filter(put, chain).block(TIMEOUT);
            if (HttpStatus.TOO_MANY_REQUESTS.equals(put.getResponse().getStatusCode())) {
                exceeded = put;
                break;
            }
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                exceeded != null ? exceeded.getResponse().getStatusCode() : null,
                "同一 XFF 最后一跳应共享写操作配额并触发限额");

        final Map<String, ?> map = counters(limiter);
        assertEquals(1, map.size(), "应只按最后一跳 198.51.100.7 计数");
        assertEquals(true, map.containsKey("198.51.100.7"),
                "限流键应为 XFF 最后一跳，实际 keys=" + map.keySet());
    }

    @Test
    @DisplayName("伪造 XFF 不会使计数器 map 无界增长")
    void forgedXff_mustNotGrowCounterMapUnbounded() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 500; i++) {
            final MockServerWebExchange get = exchange(MockServerHttpRequest
                    .get("/api/auth/api-keys")
                    .remoteAddress(new InetSocketAddress("203.0.113.20", 40000 + i))
                    .header("X-Forwarded-For", "forged-" + i));
            limiter.filter(get, chain).block(TIMEOUT);
        }

        assertEquals(1, counters(limiter).size(),
                "500 个伪造 XFF 不应产生 500 个计数桶，实际=" + counters(limiter).size());
    }
}

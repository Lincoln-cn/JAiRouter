package org.unreal.modelrouter.auth.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.constants.ErrorCode;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SlaCredentialRateLimiter} 的行为测试（issue #1216）。
 */
class SlaCredentialRateLimiterTest {

    private SlaCredentialRateLimiter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SlaCredentialRateLimiter();
        ReflectionTestUtils.setField(filter, "limitPerMinute", 2);
        ReflectionTestUtils.setField(filter, "limitPerHour", 100);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("非 SLA 路径直接放行，不占用配额")
    void passesThroughUnrelatedPaths() {
        for (int i = 0; i < 10; i++) {
            StepVerifier.create(filter.filter(exchange("/api/auth/api-keys"), chain)).verifyComplete();
        }
        verify(chain, times(10)).filter(any());
    }

    @Test
    @DisplayName("配额内放行，超出后返回 429 与 RATE_LIMIT_EXCEEDED")
    void rejectsAfterQuotaIsExhausted() throws Exception {
        StepVerifier.create(filter.filter(exchange("/api/auth/sla/refresh"), chain)).verifyComplete();
        StepVerifier.create(filter.filter(exchange("/api/auth/sla/bootstrap"), chain)).verifyComplete();

        MockServerWebExchange blocked = exchange("/api/auth/sla/refresh");
        StepVerifier.create(filter.filter(blocked, chain)).verifyComplete();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getResponse().getStatusCode());
        String body = blocked.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains(ErrorCode.RATE_LIMIT_EXCEEDED.code()), "429 体应带错误码，实际: " + body);
        assertTrue(body.contains("请求过于频繁"), body);
        // 第 3 次已被拦下，链只应当被调用 2 次
        verify(chain, times(2)).filter(any());
    }

    @Test
    @DisplayName("管理面同样受限：两条前缀共用同一个 IP 桶")
    void coversAdminPrefixWithSameBucket() {
        StepVerifier.create(filter.filter(exchange("/api/security/sla/accounts"), chain)).verifyComplete();
        StepVerifier.create(filter.filter(exchange("/api/auth/sla/refresh"), chain)).verifyComplete();

        MockServerWebExchange blocked = exchange("/api/security/sla/accounts/acc-1/revoke");
        StepVerifier.create(filter.filter(blocked, chain)).verifyComplete();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getResponse().getStatusCode());
        verify(chain, times(2)).filter(any());
    }

    private static MockServerWebExchange exchange(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }
}

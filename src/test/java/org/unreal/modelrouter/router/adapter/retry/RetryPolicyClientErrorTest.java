package org.unreal.modelrouter.router.adapter.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #718：重试策略必须按语义判定。
 *
 * <p>修复前 `isRetryable` 的不可重试名单只有阻塞式 `HttpClientErrorException`，
 * Reactor 链路上真实出现的三类（`WebClientResponseException`、`ResponseStatusException`、
 * `DownstreamServiceException`）全部落到末尾的「默认重试所有错误」——
 * 一个必然失败的下游 400 会被原样重放 {@code maxRetries} 次。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("重试策略按语义判定（#718）")
class RetryPolicyClientErrorTest {

    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new RetryPolicy(3, java.time.Duration.ofMillis(1),
                java.time.Duration.ofMillis(2), 1.0);
    }

    @Test
    @DisplayName("4xx 不重试：DownstreamServiceException(400/429) / WebClientResponseException(404)")
    void clientErrorsAreNotRetried() {
        assertFalse(retryPolicy.isRetryable(new DownstreamServiceException(
                "下游服务错误: bad request", HttpStatus.BAD_REQUEST, "invalid_request_error", null)),
                "400 是调用方问题，重试只会重复打给上游");
        assertFalse(retryPolicy.isRetryable(new DownstreamServiceException(
                "下游服务错误: rate limited", HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", 30L)),
                "429 且上游要求退避 30 秒，重试在毫秒级根本无意义");
        assertFalse(retryPolicy.isRetryable(WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null)),
                "Reactor 侧的 4xx 也属不可重试");
        assertFalse(retryPolicy.isRetryable(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)),
                "422 是调用方问题");
    }

    @Test
    @DisplayName("408 例外地可重试，5xx 与网络类照旧重试")
    void serverErrorsAndNetworkErrorsStayRetryable() {
        assertTrue(retryPolicy.isRetryable(new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT)),
                "408 是服务端等太久，重试有意义");
        assertTrue(retryPolicy.isRetryable(new DownstreamServiceException(
                "下游服务错误: upstream boom", HttpStatus.BAD_GATEWAY, "502", null)));
        assertTrue(retryPolicy.isRetryable(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)));
        assertTrue(retryPolicy.isRetryable(new ConnectException("Connection refused")));
        assertTrue(retryPolicy.isRetryable(new IllegalStateException("未知异常")),
                "状态未知的异常保持既有「默认重试」");
    }

    @Test
    @DisplayName("端到端：400 只调用一次；503 用满重试次数")
    void retryCountFollowsClassification() {
        final AtomicInteger clientErrorCalls = new AtomicInteger();
        Mono<Object> clientError = retryPolicy.withRetry(() -> {
            clientErrorCalls.incrementAndGet();
            return Mono.error(new DownstreamServiceException(
                    "下游服务错误: bad request", HttpStatus.BAD_REQUEST, "invalid_request_error", null));
        });
        try {
            clientError.block();
        } catch (RuntimeException ignored) {
            // 期望抛给调用方
        }
        assertEquals(1, clientErrorCalls.get(), "400 不得重放");

        final AtomicInteger serverErrorCalls = new AtomicInteger();
        Mono<Object> serverError = retryPolicy.withRetry(() -> {
            serverErrorCalls.incrementAndGet();
            return Mono.error(new DownstreamServiceException(
                    "下游服务错误: upstream boom", HttpStatus.BAD_GATEWAY, "502", null));
        });
        try {
            serverError.block();
        } catch (RuntimeException ignored) {
            // 期望抛给调用方
        }
        assertEquals(4, serverErrorCalls.get(), "初始 1 次 + maxRetries 3 次");
    }
}

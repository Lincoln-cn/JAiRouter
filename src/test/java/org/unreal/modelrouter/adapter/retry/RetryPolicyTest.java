package org.unreal.modelrouter.adapter.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryPolicy 单元测试
 *
 * @author JAiRouter Team
 * @since v2.3.0
 */
class RetryPolicyTest {

    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new RetryPolicy();
    }

    @Test
    void testDefaultConstructor() {
        // When
        RetryPolicy policy = new RetryPolicy();

        // Then
        assertEquals(3, policy.getMaxRetries());
        assertEquals(Duration.ofMillis(100), policy.getInitialDelay());
        assertEquals(Duration.ofMillis(5000), policy.getMaxDelay());
        assertEquals(2.0, policy.getMultiplier());
    }

    @Test
    void testCustomConstructor() {
        // When
        RetryPolicy policy = new RetryPolicy(
                5,
                Duration.ofMillis(200),
                Duration.ofMillis(10000),
                3.0
        );

        // Then
        assertEquals(5, policy.getMaxRetries());
        assertEquals(Duration.ofMillis(200), policy.getInitialDelay());
        assertEquals(Duration.ofMillis(10000), policy.getMaxDelay());
        assertEquals(3.0, policy.getMultiplier());
    }

    @Test
    void testGetNextDelay_FirstRetry() {
        // When
        Duration delay = retryPolicy.getNextDelay(0);

        // Then
        assertEquals(Duration.ofMillis(100), delay);
    }

    @Test
    void testGetNextDelay_SecondRetry() {
        // When
        Duration delay = retryPolicy.getNextDelay(1);

        // Then
        assertEquals(Duration.ofMillis(200), delay);
    }

    @Test
    void testGetNextDelay_ThirdRetry() {
        // When
        Duration delay = retryPolicy.getNextDelay(2);

        // Then
        assertEquals(Duration.ofMillis(400), delay);
    }

    @Test
    void testGetNextDelay_ExceedMaxDelay() {
        // When - 第 10 次重试应该超过最大延迟
        Duration delay = retryPolicy.getNextDelay(10);

        // Then - 应该被限制在最大延迟
        assertEquals(Duration.ofMillis(5000), delay);
        assertTrue(delay.toMillis() <= 5000);
    }

    @Test
    void testGetNextDelay_ExponentialBackoff() {
        // Then
        Duration delay0 = retryPolicy.getNextDelay(0);
        Duration delay1 = retryPolicy.getNextDelay(1);
        Duration delay2 = retryPolicy.getNextDelay(2);
        Duration delay3 = retryPolicy.getNextDelay(3);

        // 验证指数增长
        assertTrue(delay1.toMillis() > delay0.toMillis());
        assertTrue(delay2.toMillis() > delay1.toMillis());
        assertTrue(delay3.toMillis() > delay2.toMillis());
    }

    @Test
    void testCanRetry_WithinLimit() {
        // When
        boolean result = retryPolicy.canRetry(0, new Exception("test"));

        // Then
        assertTrue(result);
    }

    @Test
    void testCanRetry_ExceedLimit() {
        // When
        boolean result = retryPolicy.canRetry(5, new Exception("test"));

        // Then
        assertFalse(result);
    }

    @Test
    void testIsRetryable_TimeoutException() {
        // Given
        TimeoutException exception = new TimeoutException("Timeout");

        // When
        boolean result = retryPolicy.isRetryable(exception);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsRetryable_ConnectException() {
        // Given
        java.net.ConnectException exception = new java.net.ConnectException("Connection refused");

        // When
        boolean result = retryPolicy.isRetryable(exception);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsRetryable_SocketTimeoutException() {
        // Given
        java.net.SocketTimeoutException exception = new java.net.SocketTimeoutException("Read timed out");

        // When
        boolean result = retryPolicy.isRetryable(exception);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsRetryable_DownstreamServiceException() {
        // Given
        org.unreal.modelrouter.exception.DownstreamServiceException exception =
            new org.unreal.modelrouter.exception.DownstreamServiceException(
                "Service unavailable",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
            );

        // When
        boolean result = retryPolicy.isRetryable(exception);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsRetryable_HttpClientErrorException() {
        // Given
        org.springframework.web.client.HttpClientErrorException exception =
            new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.BAD_REQUEST);

        // When
        boolean result = retryPolicy.isRetryable(exception);

        // Then
        assertFalse(result);
    }

    @Test
    void testWithRetry_Success() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Mono<String> successMono = Mono.fromSupplier(() -> {
            callCount.incrementAndGet();
            return "Success";
        });

        // When
        Mono<String> result = retryPolicy.withRetry(() -> successMono);

        // Then
        String value = result.block();
        assertEquals("Success", value);
        assertEquals(1, callCount.get());
    }

    @Test
    void testWithRetry_RetryThenSuccess() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Mono<String> retryMono = Mono.fromSupplier(() -> {
            int count = callCount.incrementAndGet();
            if (count < 2) {
                throw new RuntimeException("Temporary error");
            }
            return "Success after retry";
        });

        // When
        Mono<String> result = retryPolicy.withRetry(() -> retryMono);

        // Then
        String value = result.block();
        assertEquals("Success after retry", value);
        assertTrue(callCount.get() >= 2);
    }

    @Test
    void testWithRetry_AllRetriesFail() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Mono<String> failMono = Mono.fromSupplier(() -> {
            callCount.incrementAndGet();
            throw new RuntimeException("Persistent error");
        });

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            retryPolicy.withRetry(() -> failMono).block();
        });

        // 应该调用 maxRetries + 1 次（初始调用 + 重试）
        assertTrue(callCount.get() >= 3);
    }

    @Test
    void testWithRetry_CustomRetryCondition() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Mono<String> retryMono = Mono.fromSupplier(() -> {
            callCount.incrementAndGet();
            throw new IllegalArgumentException("Non-retryable error");
        });

        // When - 使用只重试 IllegalArgumentException 的条件
        Mono<String> result = retryPolicy.withRetry(
                () -> retryMono,
                error -> error instanceof IllegalArgumentException
        );

        // Then - 应该会重试直到耗尽，抛出异常
        assertThrows(Throwable.class, () -> result.block());
        assertTrue(callCount.get() >= 3);
    }

    @Test
    void testWithRetry_FilterNonRetryable() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Mono<String> failMono = Mono.fromSupplier(() -> {
            callCount.incrementAndGet();
            throw new IllegalArgumentException("Non-retryable");
        });

        // When - 使用不重试 IllegalArgumentException 的条件
        Mono<String> result = retryPolicy.withRetry(
                () -> failMono,
                error -> !(error instanceof IllegalArgumentException)
        );

        // Then - 应该立即失败，不重试
        assertThrows(IllegalArgumentException.class, () -> result.block());
        assertEquals(1, callCount.get());
    }
}

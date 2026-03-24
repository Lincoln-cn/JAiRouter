/**
 * 限流算法测试
 * 测试各种限流算法的正确性和性能
 */
package org.unreal.modelrouter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.ratelimit.impl.LeakyBucketRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.SlidingWindowRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.TokenBucketRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.WarmUpRateLimiter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流算法单元测试
 */
@DisplayName("限流算法测试")
class RateLimiterTest {

    private RateLimitConfig config;

    @BeforeEach
    void setUp() {
        config = new RateLimitConfig();
        config.setQps(10);
        config.setBurst(20);
    }

    @Test
    @DisplayName("令牌桶算法 - 基本限流测试")
    void testTokenBucketBasic() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        limiter.init(config);

        // 突发流量测试（应在 burst 范围内允许通过）
        AtomicInteger allowedCount = new AtomicInteger(0);
        int totalRequests = 25;

        for (int i = 0; i < totalRequests; i++) {
            if (limiter.tryAcquire("test-key")) {
                allowedCount.incrementAndGet();
            }
        }

        // 应该允许通过 burst 数量的请求
        assertTrue(allowedCount.get() >= config.getBurst(), 
            "突发流量应该在 burst 范围内");
        assertTrue(allowedCount.get() <= totalRequests, 
            "允许的请求数不应超过总请求数");
    }

    @Test
    @DisplayName("令牌桶算法 - 持续速率测试")
    void testTokenBucketSustainedRate() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        config.setQps(5);
        config.setBurst(5);
        limiter.init(config);

        AtomicInteger allowedCount = new AtomicInteger(0);
        int testDurationSeconds = 3;
        CountDownLatch latch = new CountDownLatch(testDurationSeconds);

        // 每秒发送 10 个请求
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(() -> {
            for (int second = 0; second < testDurationSeconds; second++) {
                for (int i = 0; i < 10; i++) {
                    if (limiter.tryAcquire("test-key")) {
                        allowedCount.incrementAndGet();
                    }
                }
                try {
                    Thread.sleep(1000);
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        latch.await();
        executor.shutdown();

        // 3 秒内应该允许通过约 15 个请求（5 QPS * 3 秒）
        int expectedMin = config.getQps() * testDurationSeconds - 2;
        int expectedMax = config.getQps() * testDurationSeconds + config.getBurst();
        
        assertTrue(allowedCount.get() >= expectedMin, 
            "实际通过数：" + allowedCount.get() + "，应至少为：" + expectedMin);
        assertTrue(allowedCount.get() <= expectedMax, 
            "实际通过数：" + allowedCount.get() + "，应至多为：" + expectedMax);
    }

    @Test
    @DisplayName("漏桶算法 - 基本限流测试")
    void testLeakyBucketBasic() throws InterruptedException {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter();
        config.setQps(10);
        config.setBurst(15);
        limiter.init(config);

        AtomicInteger allowedCount = new AtomicInteger(0);
        int totalRequests = 30;

        // 快速发送请求
        for (int i = 0; i < totalRequests; i++) {
            if (limiter.tryAcquire("test-key")) {
                allowedCount.incrementAndGet();
            }
        }

        // 漏桶应该平滑限流
        assertTrue(allowedCount.get() > 0, "应该有请求被允许通过");
        assertTrue(allowedCount.get() <= totalRequests, "允许的请求数不应超过总请求数");
    }

    @Test
    @DisplayName("滑动窗口算法 - 基本限流测试")
    void testSlidingWindowBasic() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        config.setQps(10);
        limiter.init(config);

        AtomicInteger allowedCount = new AtomicInteger(0);
        int totalRequests = 20;

        for (int i = 0; i < totalRequests; i++) {
            if (limiter.tryAcquire("test-key")) {
                allowedCount.incrementAndGet();
            }
        }

        // 应该允许通过约 QPS 数量的请求
        assertTrue(allowedCount.get() >= config.getQps() - 2, 
            "实际通过数：" + allowedCount.get());
        assertTrue(allowedCount.get() <= config.getQps() + 2, 
            "实际通过数：" + allowedCount.get());
    }

    @Test
    @DisplayName("滑动窗口算法 - 时间窗口精度测试")
    void testSlidingWindowAccuracy() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        config.setQps(20);
        limiter.init(config);

        AtomicInteger allowedCount = new AtomicInteger(0);
        int testDurationMs = 500;

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            if (limiter.tryAcquire("test-key")) {
                allowedCount.incrementAndGet();
            }
        }

        // 0.5 秒内应该允许通过约 10 个请求（20 QPS * 0.5 秒）
        int expected = (config.getQps() * testDurationMs) / 1000;
        int tolerance = 5;
        
        assertTrue(allowedCount.get() >= expected - tolerance, 
            "实际通过数：" + allowedCount.get() + "，期望约：" + expected);
        assertTrue(allowedCount.get() <= expected + tolerance, 
            "实际通过数：" + allowedCount.get() + "，期望约：" + expected);
    }

    @Test
    @DisplayName("预热限流算法 - 预热期测试")
    void testWarmUpBasic() throws InterruptedException {
        WarmUpRateLimiter limiter = new WarmUpRateLimiter();
        config.setQps(10);
        config.setWarmUpPeriod(3); // 3 秒预热期
        limiter.init(config);

        // 预热期内请求应该被限制
        AtomicInteger allowedCountInWarmup = new AtomicInteger(0);
        int warmupRequests = 20;

        for (int i = 0; i < warmupRequests; i++) {
            if (limiter.tryAcquire("test-key")) {
                allowedCountInWarmup.incrementAndGet();
            }
        }

        // 预热期应该限制大部分请求
        assertTrue(allowedCountInWarmup.get() < warmupRequests, 
            "预热期应该限制部分请求");
    }

    @Test
    @DisplayName("并发性能测试 - 令牌桶")
    void testTokenBucketConcurrency() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        config.setQps(100);
        config.setBurst(50);
        limiter.init(config);

        int threadCount = 10;
        int requestsPerThread = 100;
        AtomicInteger allowedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        if (limiter.tryAcquire("test-key")) {
                            allowedCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 验证并发安全性
        int totalRequests = threadCount * requestsPerThread;
        assertTrue(allowedCount.get() > 0, "应该有请求被允许通过");
        assertTrue(allowedCount.get() <= totalRequests, "允许的请求数不应超过总请求数");
    }

    @Test
    @DisplayName("多 Key 隔离测试")
    void testMultiKeyIsolation() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        config.setQps(5);
        config.setBurst(5);
        limiter.init(config);

        // 测试两个不同的 key
        AtomicInteger key1Allowed = new AtomicInteger(0);
        AtomicInteger key2Allowed = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire("key1")) {
                key1Allowed.incrementAndGet();
            }
            if (limiter.tryAcquire("key2")) {
                key2Allowed.incrementAndGet();
            }
        }

        // 两个 key 应该独立限流
        assertTrue(key1Allowed.get() > 0, "key1 应该有请求通过");
        assertTrue(key2Allowed.get() > 0, "key2 应该有请求通过");
        assertEquals(key1Allowed.get(), key2Allowed.get(), 
            "两个 key 的限流应该独立");
    }

    @Test
    @DisplayName("限流器重置测试")
    void testRateLimiterReset() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        config.setQps(5);
        config.setBurst(5);
        limiter.init(config);

        // 消耗所有令牌
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("test-key");
        }

        // 重置限流器
        limiter.reset("test-key");

        // 重置后应该可以再次获取令牌
        assertTrue(limiter.tryAcquire("test-key"), 
            "重置后应该可以获取令牌");
    }
}

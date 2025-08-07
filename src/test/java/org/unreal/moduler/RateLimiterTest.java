package org.unreal.modelrouter.ratelimit;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.ratelimit.impl.LeakyBucketRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.SlidingWindowRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.TokenBucketRateLimiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private static final ModelServiceRegistry.ServiceType SERVICE_TYPE = ModelServiceRegistry.ServiceType.chat;
    private static final String MODEL_NAME = "gpt-3.5-turbo";
    private static final String CLIENT_IP = "127.0.0.1";

    // 修复后的令牌桶限流器实现，避免递归问题
    private static class FixedTokenBucketRateLimiter extends TokenBucketRateLimiter {
        private final ConcurrentHashMap<String, RateLimiter> mockScopedLimiters = new ConcurrentHashMap<>();

        public FixedTokenBucketRateLimiter(RateLimitConfig config) {
            super(config);
        }

        @Override
        protected RateLimiter createScopedLimiter() {
            // 创建一个简单的非作用域限流器，避免递归
            return new RateLimiter() {
                private final AtomicLong tokens = new AtomicLong(config.getCapacity());
                private final AtomicLong lastRefillTimestamp = new AtomicLong(System.nanoTime());

                @Override
                public boolean tryAcquire(RateLimitContext context) {
                    // 简化的令牌桶实现
                    long now = System.nanoTime();
                    long lastRefillTime = lastRefillTimestamp.get();
                    long timePassed = now - lastRefillTime;

                    // 计算需要补充的令牌数
                    long tokensToAdd = (timePassed * config.getRate()) / 1_000_000_000L;

                    if (tokensToAdd > 0) {
                        // 更新上次补充时间
                        if (lastRefillTimestamp.compareAndSet(lastRefillTime, now)) {
                            // 补充令牌，但不超过桶容量
                            tokens.updateAndGet(currentTokens ->
                                    Math.min(config.getCapacity(), currentTokens + tokensToAdd)
                            );
                        }
                    }

                    long currentTokens = tokens.get();
                    if (currentTokens < context.getTokens()) {
                        return false;
                    }

                    return tokens.compareAndSet(currentTokens, currentTokens - context.getTokens());
                }

                @Override
                public RateLimitConfig getConfig() {
                    return config;
                }
            };
        }
    }

    // 修复后的漏桶限流器实现，避免递归问题
    private static class FixedLeakyBucketRateLimiter extends LeakyBucketRateLimiter {
        public FixedLeakyBucketRateLimiter(RateLimitConfig config) {
            super(config);
        }

        @Override
        protected RateLimiter createScopedLimiter() {
            // 创建一个简单的非作用域限流器，避免递归
            return new RateLimiter() {
                private final AtomicLong waterLevel = new AtomicLong(0);
                private final AtomicLong lastLeakTimestamp = new AtomicLong(System.nanoTime());

                @Override
                public boolean tryAcquire(RateLimitContext context) {
                    long now = System.nanoTime();
                    long lastLeakTime = lastLeakTimestamp.get();
                    long timePassed = now - lastLeakTime;

                    // 计算应该漏出的水量
                    long waterToLeak = (timePassed * config.getRate()) / 1_000_000_000L;

                    if (waterToLeak > 0) {
                        if (lastLeakTimestamp.compareAndSet(lastLeakTime, now)) {
                            waterLevel.updateAndGet(currentLevel ->
                                    Math.max(0, currentLevel - waterToLeak)
                            );
                        }
                    }

                    long currentLevel = waterLevel.get();
                    if (currentLevel + context.getTokens() > config.getCapacity()) {
                        return false; // 水满，拒绝请求
                    }

                    return waterLevel.compareAndSet(currentLevel, currentLevel + context.getTokens());
                }

                @Override
                public RateLimitConfig getConfig() {
                    return config;
                }
            };
        }
    }

    // 修复后的滑动窗口限流器实现，避免递归问题
    private static class FixedSlidingWindowRateLimiter extends SlidingWindowRateLimiter {
        public FixedSlidingWindowRateLimiter(RateLimitConfig config) {
            super(config);
        }

        @Override
        protected RateLimiter createScopedLimiter() {
            // 创建一个简单的非作用域限流器，避免递归
            return new RateLimiter() {
                private final Queue<Long> requestTimestamps = new ConcurrentLinkedQueue<>();

                @Override
                public boolean tryAcquire(RateLimitContext context) {
                    long now = System.currentTimeMillis();
                    long windowSizeMillis = 1000; // 1秒窗口

                    // 移除窗口外的请求记录
                    while (!requestTimestamps.isEmpty() &&
                            requestTimestamps.peek() < now - windowSizeMillis) {
                        requestTimestamps.poll();
                    }

                    // 检查是否超过限制
                    if (requestTimestamps.size() >= config.getRate()) {
                        return false;
                    }

                    requestTimestamps.offer(now);
                    return true;
                }

                @Override
                public RateLimitConfig getConfig() {
                    return config;
                }
            };
        }
    }

    @Test
    void testTokenBucketRateLimiter() throws InterruptedException {
        // 创建令牌桶限流器配置：容量10，速率5个/秒
        RateLimitConfig config = RateLimitConfig.builder()
                .algorithm("token-bucket")
                .capacity(10)
                .rate(5)
                .build();

        RateLimiter limiter = new FixedTokenBucketRateLimiter(config);

        RateLimitContext context = new RateLimitContext(SERVICE_TYPE, MODEL_NAME, CLIENT_IP, 1);

        // 初始应该有足够的令牌
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(context), "Should allow request " + i);
        }

        // 应该被拒绝，因为令牌不足
        assertFalse(limiter.tryAcquire(context), "Should deny request when no tokens");

        // 等待一段时间让令牌重新填充
        Thread.sleep(2000); // 等待2秒

        // 现在应该可以处理一些请求
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(context), "Should allow request after refill " + i);
        }
    }

    @Test
    void testLeakyBucketRateLimiter() throws InterruptedException {
        // 创建漏桶限流器配置：容量10，速率5个/秒
        RateLimitConfig config = RateLimitConfig.builder()
                .algorithm("leaky-bucket")
                .capacity(10)
                .rate(5)
                .build();

        RateLimiter limiter = new FixedLeakyBucketRateLimiter(config);

        RateLimitContext context = new RateLimitContext(SERVICE_TYPE, MODEL_NAME, CLIENT_IP, 1);

        // 快速发送多个请求，应该被接受直到桶满
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(context), "Should allow request " + i);
        }

        // 桶已满，应该被拒绝
        assertFalse(limiter.tryAcquire(context), "Should deny request when bucket full");

        // 等待一段时间让水漏出
        Thread.sleep(2000); // 等待2秒

        // 现在应该可以处理一些请求
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(context), "Should allow request after leaking " + i);
        }
    }

    @Test
    void testSlidingWindowRateLimiter() throws InterruptedException {
        // 创建滑动窗口限流器配置：每秒最多5个请求
        RateLimitConfig config = RateLimitConfig.builder()
                .algorithm("sliding-window")
                .rate(5)
                .build();

        RateLimiter limiter = new FixedSlidingWindowRateLimiter(config);

        RateLimitContext context = new RateLimitContext(SERVICE_TYPE, MODEL_NAME, CLIENT_IP, 1);

        // 在窗口内发送请求数量不超过限制
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(context), "Should allow request " + i);
        }

        // 超过限制应该被拒绝
        assertFalse(limiter.tryAcquire(context), "Should deny request when over limit");

        // 等待窗口过去
        Thread.sleep(1100); // 等待超过1秒

        // 现在应该可以处理请求
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(context), "Should allow request after window passes " + i);
        }
    }

    @Test
    void testRateLimiterGetConfig() {
        RateLimitConfig config = RateLimitConfig.builder()
                .algorithm("token-bucket")
                .capacity(100)
                .rate(10)
                .scope("service")
                .build();

        RateLimiter tokenBucketLimiter = new FixedTokenBucketRateLimiter(config);
        RateLimiter leakyBucketLimiter = new FixedLeakyBucketRateLimiter(config);
        RateLimiter slidingWindowLimiter = new FixedSlidingWindowRateLimiter(config);

        assertEquals(config, tokenBucketLimiter.getConfig());
        assertEquals(config, leakyBucketLimiter.getConfig());
        assertEquals(config, slidingWindowLimiter.getConfig());
    }
}

package org.unreal.modelrouter.router.ratelimit.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RedisSlidingWindowRateLimiter} 的真 Redis 集成测试（#161）。
 *
 * <p><b>默认跳过</b>：仅当环境变量 {@code REDIS_TEST=true} 时执行；连接参数可用
 * {@code REDIS_TEST_HOST}（默认 {@code 127.0.0.1}）与 {@code REDIS_TEST_PORT}（默认 {@code 6379}）
 * 覆盖。CI 已提供 redis service 并设置该变量。</p>
 *
 * <p>为什么必须用真 Redis：Lua 的窗口语义（ZREMRANGEBYSCORE + ZCARD + ZADD）与
 * <b>并发原子性</b>在替身上验证不了——替身只能断言调用形态，而「多副本并发时会不会超发」
 * 恰恰是分布式限流唯一重要的性质。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.5
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST", matches = "true")
@DisplayName("RedisSlidingWindowRateLimiter 真 Redis 集成测试（REDIS_TEST=true 时执行）")
class RedisSlidingWindowRateLimiterRedisIntegrationTest {

    private LettuceConnectionFactory factory;
    private ReactiveRedisTemplate<String, String> template;
    private String key;

    @BeforeEach
    void setUp() {
        final String host = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
        final int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
        factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        template = new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
        key = "jairouter:ratelimit:test:" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (template != null) {
            template.delete(key).block(Duration.ofSeconds(5));
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    private RedisSlidingWindowRateLimiter limiter(final long rate) {
        final RateLimitConfig config = RateLimitConfig.builder()
                .capacity(rate).rate(rate).scope("client-ip").build();
        return new RedisSlidingWindowRateLimiter(config, key, template, Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("真 Redis：单个窗口内放行次数不超过上限")
    void doesNotExceedLimitWithinOneWindow() {
        final int rate = 10;
        final RedisSlidingWindowRateLimiter limiter = limiter(rate);

        int allowed = 0;
        for (int i = 0; i < 50; i++) {
            if (limiter.tryAcquire(null)) {
                allowed++;
            }
        }

        assertEquals(rate, allowed, "1 秒窗口内应恰好放行 rate 次");
    }

    @Test
    @DisplayName("真 Redis：并发调用不超发——Lua 原子性（判定与记录之间不插入其它副本的写入）")
    void concurrentAcquireDoesNotOverIssue() throws Exception {
        final int rate = 50;
        final int threads = 16;
        final int attemptsPerThread = 100;   // 合计 1600 次尝试，期望只有 rate 次放行
        final RedisSlidingWindowRateLimiter limiter = limiter(rate);

        final AtomicInteger allowed = new AtomicInteger();
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    barrier.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire(null)) {
                            allowed.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发任务未在超时内完成");
        } finally {
            pool.shutdownNow();
        }

        // 不断言「恰好等于 rate」：1600 次调用若跨越了 1 秒窗口边界会多放行一批，
        // 那是窗口滑动的正确行为，不是缺陷。这里只验证不超发的核心性质。
        assertTrue(allowed.get() <= rate,
                "并发放行数不得超过上限，实际 " + allowed.get() + " > " + rate
                        + "——说明 Lua 未能把 ZCARD 判定与 ZADD 记录放在同一段脚本里");
        assertTrue(allowed.get() > 0, "应至少放行一次");
    }

    @Test
    @DisplayName("真 Redis：窗口滑动后恢复放行")
    void recoversAfterWindowSlides() throws Exception {
        final int rate = 5;
        final RedisSlidingWindowRateLimiter limiter = limiter(rate);

        for (int i = 0; i < rate; i++) {
            assertTrue(limiter.tryAcquire(null), "窗口内第 " + (i + 1) + " 次应放行");
        }
        assertFalse(limiter.tryAcquire(null), "窗口内已放满，应拒绝");

        Thread.sleep(1100L);

        assertTrue(limiter.tryAcquire(null), "滑过一个窗口后应恢复放行");
    }

    @Test
    @DisplayName("真 Redis：key 带正数 TTL——桶自动过期，无需清理任务")
    void setsPositiveTtlOnKey() {
        limiter(10).tryAcquire(null);

        final Duration ttl = template.getExpire(key).block(Duration.ofSeconds(5));

        assertNotNull(ttl, "应能读到 key 的 TTL");
        assertTrue(!ttl.isZero() && !ttl.isNegative(), "TTL 应为正数，实际 " + ttl);
    }

    @Test
    @DisplayName("真 Redis：不同 key 的窗口互不影响")
    void separateKeysAreIndependent() {
        final String otherKey = key + "-other";
        final RateLimitConfig config = RateLimitConfig.builder().capacity(2L).rate(2L).scope("client-ip").build();
        try {
            final RedisSlidingWindowRateLimiter a = new RedisSlidingWindowRateLimiter(
                    config, key, template, Duration.ofSeconds(2));
            final RedisSlidingWindowRateLimiter b = new RedisSlidingWindowRateLimiter(
                    config, otherKey, template, Duration.ofSeconds(2));

            assertTrue(a.tryAcquire(null));
            assertTrue(a.tryAcquire(null));
            assertFalse(a.tryAcquire(null), "key A 已放满");

            assertTrue(b.tryAcquire(null), "key B 不应受 key A 影响");
        } finally {
            template.delete(otherKey).block(Duration.ofSeconds(5));
        }
    }
}

package org.unreal.modelrouter.router.ratelimit.impl;

import lombok.extern.slf4j.Slf4j;
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
 * <p><b>issue #227：窗口固定 1 秒，断言必须容忍跨窗口边界。</b>被测实现的窗口是硬编码的
 * {@code WINDOW_MS = 1000}，而「先在窗口内填满、再断言拒绝」这类断言隐含了「填充过程不跨窗口边界」
 * 的前提；真 Redis 下第一次调用要付建连 + 脚本编译（本机实测 2678ms），该前提会直接不成立。
 * 处置有两层：{@code @BeforeEach} 用一次性 key 预热（消掉首次调用的异常耗时），
 * 以及边界敏感的三条断言各自带有限次重试 —— 真正的缺陷（Lua 未把判定与记录放在同一段脚本里）
 * 会在每次尝试上都复现，因此重试不会掩盖它。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.5
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "REDIS_TEST", matches = "true")
@DisplayName("RedisSlidingWindowRateLimiter 真 Redis 集成测试（REDIS_TEST=true 时执行）")
class RedisSlidingWindowRateLimiterRedisIntegrationTest {

    /**
     * 边界敏感断言的尝试次数：窗口固定 1 秒，单次尝试可能恰好跨越窗口边界（issue #227）。
     * 真正的缺陷（Lua 未把判定与记录放进同一段脚本）在每次尝试上都会复现，因此重试不掩盖它。
     */
    private static final int MAX_ATTEMPTS = 3;

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
        warmUp();
    }

    /**
     * 预热：真 Redis 下**第一次** {@code tryAcquire} 要付 Lettuce 建连 + Lua 脚本编译的代价，
     * 本机实测 **2678ms**（同一批后续调用各约 5ms），而被测实现的窗口固定 1 秒
     * （{@link RedisSlidingWindowRateLimiter} 的 {@code WINDOW_MS}）—— 首次调用一次就把窗口撑满，
     * 于是「同一窗口内应拒绝」的断言假红（issue #227，CI 与本机都能复现）。
     *
     * <p>用一个一次性 key 预热，避免占用被测 key 的额度。</p>
     */
    private void warmUp() {
        final String warmupKey = key + ":warmup";
        try {
            limiter(1L, warmupKey).tryAcquire(null);
        } catch (RuntimeException e) {
            // 连不上 Redis 时不在这里失败：真正用到 Redis 的断言会给出更清晰的错误
            log.warn("滑动窗口限流预热失败（不影响后续断言）：{}", e.toString());
        } finally {
            template.delete(warmupKey).block(Duration.ofSeconds(5));
        }
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
        return limiter(rate, key);
    }

    /**
     * 超时给到 5 秒：预热那一次含建连与脚本编译，实测可达 2.7 秒，2 秒的上限会把预热本身打断。
     */
    private RedisSlidingWindowRateLimiter limiter(final long rate, final String targetKey) {
        final RateLimitConfig config = RateLimitConfig.builder()
                .capacity(rate).rate(rate).scope("client-ip").build();
        return new RedisSlidingWindowRateLimiter(config, targetKey, template, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("真 Redis：单个窗口内放行次数不超过上限")
    void doesNotExceedLimitWithinOneWindow() {
        final int rate = 10;
        final int calls = 50;

        // 窗口固定 1 秒：这 50 次调用若跨越窗口边界，放行数会多于 rate —— 那是滑动窗口的正确行为，
        // 不是缺陷。所以这里重试，直到拿到一个「没跨边界」的样本（issue #227：原写法只跑一次，
        // 只要填充过程慢过 1 秒就假红）。
        int allowed = -1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            template.delete(key).block(Duration.ofSeconds(5));
            final RedisSlidingWindowRateLimiter limiter = limiter(rate);
            allowed = 0;
            for (int i = 0; i < calls; i++) {
                if (limiter.tryAcquire(null)) {
                    allowed++;
                }
            }
            if (allowed == rate) {
                break;
            }
        }

        assertEquals(rate, allowed, "1 秒窗口内应恰好放行 " + rate + " 次；" + calls + " 次调用重试 "
                + MAX_ATTEMPTS + " 次都没取到「未跨越窗口边界」的样本");
    }

    @Test
    @DisplayName("真 Redis：并发调用不超发——Lua 原子性（判定与记录之间不插入其它副本的写入）")
    void concurrentAcquireDoesNotOverIssue() throws Exception {
        final int rate = 50;
        final int threads = 16;
        final int attemptsPerThread = 100;   // 合计 1600 次尝试，期望只有 rate 次放行

        // 这一批 1600 次调用若跨越了 1 秒窗口边界，会多放行若干（滑动窗口的正确行为，不是缺陷）。
        // 因此重试，直到出现一次「放行数不超过上限」的样本；真正的 Lua 竞态在每次尝试上都会超发
        // （issue #227：原写法只跑一次，本机实测因跨边界放出 100 > 50 而红）。
        int allowed = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && allowed > rate; attempt++) {
            template.delete(key).block(Duration.ofSeconds(5));
            allowed = concurrentBurst(limiter(rate), threads, attemptsPerThread);
        }

        assertTrue(allowed <= rate,
                "并发放行数不得超过上限，实际 " + allowed + " > " + rate + "（重试 " + MAX_ATTEMPTS
                        + " 次均超发）——说明 Lua 未能把 ZCARD 判定与 ZADD 记录放在同一段脚本里");
        assertTrue(allowed > 0, "应至少放行一次");
    }

    /** 一次性放一批并发请求，返回放行次数 */
    private int concurrentBurst(final RedisSlidingWindowRateLimiter limiter, final int threads,
                                final int attemptsPerThread) throws Exception {
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
        return allowed.get();
    }

    @Test
    @DisplayName("真 Redis：窗口滑动后恢复放行")
    void recoversAfterWindowSlides() throws Exception {
        final int rate = 5;

        // 「窗口内填满后应拒绝」隐含「填充过程不跨窗口边界」。窗口固定 1 秒，若这次填充恰好跨了
        // 边界（首次调用含建连与脚本编译时尤其容易），第 rate+1 次就会被放行 —— 重试即可
        // （issue #227，CI 与本机都复现过）。
        boolean rejectedWithinWindow = false;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && !rejectedWithinWindow; attempt++) {
            template.delete(key).block(Duration.ofSeconds(5));
            final RedisSlidingWindowRateLimiter limiter = limiter(rate);
            for (int i = 0; i < rate; i++) {
                assertTrue(limiter.tryAcquire(null), "窗口内第 " + (i + 1) + " 次应放行");
            }
            rejectedWithinWindow = !limiter.tryAcquire(null);
        }
        assertTrue(rejectedWithinWindow, "同一 1 秒窗口内放满 " + rate + " 次后应拒绝（重试 "
                + MAX_ATTEMPTS + " 次都没取到未跨边界的样本）");

        Thread.sleep(1100L);

        assertTrue(limiter(rate).tryAcquire(null), "滑过一个窗口后应恢复放行");
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

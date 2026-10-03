package org.unreal.modelrouter.router.ratelimit.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RedisSlidingWindowRateLimiter} 的降级路径单元测试（#161）。
 *
 * <p>这里只覆盖「Redis 出问题时不能把请求拒掉」——Lua 的窗口语义与原子性由
 * {@code RedisSlidingWindowRateLimiterRedisIntegrationTest} 用真 Redis 验证
 * （替身无法验证 ZSET 与并发语义）。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.5
 */
@DisplayName("Redis 滑动窗口限流器（降级路径）")
class RedisSlidingWindowRateLimiterTest {

    private static RateLimitConfig config() {
        return RateLimitConfig.builder().capacity(100L).rate(100L).scope("client-ip").build();
    }

    @Test
    @DisplayName("Redis 调用抛异常时 fail-open 放行，不把异常抛给调用方")
    void failsOpenWhenRedisThrows() {
        @SuppressWarnings("unchecked")
        final ReactiveRedisTemplate<String, String> template = mock(ReactiveRedisTemplate.class);
        when(template.execute(any(), anyList(), anyList()))
                .thenThrow(new IllegalStateException("redis down"));

        final RedisSlidingWindowRateLimiter limiter = new RedisSlidingWindowRateLimiter(
                config(), "jairouter:ratelimit:test", template, Duration.ofMillis(50));

        assertTrue(limiter.tryAcquire(null),
                "Redis 故障时全站拒绝比放宽限流更糟，必须 fail-open");
    }

    @Test
    @DisplayName("Redis 模板不可用时放行（不阻断请求）")
    void allowsWhenTemplateIsAbsent() {
        final RedisSlidingWindowRateLimiter limiter = new RedisSlidingWindowRateLimiter(
                config(), "jairouter:ratelimit:test", null, Duration.ofMillis(50));

        assertTrue(limiter.tryAcquire(null));
    }

    @Test
    @DisplayName("配置通过 getter 暴露，供监控读取")
    void exposesConfig() {
        final RateLimitConfig config = config();
        final RedisSlidingWindowRateLimiter limiter = new RedisSlidingWindowRateLimiter(
                config, "jairouter:ratelimit:test", null, Duration.ofMillis(50));

        assertTrue(config == limiter.getConfig());
    }
}

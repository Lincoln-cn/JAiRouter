package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * {@link RedisEndUserRateLimitCounter} 的计数语义与降级路径。
 *
 * <p>Redis 不可用时的取向是 **fail-open**（与既有分布式限流一致）：
 * 宁可少限一点，也不要因为缓存故障把整条推理链路拒掉。</p>
 */
@DisplayName("Redis 终端用户计数器：原子计数 + fail-open 降级")
class RedisEndUserRateLimitCounterTest {

    private static final long WINDOW_START = 1_760_000_040L;

    @Test
    @DisplayName("Lua 返回已用次数时放行")
    void allowsWhenBelowLimit() {
        final RedisEndUserRateLimitCounter counter =
                new RedisEndUserRateLimitCounter(templateReturning(Flux.just(1L)), Duration.ofMillis(50));

        final EndUserRateLimitCounter.Decision decision =
                counter.record("bucket", WINDOW_START, 5, WINDOW_START + 10);

        assertTrue(decision.allowed());
        assertEquals(1, decision.used());
        assertEquals(5, decision.limit());
        assertFalse(decision.degraded(), "正常路径不应标记降级");
    }

    @Test
    @DisplayName("Lua 返回 0（超限回滚）时拒绝，已用量按 limit")
    void rejectsWhenScriptRollsBack() {
        final RedisEndUserRateLimitCounter counter =
                new RedisEndUserRateLimitCounter(templateReturning(Flux.just(0L)), Duration.ofMillis(50));

        final EndUserRateLimitCounter.Decision decision =
                counter.record("bucket", WINDOW_START, 5, WINDOW_START + 10);

        assertFalse(decision.allowed());
        assertEquals(5, decision.used());
        assertEquals(0, decision.remaining());
        assertEquals(50, decision.retryAfterSeconds());
        assertFalse(decision.degraded());
    }

    @Test
    @DisplayName("Redis 抛异常时 fail-open 放行并标记降级")
    void failsOpenOnException() {
        final RedisEndUserRateLimitCounter counter =
                new RedisEndUserRateLimitCounter(templateThrowing(), Duration.ofMillis(50));

        final EndUserRateLimitCounter.Decision decision =
                counter.record("bucket", WINDOW_START, 5, WINDOW_START + 10);

        assertTrue(decision.allowed(), "Redis 故障不应阻断请求");
        assertTrue(decision.degraded(), "必须可观测到这是一次降级放行");
        assertEquals(5, decision.limit());
    }

    @Test
    @DisplayName("模板缺失（未配 Redis）时同样降级放行")
    void failsOpenWithoutTemplate() {
        final RedisEndUserRateLimitCounter counter = new RedisEndUserRateLimitCounter(null, null);

        final EndUserRateLimitCounter.Decision decision =
                counter.record("bucket", WINDOW_START, 5, WINDOW_START + 10);

        assertTrue(decision.allowed());
        assertTrue(decision.degraded());
    }

    @Test
    @DisplayName("Redis key 带固定前缀与窗口起点（窗口切换天然隔离）")
    void redisKeyContainsPrefixAndWindow() {
        assertEquals("jairouter:ratelimit:end-user:bucket:" + WINDOW_START,
                RedisEndUserRateLimitCounter.redisKey("bucket", WINDOW_START));
    }

    private static ReactiveRedisTemplate<String, String> templateReturning(final Flux<Long> result) {
        final ReactiveRedisTemplate<String, String> template = template();
        // doReturn 绕过 thenReturn 的泛型推断（execute 的 T 在这里被推断成 Object）
        doReturn(result).when(template).execute(any(), anyList(), anyList());
        return template;
    }

    private static ReactiveRedisTemplate<String, String> templateThrowing() {
        final ReactiveRedisTemplate<String, String> template = template();
        doThrow(new IllegalStateException("redis down")).when(template).execute(any(), anyList(), anyList());
        return template;
    }

    @SuppressWarnings("unchecked")
    private static ReactiveRedisTemplate<String, String> template() {
        return mock(ReactiveRedisTemplate.class);
    }
}

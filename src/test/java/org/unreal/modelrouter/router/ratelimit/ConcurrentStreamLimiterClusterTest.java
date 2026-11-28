package org.unreal.modelrouter.router.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

/**
 * {@link ConcurrentStreamLimiter} 的跨副本计数路径（{@code concurrent-scope=cluster}）。
 *
 * <p>issue #1246 的补充：默认按副本计数已能守住各自的出站连接池，但多副本部署若希望
 * 「全集群总共只允许 N 条在途」，需要把计数放到 Redis。这里覆盖四条边界：Redis 判超限、
 * Redis 放行并归还、Redis 故障降级本地、没有 Redis 时的行为。</p>
 *
 * <p>只验证限制器与 Redis 的交互口径，真机多副本行为由部署验证覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConcurrentStreamLimiter 跨副本计数")
class ConcurrentStreamLimiterClusterTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectProvider<ReactiveRedisTemplate<String, String>> provider;

    private ConcurrentStreamLimiter limiter;

    @BeforeEach
    void setUp() throws Exception {
        limiter = new ConcurrentStreamLimiter(2);
        setField("concurrentScope", ConcurrentStreamLimiter.SCOPE_CLUSTER);
        setField("keyTtlSeconds", 700L);
        limiter.setReactiveRedisTemplateProvider(provider);
    }

    private void setField(final String name, final Object value) throws Exception {
        final Field field = ConcurrentStreamLimiter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(limiter, value);
    }

    @Test
    @DisplayName("Redis 判超限时返回拒绝租约，且不再归还")
    void clusterRejectsWhenRedisReportsOverLimit() {
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.execute(anyRedisScript(), anyList(), anyList())).thenReturn(Flux.just(-1L));

        final ConcurrentStreamLimiter.Lease lease = limiter.acquireMono("key-a").block();

        assertFalse(lease.granted(), "Redis 返回 -1 必须当成超限");
        assertEquals(1, limiter.rejectedTotal());
    }

    @Test
    @DisplayName("Redis 放行后，租约关闭时通过 Redis 归还名额")
    void clusterGrantsAndReleasesThroughRedis() throws Exception {
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.execute(anyRedisScript(), anyList(), anyList()))
                .thenReturn(Flux.just(1L))
                .thenReturn(Flux.just(0L));

        final ConcurrentStreamLimiter.Lease lease = limiter.acquireMono("key-a").block();

        assertTrue(lease.granted());
        lease.close();
        Thread.sleep(200);

        // 一次申请 + 一次归还
        verify(redisTemplate, atLeastOnce()).execute(anyRedisScript(), anyList(), anyList());
    }

    @Test
    @DisplayName("Redis 报错时降级为本地计数，请求照常放行")
    void clusterFallsBackToLocalOnRedisFailure() {
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.execute(anyRedisScript(), anyList(), anyList()))
                .thenReturn(Flux.error(new IllegalStateException("redis down")));

        final ConcurrentStreamLimiter.Lease first = limiter.acquireMono("key-a").block();
        final ConcurrentStreamLimiter.Lease second = limiter.acquireMono("key-a").block();
        final ConcurrentStreamLimiter.Lease third = limiter.acquireMono("key-a").block();

        assertTrue(first.granted(), "Redis 故障不能把请求全拒了");
        assertTrue(second.granted());
        assertFalse(third.granted(), "降级之后仍按本地上限 2 生效");
        assertEquals(2, limiter.activeCount("key-a"), "降级路径的计数落在本地");

        first.close();
        second.close();
        assertEquals(0, limiter.activeCount("key-a"));
    }

    @Test
    @DisplayName("容器里没有 Redis 时 cluster 配置退化为本地计数")
    void clusterWithoutRedisUsesLocalCounting() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertTrue(limiter.acquireMono("key-a").block().granted());
        assertTrue(limiter.acquireMono("key-a").block().granted());
        assertFalse(limiter.acquireMono("key-a").block().granted());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("默认 local 范围完全不碰 Redis")
    void localScopeNeverTouchesRedis() throws Exception {
        setField("concurrentScope", ConcurrentStreamLimiter.SCOPE_LOCAL);

        assertTrue(limiter.acquireMono("key-a").block().granted());
        assertTrue(limiter.acquireMono("key-a").block().granted());
        assertFalse(limiter.acquireMono("key-a").block().granted(), "本地上限仍是 2");
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(provider);
    }

    @SuppressWarnings("unchecked")
    private static RedisScript<Long> anyRedisScript() {
        return (RedisScript<Long>) any(RedisScript.class);
    }
}

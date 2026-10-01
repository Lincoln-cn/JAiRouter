package org.unreal.modelrouter.common.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScheduledTaskLock} 测试。
 *
 * <p>用一个「只有同一个 key 才能被占一次」的内存假 Redis 模拟多副本：第一个取锁者成功、
 * 第二个（代表另一个副本）失败——这正是本组件要保证的语义。同时覆盖三类降级路径：
 * 未配置 Redis、Redis 抛异常、开关关闭。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@DisplayName("定时任务排他锁测试")
class ScheduledTaskLockTest {

    /** 模拟「谁占了这个 key」的共享状态。 */
    private static final Set<String> HELD_KEYS = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("unchecked")
    private ObjectProvider<ReactiveStringRedisTemplate> providerReturning(
            final ReactiveStringRedisTemplate template) {
        ObjectProvider<ReactiveStringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ReactiveStringRedisTemplate fakeRedisRecordingTtl(final AtomicReference<Duration> capturedTtl) {
        HELD_KEYS.clear();
        ReactiveStringRedisTemplate template = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    capturedTtl.set(invocation.getArgument(2));
                    String key = invocation.getArgument(0);
                    return Mono.just(HELD_KEYS.add(key));
                });
        return template;
    }

    @Test
    @DisplayName("同一任务名的第二个副本取锁失败（互斥生效）")
    void tryAcquire_secondReplica_isRejected() {
        AtomicReference<Duration> ttl = new AtomicReference<>();
        ScheduledTaskLock lockA = new ScheduledTaskLock(
                providerReturning(fakeRedisRecordingTtl(ttl)), true);
        ScheduledTaskLock lockB = new ScheduledTaskLock(
                providerReturning(fakeRedisRecordingTtl(ttl)), true);

        assertTrue(lockA.tryAcquire("audit.security-alerts", Duration.ofMinutes(5)),
                "第一个副本应取得锁");
        assertFalse(lockB.tryAcquire("audit.security-alerts", Duration.ofMinutes(5)),
                "第二个副本应被拒绝，从而跳过本周期");
    }

    @Test
    @DisplayName("不同任务名各自独立取锁，互不影响")
    void tryAcquire_differentTaskNames_areIndependent() {
        AtomicReference<Duration> ttl = new AtomicReference<>();
        ScheduledTaskLock lock = new ScheduledTaskLock(
                providerReturning(fakeRedisRecordingTtl(ttl)), true);

        assertTrue(lock.tryAcquire("audit.security-alerts", Duration.ofMinutes(5)));
        assertTrue(lock.tryAcquire("security.archive", Duration.ofDays(1)),
                "另一个任务名不应被前一个任务的锁挡住");
    }

    @Test
    @DisplayName("租约取触发周期的 90%（不等于周期，避免下个周期与到期赛跑）")
    void tryAcquire_leaseIsNintyPercentOfPeriod() {
        AtomicReference<Duration> ttl = new AtomicReference<>();
        ScheduledTaskLock lock = new ScheduledTaskLock(
                providerReturning(fakeRedisRecordingTtl(ttl)), true);

        lock.tryAcquire("t", Duration.ofMinutes(10));

        assertNotNull(ttl.get(), "应向 Redis 传入租约");
        assertEquals(Duration.ofMinutes(9), ttl.get(),
                "10 分钟周期的租约应为 9 分钟");
    }

    @Test
    @DisplayName("未配置 Redis 时放行（等价单实例语义，行为与引入前一致）")
    void tryAcquire_withoutRedis_allowsExecution() {
        ScheduledTaskLock lock = new ScheduledTaskLock(providerReturning(null), true);

        assertTrue(lock.tryAcquire("audit.security-alerts", Duration.ofMinutes(5)));
        assertTrue(lock.tryAcquire("audit.security-alerts", Duration.ofMinutes(5)),
                "无 Redis 时不应互相阻塞");
    }

    @Test
    @DisplayName("Redis 抛异常时 fail-open 放行（宁可重复执行，也不让清理/巡检停摆）")
    void tryAcquire_whenRedisThrows_failsOpen() {
        ReactiveStringRedisTemplate template = mock(ReactiveStringRedisTemplate.class);
        when(template.opsForValue()).thenThrow(new IllegalStateException("redis down"));

        ScheduledTaskLock lock = new ScheduledTaskLock(providerReturning(template), true);

        assertTrue(lock.tryAcquire("audit.security-alerts", Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("开关关闭时放行，且完全不访问 Redis")
    void tryAcquire_whenDisabled_allowsAndSkipsRedis() {
        ReactiveStringRedisTemplate template = mock(ReactiveStringRedisTemplate.class);
        ScheduledTaskLock lock = new ScheduledTaskLock(providerReturning(template), false);

        assertTrue(lock.tryAcquire("audit.security-alerts", Duration.ofMinutes(5)));
        verify(template, never()).opsForValue();
    }

    @Test
    @DisplayName("多线程并发取同一把锁时只有一个成功")
    void tryAcquire_concurrentThreads_onlyOneSucceeds() throws Exception {
        AtomicReference<Duration> ttl = new AtomicReference<>();
        ScheduledTaskLock lock = new ScheduledTaskLock(
                providerReturning(fakeRedisRecordingTtl(ttl)), true);

        int threads = 16;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acquired = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    if (lock.tryAcquire("quota.flush", Duration.ofMinutes(1))) {
                        acquired.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 屏障中断等：不计入成功数
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue(done.await(20, TimeUnit.SECONDS), "并发取锁应在超时前完成");
        assertEquals(1, acquired.get(), "同一任务名同一周期只应有一个执行者");
    }
}

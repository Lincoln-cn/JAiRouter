package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计数后端「限额 CAS」契约（P1 审计：分布式/本地 TOCTOU 双花修复）。
 *
 * <p>语义：{@code incrementWithLimit} 在累加前检查累加后是否越限；越限则不落账并返回 {@code null}。</p>
 */
@DisplayName("QuotaCounterBackend 限额 CAS 契约")
class QuotaCounterBackendLimitTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final QuotaDimension DIMENSION = new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-4");

    private LocalCounterBackend backend;

    @BeforeEach
    void setUp() {
        backend = new LocalCounterBackend();
    }

    private QuotaCounterKey dayKey() {
        return new QuotaCounterKey(DIMENSION, QuotaWindow.DAY, QuotaWindow.DAY.windowStart(NOW));
    }

    @Test
    @DisplayName("本地：未超限时 CAS 累加成功并返回累计值")
    void local_withinLimit_shouldIncrement() {
        long[] totals = backend.incrementWithLimit(dayKey(), 1L, 10L, 100L, 1000L).block(WAIT);

        assertArrayEquals(new long[]{1L, 10L}, totals);
        assertArrayEquals(new long[]{1L, 10L}, backend.totals(dayKey()));
    }

    @Test
    @DisplayName("本地：超限请求不落账，返回空 Mono 且计数保持不变")
    void local_overLimit_shouldNotPersist() {
        backend.incrementWithLimit(dayKey(), 1L, 10L, 2L, 1000L).block(WAIT);
        backend.incrementWithLimit(dayKey(), 1L, 10L, 2L, 1000L).block(WAIT);

        assertNull(backend.incrementWithLimit(dayKey(), 1L, 10L, 2L, 1000L).block(WAIT),
                "第 3 次应超限");
        assertArrayEquals(new long[]{2L, 20L}, backend.totals(dayKey()), "超限不得写入");
    }

    @Test
    @DisplayName("本地：token 限额超限同样拒绝")
    void local_overTokenLimit_shouldReject() {
        backend.incrementWithLimit(dayKey(), 1L, 50L, 0L, 60L).block(WAIT);

        assertNull(backend.incrementWithLimit(dayKey(), 1L, 20L, 0L, 60L).block(WAIT));
        assertArrayEquals(new long[]{1L, 50L}, backend.totals(dayKey()));
    }

    @Test
    @DisplayName("本地：限额为 0 表示不限制")
    void local_zeroLimit_meansUnlimited() {
        long[] totals = backend.incrementWithLimit(dayKey(), 5L, 100L, 0L, 0L).block(WAIT);
        assertArrayEquals(new long[]{5L, 100L}, totals);
    }

    @Test
    @DisplayName("本地：并发 CAS 限额=1 时恰好只成功 1 次（无双花）")
    void local_concurrentCas_shouldNotDoubleSpend() throws Exception {
        final int threads = 16;
        final LocalCounterBackend shared = new LocalCounterBackend();
        final QuotaCounterKey key = dayKey();
        final AtomicInteger allowed = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Runnable> jobs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                jobs.add(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        long[] result = shared.incrementWithLimit(key, 1L, 0L, 1L, 0L).block(WAIT);
                        if (result != null) {
                            allowed.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            jobs.forEach(pool::execute);
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "并发用例超时");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, allowed.get(), "限额=1 时并发预留只能成功 1 次");
        assertArrayEquals(new long[]{1L, 0L}, shared.totals(key));
    }

    @Test
    @DisplayName("Redis：脚本形态 — ARGV 携带 maxReq/maxTok，超限返回 OVER 且回滚")
    void redis_scriptShape_andOverRollback() {
        final FakeReactiveRedisTemplate template = new FakeReactiveRedisTemplate();
        final QuotaProperties properties = new QuotaProperties();
        properties.setEnabled(true);
        properties.getDistributed().setEnabled(true);
        final RedisCounterBackend redisBackend = new RedisCounterBackend(template, properties);
        final QuotaCounterKey key = dayKey();
        final String redisKey = redisBackend.keyOf(key);

        long[] ok = redisBackend.incrementWithLimit(key, 1L, 10L, 5L, 100L).block(WAIT);
        assertArrayEquals(new long[]{1L, 10L}, ok);
        assertEquals(RedisCounterBackend.INCREMENT_WITH_LIMIT_SCRIPT, template.lastInvocation().script());
        assertEquals(List.of("1", "10",
                        String.valueOf(redisBackend.ttlSeconds(QuotaWindow.DAY)),
                        "5", "100"),
                template.lastInvocation().args());

        // 第二次会超限（maxReq=1）
        assertNull(redisBackend.incrementWithLimit(key, 1L, 10L, 1L, 100L).block(WAIT));
        long[] values = template.values(redisKey);
        assertArrayEquals(new long[]{1L, 10L}, values, "超限必须回滚，不得双花");
    }
}

package org.unreal.modelrouter.router.ratelimit.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滑动窗口限流器并发准入测试.
 *
 * <p>缺陷 #122：原实现先 {@code size()} 再 {@code offer}（check-then-act），
 * 并发突发下多线程同时观察到 {@code size() < rate} 并全部放行，超过窗口配额。
 * 自旋放行对齐后以小 rate + 大并发触发竞态；旧实现会放行超过 rate。</p>
 */
@DisplayName("SlidingWindowRateLimiter: 并发准入不超限")
class SlidingWindowRateLimiterConcurrencyTest {

    private static final long WINDOW_MS = 1000L;

    private static RateLimitContext context() {
        return new RateLimitContext(
                ModelServiceRegistry.ServiceType.chat, "m", "127.0.0.1", 1, null, null);
    }

    /**
     * 单轮并发突发：自旋对齐后 64 线程同时 tryAcquire，放行数必须 == rate。
     */
    private static int burst(final SlidingWindowRateLimiter limiter,
                             final RateLimitContext ctx,
                             final int threads) throws Exception {
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicBoolean go = new AtomicBoolean(false);
        final AtomicInteger admitted = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        while (!go.get()) {
                            Thread.onSpinWait();
                        }
                        if (limiter.tryAcquire(ctx)) {
                            admitted.incrementAndGet();
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "worker 未全部就绪");
            go.set(true);
            assertTrue(done.await(15, TimeUnit.SECONDS), "并发任务未在超时内完成");
        } finally {
            pool.shutdownNow();
        }
        return admitted.get();
    }

    /**
     * 并发突发放行数不得超过 rate。
     *
     * <p>rate 取 2（小于常见核数），64 线程自旋后同一瞬间进入 tryAcquire，
     * 使多个线程同时观察到空窗口。旧 size-then-offer 实现会放行超过 rate；
     * 多轮独立实例重复以稳定捕获竞态。</p>
     */
    @Test
    @DisplayName("并发突发放行数不超过 rate（同一窗口）")
    void concurrentBurst_shouldNotAdmitMoreThanRate() throws Exception {
        final long rate = 2L;
        final int threads = 64;
        final int trials = 30;

        for (int trial = 0; trial < trials; trial++) {
            final RateLimitConfig config =
                    new RateLimitConfig("sliding-window", rate, rate, "service");
            final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config);
            final int admitted = burst(limiter, context(), threads);
            assertTrue(admitted <= rate,
                    "trial=" + trial + " 同一窗口内放行数 " + admitted + " 超过 rate=" + rate);
            assertEquals(rate, admitted, "trial=" + trial + " 突发足够大时应恰好放行 rate 次");
        }
    }

    @Test
    @DisplayName("窗口过期后可重新放行 rate 次")
    void afterWindowExpires_canAdmitAgain() throws Exception {
        final long rate = 5L;
        final RateLimitConfig config = new RateLimitConfig("sliding-window", rate, rate, "service");
        final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config);
        final RateLimitContext ctx = context();

        int firstWindow = 0;
        for (int i = 0; i < rate + 3; i++) {
            if (limiter.tryAcquire(ctx)) {
                firstWindow++;
            }
        }
        assertEquals(rate, firstWindow);

        Thread.sleep(WINDOW_MS + 50);

        int secondWindow = 0;
        for (int i = 0; i < rate + 3; i++) {
            if (limiter.tryAcquire(ctx)) {
                secondWindow++;
            }
        }
        assertEquals(rate, secondWindow, "窗口过期后应恢复 rate 配额");
    }
}

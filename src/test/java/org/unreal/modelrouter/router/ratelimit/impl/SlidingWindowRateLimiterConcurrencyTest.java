package org.unreal.modelrouter.router.ratelimit.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 *
 * <p>就绪同步用 {@link CyclicBarrier}（await 必然等齐全部参与方，无固定超时就绪竞态），
 * 自旋放行对齐保持不变；反挂起超时随 CPU 核数放大，仅作护栏。</p>
 */
@DisplayName("SlidingWindowRateLimiter: 并发准入不超限")
class SlidingWindowRateLimiterConcurrencyTest {

    private static final long WINDOW_MS = 1000L;

    /**
     * 反挂起超时（秒）：随 CPU 核数放大，只作护栏（卡死时 fail-fast），
     * 不参与就绪/对齐语义；就绪等待本身由 {@link CyclicBarrier#await} 结构性完成。
     */
    private static final long TIMEOUT_SECONDS =
            10L + 2L * Runtime.getRuntime().availableProcessors();

    private static RateLimitContext context() {
        return new RateLimitContext(
                ModelServiceRegistry.ServiceType.chat, "m", "127.0.0.1", 1, null, null);
    }

    /**
     * 单轮并发突发：{@link CyclicBarrier} 结构性就绪 + 自旋放行对齐后
     * {@code threads} 线程同时 tryAcquire，放行数必须 == rate。
     *
     * <p>线程池由调用方持有并跨 trial 复用，避免每轮重建 64 线程的墙钟开销；
     * 每轮仍新建独立 limiter，窗口状态互不影响。</p>
     */
    private static int burst(final SlidingWindowRateLimiter limiter,
                             final RateLimitContext ctx,
                             final int threads,
                             final ExecutorService pool) throws Exception {
        // parties = workers + 调用方：await() 等齐全部参与方才放行，无就绪超时竞态
        final CyclicBarrier ready = new CyclicBarrier(threads + 1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicBoolean go = new AtomicBoolean(false);
        final AtomicInteger admitted = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    ready.await();
                    while (!go.get()) {
                        Thread.onSpinWait();
                    }
                    if (limiter.tryAcquire(ctx)) {
                        admitted.incrementAndGet();
                    }
                } catch (final Exception e) {
                    // 屏障破坏/中断：由调用方的就绪或完成超时显式暴露
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            throw new AssertionError(
                    "就绪屏障超时（readiness timeout，非准入断言失败）：" + threads
                            + " 个 worker 未在 " + TIMEOUT_SECONDS + "s 内全部到达 CyclicBarrier"
                            + "（线程调度延迟/CPU 饱和），请增大 TIMEOUT_SECONDS 或检查执行环境", e);
        } catch (final BrokenBarrierException e) {
            throw new AssertionError(
                    "就绪屏障被破坏（broken barrier，非准入断言失败）：有 worker 在就绪阶段异常退出", e);
        } finally {
            // 无论成败都放行已进入自旋的 worker，避免其在 while(!go) 上挂死
            go.set(true);
        }
        if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError(
                    "并发任务未在 " + TIMEOUT_SECONDS + "s 内完成"
                            + "（completion timeout，非准入断言失败）");
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
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int trial = 0; trial < trials; trial++) {
                final RateLimitConfig config =
                        new RateLimitConfig("sliding-window", rate, rate, "service");
                final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config);
                final int admitted = burst(limiter, context(), threads, pool);
                assertTrue(admitted <= rate,
                        "trial=" + trial + " 同一窗口内放行数 " + admitted + " 超过 rate=" + rate);
                assertEquals(rate, admitted, "trial=" + trial + " 突发足够大时应恰好放行 rate 次");
            }
        } finally {
            pool.shutdownNow();
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

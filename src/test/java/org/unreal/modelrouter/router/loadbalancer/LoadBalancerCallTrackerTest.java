package org.unreal.modelrouter.router.loadbalancer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #688 在途调用记账器测试。
 *
 * <p>此前的完成回调按 {@code serviceType} 另取一个 LB 减计数，与选择时加计数的 LB 不是同一个对象
 * （池策略 / 规则策略 / sticky 包装），导致 {@code least-connections} 的在途数只增不减。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#688 在途调用记账器")
class LoadBalancerCallTrackerTest {

    private static final String KEY = LoadBalancerCallTracker.key("chat", instance("inst-1"));

    @Test
    @DisplayName("同一 LB 的加计数与减计数配对，全部完成后不残留键")
    void trackAndTakePairOnTheSameLoadBalancer() {
        LoadBalancerCallTracker tracker = new LoadBalancerCallTracker();
        CountingLoadBalancer pool = new CountingLoadBalancer();

        tracker.track(KEY, pool);
        tracker.track(KEY, pool);
        assertEquals(2, tracker.outstanding(KEY));

        assertSame(pool, tracker.take(KEY));
        assertSame(pool, tracker.take(KEY));
        assertEquals(0, tracker.outstanding(KEY));
        assertEquals(0, tracker.trackedKeys(), "减到 0 后必须移除记账键，避免无界增长");
        assertNull(tracker.take(KEY), "没有在途记录时返回 null");
    }

    @Test
    @DisplayName("同一实例被两个 LB 选中时，取回的 LB 与加计数一一对应")
    void takePrefersTheLoadBalancerWithMostOutstandingCalls() {
        LoadBalancerCallTracker tracker = new LoadBalancerCallTracker();
        CountingLoadBalancer pool = new CountingLoadBalancer();
        CountingLoadBalancer plain = new CountingLoadBalancer();

        tracker.track(KEY, plain);
        tracker.track(KEY, pool);
        tracker.track(KEY, pool);
        tracker.track(KEY, pool);
        assertEquals(4, tracker.outstanding(KEY));

        int poolTaken = 0;
        int plainTaken = 0;
        for (int i = 0; i < 4; i++) {
            LoadBalancer taken = tracker.take(KEY);
            if (taken == pool) {
                poolTaken++;
            } else if (taken == plain) {
                plainTaken++;
            }
        }

        assertEquals(3, poolTaken, "pool 上的 3 次在途调用必须全部由 pool 取回");
        assertEquals(1, plainTaken, "plain 上的 1 次在途调用必须由 plain 取回");
        assertEquals(0, tracker.outstanding(KEY));
        assertEquals(0, tracker.trackedKeys(), "全部取回后不得残留记账键");
        assertNull(tracker.take(KEY));
    }

    @Test
    @DisplayName("null 键与 null LB 被忽略，outstanding 不抛异常")
    void nullInputsAreIgnored() {
        LoadBalancerCallTracker tracker = new LoadBalancerCallTracker();
        tracker.track(null, new CountingLoadBalancer());
        tracker.track(KEY, null);
        assertEquals(0, tracker.outstanding(KEY));
        assertEquals(0, tracker.outstanding(null));
        assertNull(tracker.take(null));
        assertEquals(0, tracker.trackedKeys());
    }

    @Test
    @DisplayName("并发加/减计数后总量回到 0，且无残留键")
    void concurrentTrackAndTakeBalancesToZero() throws Exception {
        LoadBalancerCallTracker tracker = new LoadBalancerCallTracker();
        CountingLoadBalancer pool = new CountingLoadBalancer();
        int threads = 8;
        int perThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    int taken = 0;
                    for (int i = 0; i < perThread; i++) {
                        tracker.track(KEY, pool);
                        if (tracker.take(KEY) != null) {
                            taken++;
                        }
                    }
                    return taken;
                }));
            }
            start.countDown();
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get(60, TimeUnit.SECONDS);
            }
            assertEquals(threads * perThread, total, "每次 take 都必须取到 LB");
        } finally {
            executor.shutdownNow();
        }
        assertEquals(0, tracker.outstanding(KEY), "并发结束后不得有残留在途计数");
        assertEquals(0, tracker.trackedKeys());
    }

    @Test
    @DisplayName("记账键区分服务类型、实例 ID 与 baseUrl")
    void keyDistinguishesServiceTypeInstanceAndBaseUrl() {
        assertNotNull(KEY);
        assertTrue(KEY.contains("chat"));
        assertTrue(KEY.contains("inst-1"));
        assertTrue(KEY.contains("http://inst-1:8080"));
        assertFalse(KEY.equals(LoadBalancerCallTracker.key("embedding", instance("inst-1"))));

        ModelRouterProperties.ModelInstance noId = instance(null);
        assertNotNull(LoadBalancerCallTracker.key("chat", noId));
    }

    private static ModelRouterProperties.ModelInstance instance(final String id) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setId(id);
        instance.setInstanceId(id);
        instance.setName(id);
        instance.setBaseUrl("http://" + id + ":8080");
        return instance;
    }

    /** 只统计「加/减」次数的假 LB。 */
    private static final class CountingLoadBalancer implements LoadBalancer {

        private final AtomicInteger recorded = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();

        @Override
        public ModelRouterProperties.ModelInstance selectInstance(
                final List<ModelRouterProperties.ModelInstance> instances, final String clientIp) {
            return instances.isEmpty() ? null : instances.get(0);
        }

        @Override
        public void recordCall(final ModelRouterProperties.ModelInstance instance) {
            recorded.incrementAndGet();
        }

        @Override
        public void recordCallComplete(final ModelRouterProperties.ModelInstance instance) {
            completed.incrementAndGet();
        }
    }
}

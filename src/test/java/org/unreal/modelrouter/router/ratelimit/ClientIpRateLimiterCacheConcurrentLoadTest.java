package org.unreal.modelrouter.router.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.impl.TokenBucketRateLimiter;

/**
 * #878：并发首次访问同一个限流键时，只能装载一个限流器。
 */
@DisplayName("#878 客户端限流缓存的并发装载")
class ClientIpRateLimiterCacheConcurrentLoadTest {

    @Test
    @DisplayName("878-001 16 个线程同时首次取同一键，loader 只执行一次且拿到同一实例")
    void concurrentFirstAccessLoadsOnce() throws Exception {
        final ClientIpRateLimiterCache cache = new ClientIpRateLimiterCache();
        final AtomicInteger loads = new AtomicInteger();
        final CountDownLatch startGate = new CountDownLatch(1);
        final Set<RateLimiter> seen = ConcurrentHashMap.newKeySet();
        final int threads = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        final List<java.util.concurrent.Future<RateLimiter>> futures = IntStream.range(0, threads)
                .mapToObj(index -> pool.submit(() -> {
                    startGate.await();
                    final RateLimiter limiter = cache.get(ServiceType.chat, RateLimitKeyDimension.CLIENT_IP,
                            "203.0.113.9", key -> {
                                loads.incrementAndGet();
                                sleep(30L);
                                return new TokenBucketRateLimiter(new RateLimitConfig("token-bucket", 10, 10, "client"));
                            });
                    seen.add(limiter);
                    return limiter;
                }))
                
                .toList();

        startGate.countDown();
        for (final java.util.concurrent.Future<RateLimiter> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertEquals(1, loads.get(), "并发首次访问只应装载一个限流器（旧实现会装载多个，各自独立计票）");
        assertEquals(1, seen.size(), "所有调用方必须拿到同一个限流器实例");
        assertNotNull(seen.iterator().next());
    }

    @Test
    @DisplayName("878-002 第二次访问命中缓存，不再调用 loader")
    void secondAccessUsesCachedLimiter() {
        final ClientIpRateLimiterCache cache = new ClientIpRateLimiterCache();
        final AtomicInteger loads = new AtomicInteger();
        final RateLimiter first = cache.get(ServiceType.chat, RateLimitKeyDimension.CLIENT_IP, "198.51.100.7",
                key -> {
                    loads.incrementAndGet();
                    return new TokenBucketRateLimiter(new RateLimitConfig("token-bucket", 5, 5, "client"));
                });
        final RateLimiter second = cache.get(ServiceType.chat, RateLimitKeyDimension.CLIENT_IP, "198.51.100.7",
                key -> {
                    loads.incrementAndGet();
                    return new TokenBucketRateLimiter(new RateLimitConfig("token-bucket", 5, 5, "client"));
                });

        assertSame(first, second, "同一键第二次访问应复用已装载的限流器");
        assertEquals(1, loads.get());
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

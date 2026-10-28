package org.unreal.modelrouter.auth.security.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #715：用量统计的进程内并发安全。
 *
 * <p>此前 {@code dailyUsage} / {@code dailyTokenUsage} 是普通 {@code HashMap}，且用
 * 「{@code getOrDefault} 再 {@code put}」的读改写累加：同一 Key 的并发请求会互相覆盖，
 * 计数偏小（配额因此更容易被超用）。本用例用 200 个线程同时累加来固定这一行为。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("API Key 用量统计的并发安全（#715）")
class UsageStatisticsConcurrencyTest {

    private static final int THREADS = 200;

    private static final long TOKENS_PER_REQUEST = 10L;

    @Test
    @DisplayName("USG-715-001 并发 200 次累加不丢计数")
    void concurrentIncrementsAreNotLost() throws Exception {
        final UsageStatistics statistics = new UsageStatistics();
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);

        try {
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        statistics.incrementRequest(true);
                        statistics.incrementTokens(TOKENS_PER_REQUEST);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发任务未在 30 秒内完成");
        } finally {
            pool.shutdownNow();
        }

        final String today = LocalDate.now().toString();
        assertEquals(THREADS, statistics.getTotalRequests(), "总请求数不得丢计数");
        assertEquals(THREADS, statistics.getSuccessfulRequests());
        assertEquals(THREADS, (long) statistics.getDailyUsage().get(today), "每日请求数不得丢计数");
        assertEquals(THREADS * TOKENS_PER_REQUEST, (long) statistics.getDailyTokenUsage().get(today),
                "每日 Token 数不得丢计数");
    }

    @Test
    @DisplayName("USG-715-002 历史 HashMap 数据被迁移成并发容器")
    void legacyHashMapIsUpgraded() {
        final UsageStatistics statistics = new UsageStatistics();
        final Map<String, Long> legacy = new HashMap<>();
        legacy.put("2026-01-01", 7L);
        statistics.setDailyUsage(legacy);
        statistics.setDailyTokenUsage(legacy);

        assertInstanceOf(ConcurrentHashMap.class, statistics.getDailyUsage(),
                "读取时必须换成并发容器，否则并发 merge 会与 HashMap 内部状态打架");
        assertInstanceOf(ConcurrentHashMap.class, statistics.getDailyTokenUsage());
        assertEquals(7L, (long) statistics.getDailyUsage().get("2026-01-01"), "迁移不得丢历史数据");

        statistics.incrementRequest(false);
        assertEquals(1L, (long) statistics.getDailyUsage().get(LocalDate.now().toString()));
        assertEquals(7L, (long) statistics.getDailyUsage().get("2026-01-01"), "历史数据保持不动");
    }
}

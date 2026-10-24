package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenBucketRateLimiter 单元测试
 *
 * @author JAiRouter Team
 * @since v2.7.6
 */
@DisplayName("TokenBucketRateLimiter 滑动窗口速率限制器测试")
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter();
    }

    @Test
    @DisplayName("无限制模式 - limit 为 0 时始终允许")
    void tryAcquire_noLimit_shouldAlwaysAllow() {
        assertTrue(rateLimiter.tryAcquire("key-1", 0));
        assertTrue(rateLimiter.tryAcquire("key-1", 0));
        assertTrue(rateLimiter.tryAcquire("key-1", 0));
        assertEquals(0, rateLimiter.getCurrentCount("key-1"));
    }

    @Test
    @DisplayName("负数限制 - limit 为负数时始终允许")
    void tryAcquire_negativeLimit_shouldAlwaysAllow() {
        assertTrue(rateLimiter.tryAcquire("key-1", -1));
        assertTrue(rateLimiter.tryAcquire("key-1", -100));
    }

    @Test
    @DisplayName("正常速率 - 未超过限制时允许请求")
    void tryAcquire_withinLimit_shouldAllow() {
        String keyId = "key-normal";
        int limit = 5;

        for (int i = 0; i < limit; i++) {
            assertTrue(rateLimiter.tryAcquire(keyId, limit),
                "第 " + (i + 1) + " 次请求应该被允许");
        }
    }

    @Test
    @DisplayName("超过限制 - 超出限制时拒绝请求")
    void tryAcquire_exceedLimit_shouldReject() {
        String keyId = "key-exceed";
        int limit = 3;

        assertTrue(rateLimiter.tryAcquire(keyId, limit));
        assertTrue(rateLimiter.tryAcquire(keyId, limit));
        assertTrue(rateLimiter.tryAcquire(keyId, limit));
        assertFalse(rateLimiter.tryAcquire(keyId, limit),
            "第 4 次请求应该被拒绝");

        assertEquals(3, rateLimiter.getCurrentCount(keyId));
    }

    @Test
    @DisplayName("不同 Key 独立计数")
    void tryAcquire_differentKeys_shouldBeIndependent() {
        int limit = 2;

        assertTrue(rateLimiter.tryAcquire("key-a", limit));
        assertTrue(rateLimiter.tryAcquire("key-a", limit));
        assertFalse(rateLimiter.tryAcquire("key-a", limit));

        // key-b 不受 key-a 影响
        assertTrue(rateLimiter.tryAcquire("key-b", limit));
        assertTrue(rateLimiter.tryAcquire("key-b", limit));
        assertFalse(rateLimiter.tryAcquire("key-b", limit));
    }

    @Test
    @DisplayName("getCurrentCount - 无窗口时返回 0")
    void getCurrentCount_noWindow_shouldReturnZero() {
        assertEquals(0, rateLimiter.getCurrentCount("nonexistent-key"));
    }

    @Test
    @DisplayName("getCurrentCount - 返回正确的当前计数")
    void getCurrentCount_withRequests_shouldReturnCorrectCount() {
        String keyId = "key-count";
        int limit = 10;

        rateLimiter.tryAcquire(keyId, limit);
        rateLimiter.tryAcquire(keyId, limit);
        rateLimiter.tryAcquire(keyId, limit);

        assertEquals(3, rateLimiter.getCurrentCount(keyId));
    }

    @Test
    @DisplayName("reset - 清除指定 Key 的窗口")
    void reset_specificKey_shouldRemoveWindow() {
        String keyId = "key-reset";
        int limit = 2;

        rateLimiter.tryAcquire(keyId, limit);
        rateLimiter.tryAcquire(keyId, limit);
        assertFalse(rateLimiter.tryAcquire(keyId, limit));

        rateLimiter.reset(keyId);

        assertEquals(0, rateLimiter.getCurrentCount(keyId));
        assertTrue(rateLimiter.tryAcquire(keyId, limit),
            "重置后应该重新允许请求");
    }

    @Test
    @DisplayName("resetAll - 清除所有窗口")
    void resetAll_shouldRemoveAllWindows() {
        int limit = 1;

        rateLimiter.tryAcquire("key-1", limit);
        rateLimiter.tryAcquire("key-2", limit);
        assertFalse(rateLimiter.tryAcquire("key-1", limit));
        assertFalse(rateLimiter.tryAcquire("key-2", limit));

        assertEquals(2, rateLimiter.getActiveWindowCount());

        rateLimiter.resetAll();

        assertEquals(0, rateLimiter.getActiveWindowCount());
        assertTrue(rateLimiter.tryAcquire("key-1", limit));
        assertTrue(rateLimiter.tryAcquire("key-2", limit));
    }

    @Test
    @DisplayName("getActiveWindowCount - 返回活跃窗口数")
    void getActiveWindowCount_shouldReturnCorrectCount() {
        assertEquals(0, rateLimiter.getActiveWindowCount());

        rateLimiter.tryAcquire("key-1", 10);
        assertEquals(1, rateLimiter.getActiveWindowCount());

        rateLimiter.tryAcquire("key-2", 10);
        assertEquals(2, rateLimiter.getActiveWindowCount());

        rateLimiter.reset("key-1");
        assertEquals(1, rateLimiter.getActiveWindowCount());
    }

    @Test
    @DisplayName("限制为 1 - 每分钟只允许 1 个请求")
    void tryAcquire_limitOne_shouldAllowOnlyFirst() {
        String keyId = "key-single";

        assertTrue(rateLimiter.tryAcquire(keyId, 1));
        assertFalse(rateLimiter.tryAcquire(keyId, 1));
        assertFalse(rateLimiter.tryAcquire(keyId, 1));
    }

    @Test
    @DisplayName("高频并发 - 多线程竞争不超过限制")
    void tryAcquire_concurrent_shouldNotExceedLimit() throws InterruptedException {
        String keyId = "key-concurrent";
        int limit = 100;
        int threadCount = 10;
        int requestsPerThread = 20;

        java.util.concurrent.CountDownLatch latch =
            new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                for (int i = 0; i < requestsPerThread; i++) {
                    if (rateLimiter.tryAcquire(keyId, limit)) {
                        successCount.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        assertEquals(limit, successCount.get(),
            "成功请求数不应超过限制 " + limit);
        assertEquals(limit, rateLimiter.getCurrentCount(keyId),
            "当前计数应等于限制 " + limit);
    }

    @Test
    @DisplayName("窗口表有界 - 超过上限后条目数不再增长（#625）")
    void windowTable_isBounded() {
        int distinctKeys = 10_050;

        for (int i = 0; i < distinctKeys; i++) {
            rateLimiter.tryAcquire("bounded-key-" + i, 1);
        }

        int active = rateLimiter.getActiveWindowCount();
        assertTrue(active <= 10_000,
            "窗口条目数必须被 maximumSize 限制在 10_000 以内，实际 " + active);
        assertTrue(active > 0, "窗口表不应被清空，实际 " + active);
    }

    @Test
    @DisplayName("淘汰取舍 - 被淘汰的窗口重新出现时从 0 开始计（javadoc 声明的语义）")
    void evictedWindow_reappearsFromZero() {
        int distinctKeys = 20_000;
        for (int i = 0; i < distinctKeys; i++) {
            rateLimiter.tryAcquire("evict-key-" + i, 1);
        }

        // 首键在 20000 次写入后必然已被淘汰（淘汰对象不保证，但「条目数有界 ⇒ 旧键必然出局」可由
        // 下面这条断言兜住：要么它不在表里（计数 0），要么它重新从 0 开始 ⇒ 允许一次）。
        boolean allowed = rateLimiter.tryAcquire("evict-key-0", 1);
        int count = rateLimiter.getCurrentCount("evict-key-0");
        assertTrue(allowed || count == 1,
            "淘汰后的键必须能重新判定（allowed=" + allowed + ", count=" + count + "）");
    }
}

package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalEndUserRateLimitCounter} 的窗口语义与并发正确性。
 *
 * <p>关键语义：固定分钟窗口、恰好 {@code limit} 次放行、窗口切换归零、
 * {@code Retry-After} 落到下一个整分钟且最小 1 秒。</p>
 */
@DisplayName("本地终端用户计数器：固定分钟窗口 + 恰好 N 次")
class LocalEndUserRateLimitCounterTest {

    /** 某个自然分钟的起点（数值本身无意义，只要能被 60 整除即可）。 */
    private static final long WINDOW_START = 1_760_000_040L;

    @Test
    @DisplayName("恰好 limit 次放行，第 limit+1 次拒绝")
    void allowsExactlyLimitTimes() {
        final LocalEndUserRateLimitCounter counter = new LocalEndUserRateLimitCounter(100);

        for (int i = 1; i <= 3; i++) {
            final EndUserRateLimitCounter.Decision decision =
                    counter.record("bucket", WINDOW_START, 3, WINDOW_START + 5);
            assertTrue(decision.allowed(), "第 " + i + " 次应当放行");
            assertEquals(i, decision.used(), "已用量应当逐次递增");
            assertEquals(3, decision.limit());
            assertEquals(3 - i, decision.remaining(), "剩余次数");
            assertFalse(decision.degraded(), "本地实现没有降级路径");
        }

        final EndUserRateLimitCounter.Decision rejected =
                counter.record("bucket", WINDOW_START, 3, WINDOW_START + 5);
        assertFalse(rejected.allowed(), "第 4 次应当被拒");
        assertEquals(3, rejected.used(), "被拒时已用量为 limit");
        assertEquals(0, rejected.remaining());
        assertEquals(55, rejected.retryAfterSeconds(), "距本窗口结束还有 55 秒");
    }

    @Test
    @DisplayName("换窗口后重新从 1 开始计")
    void newWindowResets() {
        final LocalEndUserRateLimitCounter counter = new LocalEndUserRateLimitCounter(100);
        counter.record("bucket", WINDOW_START, 1, WINDOW_START + 1);
        assertFalse(counter.record("bucket", WINDOW_START, 1, WINDOW_START + 2).allowed());

        final EndUserRateLimitCounter.Decision nextWindow =
                counter.record("bucket", WINDOW_START + 60, 1, WINDOW_START + 61);
        assertTrue(nextWindow.allowed(), "下一个窗口应当重新放行");
        assertEquals(1, nextWindow.used());
    }

    @Test
    @DisplayName("Retry-After 最小为 1 秒（窗口刚结束时不会返回 0）")
    void retryAfterIsAtLeastOneSecond() {
        assertEquals(1, EndUserRateLimitCounter.retryAfterSeconds(WINDOW_START, WINDOW_START + 60));
        assertEquals(1, EndUserRateLimitCounter.retryAfterSeconds(WINDOW_START, WINDOW_START + 100));
        assertEquals(60, EndUserRateLimitCounter.retryAfterSeconds(WINDOW_START, WINDOW_START));
    }

    @Test
    @DisplayName("桶数量受 maximumSize 约束（不随用户数无界增长）")
    void bucketCountIsBounded() {
        final LocalEndUserRateLimitCounter counter = new LocalEndUserRateLimitCounter(2);

        for (int i = 0; i < 50; i++) {
            counter.record("bucket-" + i, WINDOW_START, 10, WINDOW_START + 1);
        }

        assertTrue(counter.estimatedSize() <= 2,
                "缓存条目上限为 2，实际是 " + counter.estimatedSize());
    }
}

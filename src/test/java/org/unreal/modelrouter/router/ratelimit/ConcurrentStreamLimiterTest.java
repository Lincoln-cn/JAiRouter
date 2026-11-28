package org.unreal.modelrouter.router.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ConcurrentStreamLimiter} 的行为测试（issue #1246）。
 *
 * <p>只测限制器本身：名额的占用、归还、幂等与主体隔离。请求链路上的挂载由
 * {@code ServiceRequestHandlerConcurrencyLeaseTest} 覆盖。</p>
 */
@DisplayName("ConcurrentStreamLimiter（单主体并发上限）")
class ConcurrentStreamLimiterTest {

    @Test
    @DisplayName("配置为 0 时不限制，租约恒放行且计数不变")
    void zeroThresholdDisablesLimiting() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(0);

        for (int i = 0; i < 100; i++) {
            final ConcurrentStreamLimiter.Lease lease = limiter.acquire("key-a");
            assertTrue(lease.granted(), "不限时第 " + i + " 次申请也必须放行");
            lease.close();
        }

        assertEquals(0, limiter.activeCount("key-a"));
        assertEquals(0, limiter.rejectedTotal());
    }

    @Test
    @DisplayName("超过上限的申请被拒绝，且不把计数抬上去")
    void rejectsBeyondThreshold() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(2);

        assertTrue(limiter.acquire("key-a").granted());
        assertTrue(limiter.acquire("key-a").granted());

        final ConcurrentStreamLimiter.Lease rejected = limiter.acquire("key-a");
        assertFalse(rejected.granted(), "第三个并发请求必须被拒绝");
        assertEquals(2, limiter.activeCount("key-a"), "被拒绝的申请不能留下占位");
        assertEquals(1, limiter.rejectedTotal());
    }

    @Test
    @DisplayName("归还名额后可以再次申请")
    void releasedSlotIsReusable() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);

        final ConcurrentStreamLimiter.Lease first = limiter.acquire("key-a");
        assertTrue(first.granted());
        assertFalse(limiter.acquire("key-a").granted());

        first.close();
        assertEquals(0, limiter.activeCount("key-a"));

        final ConcurrentStreamLimiter.Lease second = limiter.acquire("key-a");
        assertTrue(second.granted(), "归还之后应能重新申请到名额");
        second.close();
    }

    @Test
    @DisplayName("重复关闭不重复扣减")
    void closeIsIdempotent() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);

        final ConcurrentStreamLimiter.Lease lease = limiter.acquire("key-a");
        assertTrue(lease.granted());

        lease.close();
        lease.close();
        lease.close();

        assertEquals(0, limiter.activeCount("key-a"), "重复关闭不能把计数减到负数");
        assertTrue(limiter.acquire("key-a").granted());
    }

    @Test
    @DisplayName("不同调用主体的名额互不影响")
    void differentCallersAreIndependent() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);

        assertTrue(limiter.acquire("key-a").granted());
        assertFalse(limiter.acquire("key-a").granted());
        assertTrue(limiter.acquire("key-b").granted(), "另一个主体的名额不该被占用");
        assertTrue(limiter.acquire("jwt:alice").granted());
    }

    @Test
    @DisplayName("调用主体为空时不做限制（无法归属的请求不该被误伤）")
    void blankCallerIsNotLimited() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);

        assertTrue(limiter.acquire(null).granted());
        assertTrue(limiter.acquire(null).granted());
        assertTrue(limiter.acquire("  ").granted());
        assertEquals(0, limiter.rejectedTotal());
    }

    @Test
    @DisplayName("被拒绝的租约关闭时不影响在途计数")
    void rejectingDoesNotReleaseSomeoneElsesSlot() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);

        final ConcurrentStreamLimiter.Lease granted = limiter.acquire("key-a");
        final ConcurrentStreamLimiter.Lease rejected = limiter.acquire("key-a");
        assertFalse(rejected.granted());

        rejected.close();

        assertEquals(1, limiter.activeCount("key-a"), "关闭一个被拒绝的租约不能释放别人的名额");
        granted.close();
        assertEquals(0, limiter.activeCount("key-a"));
    }
}

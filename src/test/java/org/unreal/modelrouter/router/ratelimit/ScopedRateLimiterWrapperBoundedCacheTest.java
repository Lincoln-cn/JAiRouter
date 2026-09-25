package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #123: ScopedRateLimiterWrapper 内部 map 原为无界 ConcurrentHashMap，
 * key 来自 client-ip/model/instance/rule 等外部高基数输入，可被撑爆。
 * 本测试插入远超上限的 key，断言缓存大小被限制在 Caffeine maximumSize 内。
 */
@DisplayName("ScopedRateLimiterWrapper: 作用域缓存有界")
class ScopedRateLimiterWrapperBoundedCacheTest {

    private static final int MAX_SIZE = 10000;

    private static RateLimitContext ctxWithIp(final String ip) {
        return new RateLimitContext(ModelServiceRegistry.ServiceType.chat, "m", ip, 1, null, null);
    }

    /** 极简限流器工厂产物（手写 fake） */
    private static RateLimiter simpleLimiter(final RateLimitConfig config) {
        return new RateLimiter() {
            @Override
            public boolean tryAcquire(final RateLimitContext context) {
                return true;
            }

            @Override
            public RateLimitConfig getConfig() {
                return config;
            }
        };
    }

    @Test
    @DisplayName("插入远超上限的 client-ip key 后缓存大小不超过 maximumSize")
    void manyScopeKeys_shouldStayWithinBound() {
        RateLimitConfig config = new RateLimitConfig("token-bucket", 10, 10, "client-ip");
        ScopedRateLimiterWrapper wrapper = new ScopedRateLimiterWrapper(config, ScopedRateLimiterWrapperBoundedCacheTest::simpleLimiter);

        final int inserts = MAX_SIZE * 2;
        for (int i = 0; i < inserts; i++) {
            String ip = "10." + ((i >>> 16) & 0xff) + "." + ((i >>> 8) & 0xff) + "." + (i & 0xff);
            assertTrue(wrapper.tryAcquire(ctxWithIp(ip)));
        }

        wrapper.cleanUp();
        long size = wrapper.size();
        assertTrue(size <= MAX_SIZE,
                "缓存大小 " + size + " 超过上限 " + MAX_SIZE + "（已插入 " + inserts + " 个 key）");
        // 旧实现为无界 map，此处会等于 inserts；有界后必须远小于插入数
        assertTrue(size < inserts, "缓存大小应远小于插入 key 数");
    }
}

package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.checker.RateLimiterCleanupChecker;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

import static org.mockito.Mockito.*;

/**
 * 限流器清理检查器测试
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterCleanupCheckerTest {

    @Mock
    private RateLimitManager rateLimitManager;

    private RateLimiterCleanupChecker cleanupChecker;

    @BeforeEach
    void setUp() {
        cleanupChecker = new RateLimiterCleanupChecker(rateLimitManager);
    }

    @Test
    void testCleanupInactiveRateLimiters() {
        // 执行清理任务
        cleanupChecker.cleanupInactiveRateLimiters();

        // 验证调用了RateLimitManager的清理方法
        verify(rateLimitManager, times(1)).cleanupInactiveClientIpLimiters();
    }

    @Test
    void testCleanupInactiveRateLimitersWithException() {
        // 模拟RateLimitManager抛出异常
        doThrow(new RuntimeException("清理失败")).when(rateLimitManager).cleanupInactiveClientIpLimiters();

        // 执行清理任务，不应该抛出异常
        cleanupChecker.cleanupInactiveRateLimiters();

        // 验证仍然调用了清理方法
        verify(rateLimitManager, times(1)).cleanupInactiveClientIpLimiters();
    }
}
package org.unreal.modelrouter.monitor.tracing.wrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.router.ratelimit.RateLimiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RateLimiterTracingWrapper 测试
 *
 * 验证监控指标委托转发：
 * - getRemainingCapacity 必须转发到底层限流器（而非返回接口默认值 -1）
 * - getUsageRatio 必须转发到底层限流器（而非返回接口默认值 -1）
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterTracingWrapperTest {

    @Mock
    private RateLimiter delegate;

    @Mock
    private StructuredLogger structuredLogger;

    private RateLimiterTracingWrapper wrapper;

    @BeforeEach
    void setUp() {
        wrapper = new RateLimiterTracingWrapper(delegate, structuredLogger);
    }

    @Test
    void testGetRemainingCapacityDelegatesToUnderlyingRateLimiter() {
        // 底层限流器返回具体剩余容量
        when(delegate.getRemainingCapacity()).thenReturn(30L);

        long remaining = wrapper.getRemainingCapacity();

        assertEquals(30L, remaining);
        verify(delegate, times(1)).getRemainingCapacity();
    }

    @Test
    void testGetUsageRatioDelegatesToUnderlyingRateLimiter() {
        // 底层限流器返回具体使用率
        when(delegate.getUsageRatio()).thenReturn(0.4);

        double ratio = wrapper.getUsageRatio();

        assertEquals(0.4, ratio, 0.0001);
        verify(delegate, times(1)).getUsageRatio();
    }
}

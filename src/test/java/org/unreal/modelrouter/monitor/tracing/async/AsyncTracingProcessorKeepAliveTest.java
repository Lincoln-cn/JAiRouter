package org.unreal.modelrouter.monitor.tracing.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import reactor.core.scheduler.Scheduler;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * tracing 异步线程池的 {@code keep-alive} 真的生效（issue #220）。
 *
 * <p>起因：{@code TracingPerformanceConfig.ThreadPoolConfig.keepAlive} 一直被绑定，但**没有任何
 * 消费方** —— 运维按 {@code jairouter.tracing.performance.thread-pool.keep-alive} 调参静默无效。
 * 本测试盯住修复后的接线：该值必须出现在 Reactor 调度器的空闲线程 TTL 上。</p>
 *
 * <p>为什么用反射：Reactor 没有公开读 TTL 的接口（boundedElastic 只有
 * {@code newBoundedElastic(cap, queue, prefix, ttlSeconds)} 这一侧）。要机械地守住「字段有消费方」，
 * 只能读它内部的 {@code BoundedElasticScheduler.ttlMillis}。Reactor 升级若改了字段名，会在这里
 * 以 {@code NoSuchFieldException} 变红 —— 那正是提醒同步本测试的信号，而不是被忽略的失败。</p>
 */
@DisplayName("tracing 异步线程池的 keep-alive 生效（issue #220）")
class AsyncTracingProcessorKeepAliveTest {

    @Test
    @DisplayName("配置的 keep-alive 传到 Reactor 的空闲线程 TTL 上")
    void keepAliveReachesReactorTtl() throws Exception {
        assertEquals(5_000L, ttlMillisFor(Duration.ofSeconds(5)));
        assertEquals(90_000L, ttlMillisFor(Duration.ofSeconds(90)));
    }

    @Test
    @DisplayName("默认 60s 与 Reactor 自己的默认 TTL 相同 —— 接上消费方不改变默认行为")
    void defaultKeepAliveMatchesReactorDefault() throws Exception {
        Duration defaultKeepAlive = new TracingConfiguration()
                .getPerformance().getThreadPool().getKeepAlive();

        assertEquals(Duration.ofSeconds(60), defaultKeepAlive, "前置：类默认值应仍是 60s");
        assertEquals(60_000L, ttlMillisFor(defaultKeepAlive),
                "Reactor 的 BoundedElasticScheduler.DEFAULT_TTL_SECONDS 也是 60，接上之后默认行为逐字不变");
    }

    @Test
    @DisplayName("非法值（0 / 负数）夹到 1 秒，不会让处理器构造失败")
    void clampsNonPositiveToOneSecond() {
        assertEquals(1, AsyncTracingProcessor.ttlSeconds(Duration.ZERO));
        assertEquals(1, AsyncTracingProcessor.ttlSeconds(Duration.ofSeconds(-30)));
    }

    // ==================== 反射读接线结果 ====================

    private static long ttlMillisFor(final Duration keepAlive) throws Exception {
        TracingConfiguration configuration = new TracingConfiguration();
        configuration.getPerformance().getThreadPool().setKeepAlive(keepAlive);

        Scheduler scheduler = schedulerOf(new AsyncTracingProcessor(configuration));
        try {
            return ttlMillis(scheduler);
        } finally {
            scheduler.dispose();
        }
    }

    private static Scheduler schedulerOf(final AsyncTracingProcessor processor) throws Exception {
        Field field = AsyncTracingProcessor.class.getDeclaredField("processingScheduler");
        field.setAccessible(true);
        return (Scheduler) field.get(processor);
    }

    private static long ttlMillis(final Scheduler scheduler) throws Exception {
        Field field = scheduler.getClass().getDeclaredField("ttlMillis");
        field.setAccessible(true);
        return field.getLong(scheduler);
    }
}

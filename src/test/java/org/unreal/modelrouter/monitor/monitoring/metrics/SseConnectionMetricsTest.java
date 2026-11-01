package org.unreal.modelrouter.monitor.monitoring.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * #947：SSE 活跃连接计数。
 *
 * <p>此前没有任何「当前有多少条长连接」的数字，{@code performance.md} 里的
 * 「活跃连接数 &lt; 1000」阈值无法对照。</p>
 */
@DisplayName("SseConnectionMetrics：活跃连接计数与 gauge")
class SseConnectionMetricsTest {

    @Test
    @DisplayName("订阅 +1、取消 -1，gauge 与计数同步")
    void tracksSubscriptionLifecycle() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final SseConnectionMetrics metrics = new SseConnectionMetrics(registry);
        final Flux<String> tracked = metrics.track(Flux.never(), "unit-test");

        assertEquals(0L, metrics.active("unit-test"), "未订阅时应为 0");

        final Disposable first = tracked.subscribe();
        final Disposable second = tracked.subscribe();
        assertEquals(2L, metrics.active("unit-test"), "两个订阅者 = 两条连接");
        assertEquals(2.0, gaugeValue(registry, "unit-test"), 0.001, "gauge 应反映当前连接数");

        first.dispose();
        assertEquals(1L, metrics.active("unit-test"));
        second.dispose();
        assertEquals(0L, metrics.active("unit-test"));
        assertEquals(0.0, gaugeValue(registry, "unit-test"), 0.001);
    }

    @Test
    @DisplayName("流正常完成时同样递减（doFinally 覆盖完成路径）")
    void decrementsOnCompletion() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final SseConnectionMetrics metrics = new SseConnectionMetrics(registry);

        metrics.track(Flux.just("tick", "tock"), "completed").blockLast();

        assertEquals(0L, metrics.active("completed"), "完成路径也必须把计数减回去");
    }

    @Test
    @DisplayName("不同端点各自独立计数，互不影响")
    void keepsEndpointsIndependent() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final SseConnectionMetrics metrics = new SseConnectionMetrics(registry);

        final Disposable a = metrics.track(Flux.never(), "a").subscribe();
        final Disposable b = metrics.track(Flux.never(), "b").subscribe();

        assertEquals(1L, metrics.active("a"));
        assertEquals(1L, metrics.active("b"));
        assertEquals(0L, metrics.active("never-seen"), "未知端点返回 0 而不是抛错");

        a.dispose();
        b.dispose();
    }

    private static double gaugeValue(final SimpleMeterRegistry registry, final String endpoint) {
        final Gauge gauge = registry.find(SseConnectionMetrics.METRIC).tag("endpoint", endpoint).gauge();
        assertNotNull(gauge, "端点 gauge 应已注册");
        return gauge.value();
    }
}

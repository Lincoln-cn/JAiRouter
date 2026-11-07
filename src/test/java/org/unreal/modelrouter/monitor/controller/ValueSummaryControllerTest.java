package org.unreal.modelrouter.monitor.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

/** {@link ValueSummaryController} 的测试. */
class ValueSummaryControllerTest {

    private static Map<String, Object> body(final ValueSummaryController controller) {
        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.valueSummary();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getData();
    }

    @Test
    @DisplayName("没有指标注册表时不报错，全部返回 0")
    void noRegistryReturnsZeros() {
        final Map<String, Object> data = body(new ValueSummaryController(null));
        assertThat(((Number) data.get("semanticCacheHits")).longValue()).isZero();
        assertThat(data.get("semanticCacheHitRate")).isEqualTo(0.0);
        assertThat(((Number) data.get("guardAborts")).longValue()).isZero();
        assertThat((Map<?, ?>) data.get("backendP95")).isEmpty();
    }

    @Test
    @DisplayName("命中率 = 命中 / (命中 + 未命中)，节省次数等于命中次数")
    void hitRateIsComputedFromCounters() {
        final MeterRegistry registry = new SimpleMeterRegistry();
        for (int i = 0; i < 3; i++) {
            Counter.builder("jairouter.semantic.cache.hits").register(registry).increment();
        }
        Counter.builder("jairouter.semantic.cache.misses").register(registry).increment();

        final Map<String, Object> data = body(new ValueSummaryController(registry));
        assertThat(((Number) data.get("semanticCacheHits")).longValue()).isEqualTo(3L);
        assertThat(((Number) data.get("semanticCacheMisses")).longValue()).isEqualTo(1L);
        assertThat((Double) data.get("semanticCacheHitRate")).isCloseTo(0.75, within(0.0001));
        assertThat(((Number) data.get("savedUpstreamCalls")).longValue()).isEqualTo(3L);
    }

    @Test
    @DisplayName("没有命中也没有未命中时命中率是 0 而不是 NaN")
    void hitRateIsZeroWhenNoTraffic() {
        final Map<String, Object> data = body(new ValueSummaryController(new SimpleMeterRegistry()));
        assertThat(data.get("semanticCacheHitRate")).isEqualTo(0.0);
        assertThat((Double) data.get("semanticCacheHitRate")).isNotNaN();
    }

    @Test
    @DisplayName("护栏拦截按 action 标签分开计数")
    void guardCountersAreSplitByAction() {
        final MeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("jairouter.guard.decisions").tag("action", "REDACT").register(registry).increment(2);
        Counter.builder("jairouter.guard.decisions").tag("action", "ABORT").register(registry).increment();

        final Map<String, Object> data = body(new ValueSummaryController(registry));
        assertThat(((Number) data.get("guardRedactions")).longValue()).isEqualTo(2L);
        assertThat(((Number) data.get("guardAborts")).longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("出站耗时按实例标签聚合，取同实例的最大值")
    void backendLatencyIsGroupedByInstance() {
        final MeterRegistry registry = new SimpleMeterRegistry();
        final Timer fast = Timer.builder("jairouter.backend.call.duration")
                .tag("instance", "qwen3:4b@127.0.0.1:18080").register(registry);
        fast.record(Duration.ofMillis(50));
        final Timer slow = Timer.builder("jairouter.backend.call.duration")
                .tag("instance", "qwen3:4b@127.0.0.1:18080").register(registry);
        slow.record(Duration.ofMillis(400));

        final Map<String, Object> data = body(new ValueSummaryController(registry));
        @SuppressWarnings("unchecked")
        final Map<String, Double> byInstance = (Map<String, Double>) data.get("backendP95");
        assertThat(byInstance).containsKey("qwen3:4b@127.0.0.1:18080");
        assertThat(byInstance.get("qwen3:4b@127.0.0.1:18080")).isGreaterThanOrEqualTo(400.0);
    }

    private static org.assertj.core.data.Offset<Double> within(final double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}

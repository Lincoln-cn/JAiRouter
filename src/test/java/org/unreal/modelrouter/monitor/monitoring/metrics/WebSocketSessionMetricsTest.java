package org.unreal.modelrouter.monitor.monitoring.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * WebSocket 会话计数（#947）。
 *
 * @since 3.6.0
 */
@DisplayName("WebSocket 活跃会话计数")
class WebSocketSessionMetricsTest {

    @Test
    @DisplayName("会话未结束时计为 1，结束后归零")
    void countsUntilSessionTerminates() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final WebSocketSessionMetrics metrics = new WebSocketSessionMetrics(registry);

        final Mono<Void> session = metrics.track(Mono.never(), "circuit-breaker-monitor");
        final reactor.core.Disposable subscription = session.subscribe();

        assertThat(metrics.active("circuit-breaker-monitor")).isEqualTo(1L);
        assertThat(registry.find(WebSocketSessionMetrics.METRIC)
                .tag("endpoint", "circuit-breaker-monitor")
                .gauge().value()).isEqualTo(1.0);

        subscription.dispose();

        assertThat(metrics.active("circuit-breaker-monitor")).isZero();
    }

    @Test
    @DisplayName("正常完成与异常结束都要释放计数")
    void releasesCountingOnBothTerminalSignals() {
        final WebSocketSessionMetrics metrics = new WebSocketSessionMetrics(new SimpleMeterRegistry());

        metrics.track(Mono.empty(), "routing-monitor").block();
        assertThat(metrics.active("routing-monitor")).isZero();

        assertThatCode(() -> metrics.track(Mono.error(new IllegalStateException("boom")), "routing-monitor")
                .onErrorResume(e -> Mono.empty())
                .block()).doesNotThrowAnyException();
        assertThat(metrics.active("routing-monitor")).isZero();
    }

    @Test
    @DisplayName("不同端点各自计数，互不影响")
    void keepsEndpointsSeparate() {
        final WebSocketSessionMetrics metrics = new WebSocketSessionMetrics(new SimpleMeterRegistry());

        final reactor.core.Disposable first = metrics.track(Mono.never(), "circuit-breaker-monitor").subscribe();
        final reactor.core.Disposable second = metrics.track(Mono.never(), "routing-monitor").subscribe();

        assertThat(metrics.active("circuit-breaker-monitor")).isEqualTo(1L);
        assertThat(metrics.active("routing-monitor")).isEqualTo(1L);

        first.dispose();

        assertThat(metrics.active("circuit-breaker-monitor")).isZero();
        assertThat(metrics.active("routing-monitor")).isEqualTo(1L);

        second.dispose();
    }

    @Test
    @DisplayName("未注册指标表时退化为不计数，且不抛异常")
    void toleratesMissingRegistry() {
        final WebSocketSessionMetrics metrics = new WebSocketSessionMetrics(null);

        assertThatCode(() -> metrics.track(Mono.empty(), "routing-monitor").block())
                .doesNotThrowAnyException();
        assertThat(metrics.active("routing-monitor")).isZero();
    }
}

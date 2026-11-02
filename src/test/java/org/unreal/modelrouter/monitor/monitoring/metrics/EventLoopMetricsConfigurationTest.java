package org.unreal.modelrouter.monitor.monitoring.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ReactorResourceFactory;
import reactor.netty.resources.LoopResources;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件循环延迟探针（#966）。
 *
 * @since 3.6.1
 */
@DisplayName("事件循环延迟指标")
class EventLoopMetricsConfigurationTest {

    @Test
    @DisplayName("线程数与 reactor 默认取同一表达式（最少 4）")
    void workerCountMatchesReactorDefault() {
        assertThat(EventLoopMetricsConfiguration.defaultWorkerCount())
                .isEqualTo(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    @Test
    @DisplayName("采一轮之后指标里出现样本，且耗时不为负")
    void samplesAreRecorded() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ReactorResourceFactory factory =
                new EventLoopMetricsConfiguration().eventLoopMetricsResourceFactory(registry);
        final LoopResources loopResources = factory.getLoopResources();
        final EventLoopMetricsConfiguration.EventLoopDelayProbe probe =
                new EventLoopMetricsConfiguration.EventLoopDelayProbe(loopResources, registry);
        try {
            probe.sample();
            Thread.sleep(300);
            probe.sample();

            final var timer = registry.find(EventLoopMetricsConfiguration.METRIC).timer();
            assertThat(timer).as("探针必须真的记到样本，否则指标是个空壳").isNotNull();
            assertThat(timer.count()).isGreaterThan(0L);
            assertThat(timer.max(java.util.concurrent.TimeUnit.NANOSECONDS)).isNotNegative();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("资源工厂不复用全局资源，且确实带上了自定义 LoopResources")
    void factoryCarriesCustomLoopResources() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ReactorResourceFactory factory =
                new EventLoopMetricsConfiguration().eventLoopMetricsResourceFactory(registry);

        assertThat(factory.isUseGlobalResources()).isFalse();
        assertThat(factory.getLoopResources()).isNotNull();
        assertThat(factory.getLoopResources().onServer(false)).isNotNull();
    }

    @Test
    @DisplayName("事件循环释放后取样不抛异常（探针不得成为故障源）")
    void sampleToleratesDisposedLoopResources() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ReactorResourceFactory factory =
                new EventLoopMetricsConfiguration().eventLoopMetricsResourceFactory(registry);
        final LoopResources loopResources = factory.getLoopResources();
        final EventLoopMetricsConfiguration.EventLoopDelayProbe probe =
                new EventLoopMetricsConfiguration.EventLoopDelayProbe(loopResources, registry);

        loopResources.dispose();

        org.assertj.core.api.Assertions.assertThatCode(probe::sample).doesNotThrowAnyException();
    }
}

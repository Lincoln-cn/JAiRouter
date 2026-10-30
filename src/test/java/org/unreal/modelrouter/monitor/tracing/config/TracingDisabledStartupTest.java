package org.unreal.modelrouter.monitor.tracing.config;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.unreal.modelrouter.monitor.tracing.TracingService;
import org.unreal.modelrouter.monitor.tracing.actuator.TracingHealthIndicator;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.config.TracingNoopAutoConfiguration;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #849 守卫：把追踪关掉必须是一个「可启动」的合法运行态。
 *
 * <p>缺陷形态：{@code Tracer} 由带 {@code @ConditionalOnProperty(enabled=true)} 的
 * {@code OpenTelemetryAutoConfiguration} 提供，而无条件装配的 {@link TracingService} 硬依赖它 ⇒
 * {@code jairouter.tracing.enabled=false} 时上下文刷新失败、进程退出
 * （{@code APPLICATION FAILED TO START … required a bean of type 'io.opentelemetry.api.trace.Tracer'}）。</p>
 */
class TracingDisabledStartupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TracingNoopAutoConfiguration.class);

    @Test
    @DisplayName("849-001 关闭追踪时上下文可启动并拿到 Tracer")
    void contextStartsWithNoopTracerWhenTracingDisabled() {
        runner.withPropertyValues("jairouter.tracing.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Tracer.class);
                });
    }

    @Test
    @DisplayName("849-002 追踪开启时不注册兜底 Bean，交由真正的自动配置")
    void noopConfigBacksOffWhenTracingEnabled() {
        runner.withPropertyValues("jairouter.tracing.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Tracer.class));
    }

    @Test
    @DisplayName("849-003 禁用时健康检查不得判 DOWN")
    void healthIsUpWhenTracingDisabled() {
        final TracingConfiguration configuration = mock(TracingConfiguration.class);
        when(configuration.isEnabled()).thenReturn(false);
        final TracingHealthIndicator indicator = new TracingHealthIndicator(
                configuration,
                mock(org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor.class),
                mock(org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor.class),
                mock(TracingMemoryManager.class));

        final Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("reason").toString()).contains("禁用");
    }
}

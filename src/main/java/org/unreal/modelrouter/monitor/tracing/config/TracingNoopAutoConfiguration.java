package org.unreal.modelrouter.monitor.tracing.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 追踪开关关闭时的兜底自动配置（#849）。
 *
 * <p>背景：真正提供 {@link Tracer} 的 {@link OpenTelemetryAutoConfiguration} 带
 * {@code @ConditionalOnProperty(jairouter.tracing.enabled=true, matchIfMissing=true)}，
 * 而 {@code TracingService}、{@code TracingController}、{@code SpanManager}、
 * 同包内的其它追踪消费方、{@code ExporterHealthChecker}、{@code HealthCheckTracingEnhancer}
 * 等消费方都是<b>无条件</b> Bean，构造参数硬依赖 {@link Tracer}。于是把追踪关掉时上下文
 * 直接刷新失败、进程退出：</p>
 *
 * <pre>
 * APPLICATION FAILED TO START
 * Parameter 0 of constructor in …monitor.tracing.TracingService
 * required a bean of type 'io.opentelemetry.api.trace.Tracer' that could not be found.
 * </pre>
 *
 * <p>本类在 {@code jairouter.tracing.enabled=false} 时补一个 <b>空实现</b> 的
 * {@link Tracer}（{@link TracerProvider#noop()}）：不建 SDK、不开导出、不占内存，
 * 所有 span 操作退化为无操作，但应用可以正常启动 —— 「关掉追踪」是一个合法运行态，
 * 不该变成启动失败。SDK 仍在时（{@code enabled=true}）本配置不生效，行为完全不变。</p>
 *
 * @author JAiRouter Team
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "jairouter.tracing.enabled", havingValue = "false")
public class TracingNoopAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(TracingNoopAutoConfiguration.class);

    /**
     * 关闭追踪时提供空的 {@link OpenTelemetry} 实例（供可能存在的可选注入点使用）。
     *
     * @return 空实现的 OpenTelemetry
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry noopOpenTelemetry() {
        LOGGER.info("追踪已关闭（jairouter.tracing.enabled=false）：使用空实现的 OpenTelemetry");
        return OpenTelemetry.noop();
    }

    /**
     * 关闭追踪时提供空的 {@link Tracer}，让无条件装配的追踪服务仍能构建。
     *
     * @return 空实现的 Tracer
     */
    @Bean
    @ConditionalOnMissingBean(Tracer.class)
    public Tracer noopTracer() {
        return TracerProvider.noop().get("jairouter-disabled");
    }
}

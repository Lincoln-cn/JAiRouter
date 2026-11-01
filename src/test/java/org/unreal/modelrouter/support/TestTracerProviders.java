package org.unreal.modelrouter.support;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;

/**
 * 测试用的 {@link SdkTracerProvider} 工厂（#940）。
 *
 * <p>升级到 OpenTelemetry 1.67 后出现了一个安静的行为变化：**没有任何 SpanProcessor 的
 * {@code SdkTracerProvider} 会退化成 noop** —— {@code provider.get("x")} 拿到的 tracer
 * 产出的 span 是 {@code PropagatedSpan}（SpanContext 无效），而不是 {@code SdkSpan}。</p>
 *
 * <p>这直接打红了 17 个 tracing 用例：它们用裸 {@code SdkTracerProvider.builder().build()}
 * 造 tracer，然后依赖「span 有效」进入 {@code DefaultTracingContext.isActive()} 分支；
 * 升级后 span 全部无效，整条链路静默短路，断言变成「zero interactions with this mock」。</p>
 *
 * <p>实测对照（1.67.0）：</p>
 * <ul>
 *   <li>{@code builder().build()} → {@code PropagatedSpan}，{@code isValid()=false}</li>
 *   <li>{@code builder().setSampler(alwaysOn()).build()} → 仍然是 {@code PropagatedSpan}</li>
 *   <li>{@code builder().addSpanProcessor(...).build()} → {@code SdkSpan}，{@code isValid()=true}</li>
 * </ul>
 *
 * <p>也就是说**决定因素是有没有 processor，不是 sampler**。生产代码不受影响：
 * {@code OpenTelemetryAutoConfiguration.tracerProvider(...)} 总是挂一个
 * {@code BatchSpanProcessor}。</p>
 *
 * @since 3.5.10
 */
public final class TestTracerProviders {

    private TestTracerProviders() {
    }

    /**
     * 造一个能产出有效 span 的 provider（挂一个丢弃型 exporter，不产生输出噪声）。
     *
     * @return 可直接 {@code get(tracerName)} 的 provider
     */
    public static SdkTracerProvider sdk() {
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(DiscardingSpanExporter.INSTANCE))
                .build();
    }

    /** 丢弃所有 span 的 exporter：只需要「存在一个 processor」，不需要导出。 */
    private static final class DiscardingSpanExporter implements SpanExporter {

        private static final DiscardingSpanExporter INSTANCE = new DiscardingSpanExporter();

        @Override
        public CompletableResultCode export(final Collection<SpanData> spans) {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}

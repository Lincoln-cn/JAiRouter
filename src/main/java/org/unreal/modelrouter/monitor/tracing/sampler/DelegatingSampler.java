package org.unreal.modelrouter.monitor.tracing.sampler;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

import java.util.List;
import java.util.function.Supplier;

/**
 * 每次都转发给「当前」采样器的委托采样器（issue #234）。
 *
 * <p>为什么需要它：{@code SdkTracerProvider} 在启动时只取一次 {@code Sampler} 实例，而
 * {@code SamplingStrategyManager} 允许运行时换策略（{@code PUT /api/tracing/actuator/config} 会走到
 * {@code updateSamplingConfiguration}）。如果直接把当时的采样器交给 provider，运行时换策略就只改了内部字段、
 * 对实际采样毫无影响 —— 之前就是这样。这里让 provider 拿一个委托壳，每次判定都向 manager 取当前策略，
 * 于是运行时切换真正生效。</p>
 *
 * <p>注意语义：本类**只作为 root sampler** 使用（由 {@code Sampler.parentBased(delegate)} 包裹）。
 * 有父 span 时仍由 {@code ParentBasedSampler} 直接沿用父级决策，不会调用到这里。</p>
 */
public final class DelegatingSampler implements Sampler {

    private final Supplier<Sampler> delegateSupplier;

    public DelegatingSampler(final Supplier<Sampler> delegateSupplier) {
        this.delegateSupplier = delegateSupplier;
    }

    @Override
    public SamplingResult shouldSample(
            final Context parentContext,
            final String traceId,
            final String name,
            final SpanKind spanKind,
            final Attributes attributes,
            final List<LinkData> parentLinks) {
        return current().shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
        return current().getDescription();
    }

    /** 兜底：manager 尚未初始化完（@PostConstruct 之前）也要能判定，返回「全采」而不是抛异常 */
    private Sampler current() {
        final Sampler delegate = delegateSupplier.get();
        return delegate == null ? Sampler.alwaysOn() : delegate;
    }
}

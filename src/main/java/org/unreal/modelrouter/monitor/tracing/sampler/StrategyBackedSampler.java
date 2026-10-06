package org.unreal.modelrouter.monitor.tracing.sampler;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把项目自己的 {@link SamplingStrategy} 适配成 OpenTelemetry 的 {@link Sampler}（issue #234）。
 *
 * <p>为什么需要这一层：{@code RuleBasedSamplingStrategy} / {@code AdaptiveSamplingStrategy} 实现的是本仓库的
 * {@link SamplingStrategy} 接口（入参是 {@code Map<String, Object>} 属性），而 OTel SDK 只认 {@link Sampler}
 * （入参是 {@code Attributes}）。此前 {@code SamplingStrategyManager} 干脆没用这两个实现 —— 这就是
 * "规则采样/自适应采样配了不生效"的根因。这里做一次入参/出参的翻译，把两套接口接上。</p>
 *
 * <p>注意两个 {@code SamplingResult} 同名：本包里的（策略层）与
 * {@code io.opentelemetry.sdk.trace.samplers.SamplingResult}（SDK 层）。这里用全限定名指 SDK 那个。</p>
 */
public final class StrategyBackedSampler implements Sampler {

    private final SamplingStrategy strategy;

    public StrategyBackedSampler(final SamplingStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public io.opentelemetry.sdk.trace.samplers.SamplingResult shouldSample(
            final Context parentContext,
            final String traceId,
            final String name,
            final SpanKind spanKind,
            final Attributes attributes,
            final List<LinkData> parentLinks) {

        final SamplingResult decision = strategy.shouldSample(
                parentContext, traceId, name, spanKind, toMap(attributes), Map.of());

        return switch (decision.getDecision()) {
            case RECORD_AND_SAMPLE -> io.opentelemetry.sdk.trace.samplers.SamplingResult.recordAndSample();
            case RECORD_ONLY -> io.opentelemetry.sdk.trace.samplers.SamplingResult.recordOnly();
            case DROP -> io.opentelemetry.sdk.trace.samplers.SamplingResult.drop();
        };
    }

    @Override
    public String getDescription() {
        return strategy.getDescription();
    }

    /** {@code Attributes} → {@code Map<String, Object>}：规则匹配只用到标量键值 */
    private static Map<String, Object> toMap(final Attributes attributes) {
        final Map<String, Object> map = new HashMap<>();
        attributes.forEach((key, value) -> map.put(key.getKey(), value));
        return map;
    }
}

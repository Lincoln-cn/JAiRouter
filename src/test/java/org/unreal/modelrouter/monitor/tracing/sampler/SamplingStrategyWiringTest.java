package org.unreal.modelrouter.monitor.tracing.sampler;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采样策略接线（issue #234）。
 *
 * <p>背景：{@code SamplingStrategyManager} 此前把 {@code ratio_based} 与 {@code rule_based} 注册成
 * 同一个 {@code traceIdRatioBased(ratio)}，**从不读 {@code rules}**；{@code AdaptiveSamplingStrategy} 与
 * {@code RuleBasedSamplingStrategy} 两个类全仓只有测试实例化过。于是"规则采样/自适应采样配了不生效"。</p>
 *
 * <p>这些用例断言的是**行为**而非内部字段：给定确定性配置（采样率取 0.0 / 1.0），采样器对同一 traceId
 * 的判定必须是确定的，因此可以直接断言 {@code isSampled()}。</p>
 */
@DisplayName("采样策略接线（issue #234）")
class SamplingStrategyWiringTest {

    private static final String TRACE_ID = "00000000000000000000000000000001";
    private static final String RULE_CONDITION = "http.status_code >= 500";

    private static TracingConfiguration.SamplingConfig config(final String strategy, final double ratio) {
        final TracingConfiguration configuration = new TracingConfiguration();
        final TracingConfiguration.SamplingConfig sampling = configuration.getSampling();
        sampling.setStrategy(strategy);
        sampling.setRatio(ratio);
        sampling.setRules(List.of());
        sampling.setAlwaysSample(List.of());
        sampling.setNeverSample(List.of());
        sampling.setServiceRatios(Map.of());
        sampling.getAdaptive().setEnabled(false);
        return sampling;
    }

    private static SamplingStrategyManager managerFor(final TracingConfiguration.SamplingConfig sampling) {
        final TracingConfiguration configuration = new TracingConfiguration();
        configuration.setSampling(sampling);
        final SamplingStrategyManager manager = new SamplingStrategyManager(configuration);
        manager.init();
        return manager;
    }

    private static boolean sampled(final Sampler sampler, final String spanName, final Attributes attributes) {
        return sampler.shouldSample(Context.root(), TRACE_ID, spanName, SpanKind.SERVER, attributes, List.of())
                .getDecision() == SamplingDecision.RECORD_AND_SAMPLE;
    }

    @Test
    @DisplayName("strategy=ratio：只按全局采样率，rules 不参与")
    void ratioStrategyIgnoresRules() {
        final TracingConfiguration.SamplingConfig sampling = config("ratio", 0.0);
        sampling.setRules(List.of(rule(RULE_CONDITION, 1.0)));

        final Sampler sampler = managerFor(sampling).getCurrentStrategy();

        assertFalse(sampled(sampler, "GET /api", Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("http.status_code"), "500")),
                "ratio=0.0 时即使命中 rules 也不该采样 —— rules 只在 strategy=rule 下生效");
    }

    @Test
    @DisplayName("strategy=rule：命中 rules 的 span 按规则采样率放行，未命中的回落全局采样率")
    void ruleStrategyAppliesRules() {
        final TracingConfiguration.SamplingConfig sampling = config("rule", 0.0);
        sampling.setRules(List.of(rule(RULE_CONDITION, 1.0)));

        final Sampler sampler = managerFor(sampling).getCurrentStrategy();

        assertTrue(sampled(sampler, "GET /api", Attributes.of(
                        io.opentelemetry.api.common.AttributeKey.stringKey("http.status_code"), "500")),
                "命中 condition（>=500）的 span 应按规则采样率 1.0 放行");
        assertFalse(sampled(sampler, "GET /api", Attributes.of(
                        io.opentelemetry.api.common.AttributeKey.stringKey("http.status_code"), "200")),
                "未命中规则的 span 应回落全局 ratio=0.0");
    }

    @Test
    @DisplayName("strategy=rule：always-sample / never-sample 列表也一并生效（此前同样被忽略）")
    void ruleStrategyAppliesSampleLists() {
        final TracingConfiguration.SamplingConfig sampling = config("rule", 1.0);
        sampling.setAlwaysSample(List.of("POST /api/v1/chat/completions"));
        sampling.setNeverSample(List.of("/health"));

        final Sampler sampler = managerFor(sampling).getCurrentStrategy();

        assertTrue(sampled(sampler, "POST /api/v1/chat/completions", Attributes.empty()));
        assertFalse(sampled(sampler, "/health", Attributes.empty()), "never-sample 列表应直接丢弃");
    }

    @Test
    @DisplayName("strategy=adaptive：adaptive.enabled=true 时选中自适应策略，未开启时回落 ratio")
    void adaptiveStrategyRequiresEnabled() {
        final TracingConfiguration.SamplingConfig enabled = config("adaptive", 0.5);
        enabled.getAdaptive().setEnabled(true);
        assertTrue(managerFor(enabled).getCurrentStrategy().getDescription().contains("Adaptive"),
                "adaptive.enabled=true 且 strategy=adaptive 时应装配自适应策略，实际："
                        + managerFor(enabled).getCurrentStrategy().getDescription());

        final TracingConfiguration.SamplingConfig disabled = config("adaptive", 0.5);
        final Sampler fallback = managerFor(disabled).getCurrentStrategy();
        assertTrue(fallback.getDescription().contains("Ratio"),
                "adaptive 未开启时应回落比例采样，实际：" + fallback.getDescription());
    }

    @Test
    @DisplayName("策略名不认识时回落 ratio，不抛异常")
    void unknownStrategyFallsBackToRatio() {
        final Sampler sampler = managerFor(config("no-such-strategy", 0.5)).getCurrentStrategy();

        assertTrue(sampler.getDescription().contains("Ratio"), "实际：" + sampler.getDescription());
    }

    @Test
    @DisplayName("DelegatingSampler：运行时换策略立刻生效（provider 只在启动时取一次采样器）")
    void runtimeSwitchTakesEffectImmediately() {
        final TracingConfiguration.SamplingConfig sampling = config("ratio", 0.0);
        sampling.setRules(List.of(rule(RULE_CONDITION, 1.0)));
        final SamplingStrategyManager manager = managerFor(sampling);

        // provider 侧的实际形态：parentBased 包一个委托壳（见 OpenTelemetryAutoConfiguration#otelSampler）
        final Sampler providerSide = Sampler.parentBased(new DelegatingSampler(manager::getCurrentStrategy));
        final Attributes errorSpan = Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("http.status_code"), "500");

        assertFalse(sampled(providerSide, "GET /api", errorSpan), "切换前按 ratio=0.0，全部丢弃");

        manager.updateStrategy("rule");

        assertTrue(sampled(providerSide, "GET /api", errorSpan),
                "切换后同一采样器实例应立即按新策略判定（此前 provider 拿的是启动时那一份，换了不生效）");
    }

    @Test
    @DisplayName("校验器：策略名非法报错，合法但缺子配置只告警")
    void validatorChecksStrategy() {
        final SamplingConfigurationValidator validator = new SamplingConfigurationValidator();

        final SamplingConfigurationValidator.ValidationResult bad =
                validator.validateSamplingConfig(config("ratio_based_traceid_ratio", 0.5));
        assertFalse(bad.isValid(), "不认识的策略名应判为非法：" + bad.getErrors());

        final SamplingConfigurationValidator.ValidationResult ruleWithoutRules =
                validator.validateSamplingConfig(config("rule", 0.5));
        assertTrue(ruleWithoutRules.isValid(), "strategy=rule 但没有 rules 应当只是告警，不算非法");
        assertFalse(ruleWithoutRules.getWarnings().isEmpty(), "应当给出「会回落全局采样率」的告警");
    }

    private static TracingConfiguration.SamplingConfig.SamplingRule rule(final String condition, final double ratio) {
        final TracingConfiguration.SamplingConfig.SamplingRule rule =
                new TracingConfiguration.SamplingConfig.SamplingRule();
        rule.setCondition(condition);
        rule.setRatio(ratio);
        return rule;
    }
}

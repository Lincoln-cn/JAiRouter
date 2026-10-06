package org.unreal.modelrouter.monitor.tracing.sampler;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 采样策略管理器
 *
 * <p>负责按配置装配采样器、并在运行时更新（issue #234 接线）。策略由
 * {@code jairouter.tracing.sampling.strategy} 选择：{@code ratio}（默认）/ {@code rule} / {@code adaptive}。</p>
 *
 * <p><b>这里的采样器都是「root sampler」</b>：{@code OpenTelemetryAutoConfiguration#otelSampler()} 会用
 * {@code Sampler.parentBased(new DelegatingSampler(...))} 包一层 —— 有父 span 时沿用父级决策，
 * 没有父 span 时才落到本管理器当前选中的策略。{@code DelegatingSampler} 让运行时切换真正生效
 * （此前 {@code SdkTracerProvider} 启动时取一次采样器就固定了，{@code updateStrategy} 只改内部字段）。</p>
 *
 * <p>装配不出来时（策略名不认识、或 {@code adaptive} 但 {@code adaptive.enabled=false}）一律回落
 * {@code ratio} 并告警 —— 采样配置写错不该让服务起不来，静默全采/全不采同样不可接受。</p>
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SamplingStrategyManager {

    /** 合法的策略名（与 {@code SamplingStrategyValidator} 的校验口径一致） */
    public static final Set<String> SUPPORTED_STRATEGIES = Set.of("ratio", "rule", "adaptive");

    private final TracingConfiguration tracingConfig;
    private final Map<String, Sampler> strategies = new LinkedHashMap<>();
    private volatile Sampler currentStrategy;

    public SamplingStrategyManager(final TracingConfiguration tracingConfig) {
        this.tracingConfig = tracingConfig;
    }

    /**
     * 初始化采样策略管理器
     */
    @PostConstruct
    public void init() {
        log.info("初始化采样策略管理器");
        refreshStrategies();
    }

    /**
     * 按当前配置重建策略并选中 {@code sampling.strategy} 指定的那个。
     *
     * <p>{@code strategies} 里存的都是未包 {@code parentBased} 的 root sampler（见类注释）。</p>
     */
    public void refreshStrategies() {
        final TracingConfiguration.SamplingConfig samplingConfig = tracingConfig.getSampling();

        strategies.clear();

        // ratio：只按全局采样率做 traceId 比例采样
        strategies.put("ratio", Sampler.traceIdRatioBased(samplingConfig.getRatio()));

        // rule：按 rules 逐条匹配 span 属性，未命中回落全局采样率
        strategies.put("rule", new StrategyBackedSampler(new RuleBasedSamplingStrategy(samplingConfig)));

        // adaptive：用上下限约束采样率；未开启就没必要装配，否则选中它时会静默变成另一种语义
        final TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig = samplingConfig.getAdaptive();
        if (adaptiveConfig != null && adaptiveConfig.isEnabled()) {
            strategies.put("adaptive", new StrategyBackedSampler(new AdaptiveSamplingStrategy(
                    samplingConfig.getRatio(),
                    adaptiveConfig.getMaxRatio(),
                    adaptiveConfig.getMinRatio(),
                    adaptiveConfig.getTargetSpansPerSecond(),
                    adaptiveConfig.getAdjustmentInterval())));
        }

        final String requested = samplingConfig.getStrategy();
        final Sampler selected = strategies.get(requested);
        if (selected == null) {
            log.warn("采样策略 {} 不可用（合法值 {}；adaptive 还需 adaptive.enabled=true），回落 ratio。当前可用：{}",
                    requested, SUPPORTED_STRATEGIES, strategies.keySet());
            currentStrategy = strategies.get("ratio");
        } else {
            currentStrategy = selected;
        }
        log.info("采样策略刷新完成：请求={} 实际生效={}（已装配 {}）",
                requested, currentStrategy.getDescription(), strategies.keySet());
    }

    /**
     * 获取当前采样策略（root sampler；provider 会再包一层 parentBased）
     *
     * @return 当前采样策略
     */
    public Sampler getCurrentStrategy() {
        return currentStrategy;
    }

    /**
     * 运行时切换采样策略
     *
     * @param strategyName 策略名称（{@code ratio} / {@code rule} / {@code adaptive}）
     */
    public void updateStrategy(final String strategyName) {
        final Sampler strategy = strategies.get(strategyName);
        if (strategy != null) {
            this.currentStrategy = strategy;
            log.info("更新采样策略为: {}", strategyName);
        } else {
            log.warn("未找到采样策略: {}（可用：{}），保持当前策略 {}",
                    strategyName, strategies.keySet(), currentStrategy.getDescription());
        }
    }

    /**
     * 更新采样配置：写入生效配置对象后按新的 {@code strategy} / 参数重建策略
     *
     * @param newConfig 新的采样配置
     */
    public void updateSamplingConfiguration(final TracingConfiguration.SamplingConfig newConfig) {
        if (newConfig == null) {
            log.warn("采样配置为 null，忽略本次更新");
            return;
        }
        tracingConfig.setSampling(newConfig);
        log.info("更新采样配置：strategy={} ratio={}", newConfig.getStrategy(), newConfig.getRatio());
        refreshStrategies();
    }

    /** 供诊断/测试查看当前装配了哪些策略 */
    public Map<String, Sampler> availableStrategies() {
        return Map.copyOf(strategies);
    }
}

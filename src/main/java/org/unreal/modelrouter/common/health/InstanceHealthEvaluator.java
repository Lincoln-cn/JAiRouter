package org.unreal.modelrouter.common.health;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一组探针结论合成为一个 {@link InstanceHealth}。
 *
 * <p>这是一个**纯函数**：不查库、不建连接、不看时间。这样它可以被穷举测试，
 * 判定逻辑也能被解释清楚 —— 现在（v3.7.3）的判定散在 {@code ServerChecker} 里，
 * 与 IO 混在一起，想测一个分支得先起连接。</p>
 *
 * <p>合并规则（顺序即优先级）：</p>
 * <ol>
 *   <li>任一 DOWN 且严重度为 FATAL ⇒ {@link InstanceHealth#UNHEALTHY}</li>
 *   <li>任一 DOWN 且严重度为 SOFT ⇒ {@link InstanceHealth#DEGRADED}</li>
 *   <li>没有 UP、且至少有一个 UNKNOWN ⇒ {@link InstanceHealth#UNKNOWN}</li>
 *   <li>否则 ⇒ {@link InstanceHealth#HEALTHY}</li>
 * </ol>
 *
 * <p>「适配器解析不出」属于 SOFT：实例本身没坏，是配置疏漏，判成 UNHEALTHY
 * 会把一个提示性问题放大成整条链路不可用（见 {@code #1004}）。</p>
 *
 * @since v3.8.0
 */
public final class InstanceHealthEvaluator {

    private InstanceHealthEvaluator() {
    }

    /**
     * 合成健康状态。
     *
     * @param signals 各探针结论，顺序不重要；允许为空
     * @return 合成后的健康状态，永不为 {@code null}
     */
    public static InstanceHealth evaluate(final List<HealthSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return InstanceHealth.UNKNOWN;
        }

        boolean hasFatal = false;
        boolean hasSoft = false;
        boolean hasUp = false;
        boolean hasUnknown = false;

        for (final HealthSignal signal : signals) {
            if (signal == null) {
                continue;
            }
            if (signal.isDown()) {
                if (signal.severity() == HealthSignal.Severity.FATAL) {
                    hasFatal = true;
                } else {
                    hasSoft = true;
                }
            } else if (signal.status() == HealthSignal.Status.UP) {
                hasUp = true;
            } else {
                hasUnknown = true;
            }
        }

        if (hasFatal) {
            return InstanceHealth.UNHEALTHY;
        }
        if (hasSoft) {
            return InstanceHealth.DEGRADED;
        }
        if (!hasUp && hasUnknown) {
            return InstanceHealth.UNKNOWN;
        }
        return InstanceHealth.HEALTHY;
    }

    /**
     * 把探针名字与结论整理成「名字 → 原因」的说明。
     *
     * <p>给 API 与日志用：用户看到 {@code DEGRADED} 时，必须能知道**是哪一项**降的级。
     * 只返回非 UP 的项，避免把一串「一切正常」塞进响应。</p>
     *
     * @param results 探针名 → 结论
     * @return 非 UP 项的说明，顺序与入参一致；全 UP 时为空 Map
     */
    public static Map<String, String> describe(final Map<String, HealthSignal> results) {
        final Map<String, String> reasons = new LinkedHashMap<>();
        if (results == null) {
            return reasons;
        }
        results.forEach((name, signal) -> {
            if (signal != null && signal.status() != HealthSignal.Status.UP) {
                reasons.put(name, signal.reason() == null ? signal.status().name() : signal.reason());
            }
        });
        return reasons;
    }

    /**
     * 便捷方法：按顺序跑一组探针并合成。
     *
     * @param probes 探针列表，允许为空
     * @param serviceType 服务类型
     * @param instance 实例
     * @return 合成结果与逐项说明
     */
    public static Result run(final List<HealthProbe> probes,
                             final String serviceType,
                             final org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance instance) {
        final Map<String, HealthSignal> byName = new LinkedHashMap<>();
        final List<HealthSignal> signals = new ArrayList<>();
        if (probes != null) {
            for (final HealthProbe probe : probes) {
                final HealthSignal signal;
                try {
                    signal = probe.probe(serviceType, instance);
                } catch (RuntimeException e) {
                    // 探针自己的 bug 不该让整个检查停摆，也不该误判实例不可用；
                    // 但要留一条说明，否则「为什么这项没结论」会被吞掉。
                    final HealthSignal broken =
                            HealthSignal.unknown(probe.name() + " 探针异常: " + e.getMessage());
                    byName.put(probe.name(), broken);
                    signals.add(broken);
                    continue;
                }
                if (signal == null) {
                    continue;
                }
                byName.put(probe.name(), signal);
                signals.add(signal);
            }
        }
        return new Result(evaluate(signals), describe(byName));
    }

    /**
     * 一次检查的完整结果。
     *
     * @param health  合成后的健康状态
     * @param reasons 非 UP 项的说明（探针名 → 原因）
     */
    public record Result(InstanceHealth health, Map<String, String> reasons) {
    }
}

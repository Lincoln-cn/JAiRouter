package org.unreal.modelrouter.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

/**
 * 健康合成的判定表（#1004 的根治方案，第一步）。
 *
 * <p>判定是纯函数，所以这里可以穷举边界：全 UP、只有 SOFT DOWN、有 FATAL DOWN、
 * 全 UNKNOWN、探针抛异常。每一条都对应一种线上真实情形。</p>
 */
class InstanceHealthEvaluatorTest {

    @Test
    void allUpIsHealthy() {
        final InstanceHealth health = InstanceHealthEvaluator.evaluate(List.of(
                HealthSignal.up(), HealthSignal.up()));

        assertThat(health).isEqualTo(InstanceHealth.HEALTHY);
    }

    @Test
    void softDownDegradesButDoesNotKill() {
        final InstanceHealth health = InstanceHealthEvaluator.evaluate(List.of(
                HealthSignal.up(),
                HealthSignal.down(HealthSignal.Severity.SOFT, "适配器未注册")));

        assertThat(health)
                .as("适配器这类配置问题不能把实例判死，否则一个疏漏会放大成全量不可用")
                .isEqualTo(InstanceHealth.DEGRADED);
    }

    @Test
    void fatalDownWinsOverSoft() {
        final InstanceHealth health = InstanceHealthEvaluator.evaluate(List.of(
                HealthSignal.down(HealthSignal.Severity.SOFT, "适配器未注册"),
                HealthSignal.down(HealthSignal.Severity.FATAL, "端口不可达")));

        assertThat(health).isEqualTo(InstanceHealth.UNHEALTHY);
    }

    @Test
    void noUpAndSomeUnknownIsUnknown() {
        final InstanceHealth health = InstanceHealthEvaluator.evaluate(List.of(
                HealthSignal.unknown("还没检查")));

        assertThat(health).isEqualTo(InstanceHealth.UNKNOWN);
    }

    @Test
    void unknownMixedWithUpStaysHealthy() {
        final InstanceHealth health = InstanceHealthEvaluator.evaluate(List.of(
                HealthSignal.up(), HealthSignal.unknown("熔断状态未知")));

        assertThat(health)
                .as("已有明确 UP 的项时，未知项不该把整体拉成 UNKNOWN")
                .isEqualTo(InstanceHealth.HEALTHY);
    }

    @Test
    void emptySignalsIsUnknown() {
        assertThat(InstanceHealthEvaluator.evaluate(List.of())).isEqualTo(InstanceHealth.UNKNOWN);
        assertThat(InstanceHealthEvaluator.evaluate(null)).isEqualTo(InstanceHealth.UNKNOWN);
    }

    @Test
    void describeKeepsOnlyNonUpReasons() {
        final Map<String, HealthSignal> results = new java.util.LinkedHashMap<>();
        results.put("tcp", HealthSignal.up());
        results.put("adapter", HealthSignal.down(HealthSignal.Severity.SOFT, "适配器 'default' 未注册"));

        final Map<String, String> reasons = InstanceHealthEvaluator.describe(results);

        assertThat(reasons).containsOnlyKeys("adapter");
        assertThat(reasons.get("adapter")).contains("未注册");
    }

    @Test
    void runToleratesProbeException() {
        final HealthProbe broken = new HealthProbe() {
            @Override
            public String name() {
                return "initially-broken";
            }

            @Override
            public HealthSignal probe(final String serviceType,
                                      final ModelRouterProperties.ModelInstance instance) {
                throw new IllegalStateException("boom");
            }
        };
        final HealthProbe healthy = new HealthProbe() {
            @Override
            public String name() {
                return "tcp";
            }

            @Override
            public HealthSignal probe(final String serviceType,
                                      final ModelRouterProperties.ModelInstance instance) {
                return HealthSignal.up();
            }
        };

        final InstanceHealthEvaluator.Result result = InstanceHealthEvaluator.run(
                List.of(broken, healthy), "chat", new ModelRouterProperties.ModelInstance());

        assertThat(result.health())
                .as("一个探针抛异常不能让整体判死")
                .isEqualTo(InstanceHealth.HEALTHY);
        assertThat(result.reasons()).containsKey("initially-broken");
    }
}

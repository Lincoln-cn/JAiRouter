package org.unreal.modelrouter.common.cluster;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多副本共享态健康指标（#162）。
 *
 * <p>把 {@link MultiReplicaSharedStateChecker} 的自检结果挂到 {@code /actuator/health}
 * 的 {@code sharedState} 组件详情（与 {@code RbacEndpointCoverageHealthIndicator}、
 * {@code TracingHealthIndicator} 同一约定）。</p>
 *
 * <p><b>默认恒为 UP</b>（状态放在 details 里），与 {@code RbacEndpointCoverageHealthIndicator}
 * 的理由一致：共享态开关未开是「配置缺口」而非「可用性故障」。把它直接判为 DOWN 会让所有
 * 以 {@code /actuator/health} 为探针的容器（仓库全部 Dockerfile 与 compose 都是）变成
 * unhealthy，K8s 上还可能触发重启循环——用故障换告警不划算，重启也修不好配置。</p>
 *
 * <p>需要让编排层感知时，显式设
 * {@code jairouter.cluster.shared-state-check.fail-health=true}（降级为 DOWN），
 * 或 {@code mode=FAIL}（启动即失败）。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@Component("sharedState")
@ConditionalOnProperty(
        name = "jairouter.cluster.shared-state-check.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SharedStateHealthIndicator implements HealthIndicator {

    private final MultiReplicaSharedStateChecker checker;

    public SharedStateHealthIndicator(final MultiReplicaSharedStateChecker checker) {
        this.checker = checker;
    }

    @Override
    public Health health() {
        MultiReplicaSharedStateGate.Assessment assessment = checker.getAssessment();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("multiReplica", assessment.multiReplica());
        details.put("disabledSwitches", assessment.disabledSwitches());

        if (!assessment.needsAttention()) {
            return Health.up().withDetails(details).build();
        }

        details.put("advice",
                "多副本下这些开关未开启会让各副本互不知情；开启后重启生效，"
                        + "或显式设 jairouter.cluster.shared-state-check.mode=OFF 接受该风险");
        Status status = checker.isFailHealth() ? Status.DOWN : Status.UP;
        return Health.status(status).withDetails(details).build();
    }
}

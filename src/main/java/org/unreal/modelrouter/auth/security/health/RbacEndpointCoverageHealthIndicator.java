package org.unreal.modelrouter.auth.security.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.permission.RbacEndpointCoverageChecker;
import org.unreal.modelrouter.auth.security.permission.RbacEndpointCoverageReport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RBAC 端点覆盖自检健康指标（#128 Phase 1/2）。
 *
 * <p>把 {@link RbacEndpointCoverageReport} 挂到 Spring Boot Actuator
 * {@code /actuator/health} 组件详情（与 TracingHealthIndicator / JwtRedisHealthMonitor 同一约定）。
 *
 * <p>状态恒为 UP：覆盖缺口是「信息可见性」问题而非可用性故障，
 * 不应触发探针重启。Phase 2 起 EXEMPT（显式豁免）与 MISSING（可行动缺口）
 * 分开暴露，MISSING 是需要处理的信号。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@Component("rbacEndpointCoverage")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "jairouter.security.rbac.coverage-check.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RbacEndpointCoverageHealthIndicator implements HealthIndicator {

    private final RbacEndpointCoverageChecker checker;

    @Override
    public Health health() {
        RbacEndpointCoverageReport report = checker.getReport();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("checkedCount", report.checkedCount());
        details.put("coveredCount", report.coveredCount());
        details.put("exemptCount", report.exemptCount());
        details.put("missingCount", report.missingCount());
        details.put("excludedCount", report.excludedCount());
        details.put("exemptEndpoints", report.exemptEndpoints());
        details.put("missingEndpoints", report.missingEndpoints());
        details.put("fallback",
                "unmatched GET falls back to authenticated() (fail-open, phase-3 scope); "
                        + "unmatched writes are denied unless exempt (phase-2 default, "
                        + "escape hatch jairouter.security.rbac.write-fail-closed.enabled=false)");
        return Health.up().withDetails(details).build();
    }
}

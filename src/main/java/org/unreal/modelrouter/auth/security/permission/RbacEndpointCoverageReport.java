package org.unreal.modelrouter.auth.security.permission;

import java.util.List;

/**
 * RBAC 端点覆盖自检结果（只读快照）。
 *
 * <p>统计 {@link PermissionRuleRegistry} 对已映射请求端点的覆盖情况：
 * covered = 能命中权限规则；uncovered = 无规则、将回退 login-only（authenticated）；
 * excluded = 不经过 {@link PermissionAuthorizationManager} 的路径（见
 * {@link RbacEndpointCoverageChecker} 排除清单）。
 *
 * @param checkedCount     参与规则匹配的端点数（不含 excluded）
 * @param coveredCount     至少命中一条权限规则的端点数
 * @param uncoveredCount   未命中任何规则的端点数（当前行为：任意登录用户可访问）
 * @param excludedCount    按排除清单跳过的端点数
 * @param uncoveredEndpoints 未覆盖端点清单，形如 {@code GET /api/foo/{id}}，已排序去重
 * @author JAiRouter Team
 * @since 3.0.3
 */
public record RbacEndpointCoverageReport(
        int checkedCount,
        int coveredCount,
        int uncoveredCount,
        int excludedCount,
        List<String> uncoveredEndpoints) {

    public RbacEndpointCoverageReport {
        uncoveredEndpoints = List.copyOf(uncoveredEndpoints);
    }

    public static RbacEndpointCoverageReport empty() {
        return new RbacEndpointCoverageReport(0, 0, 0, 0, List.of());
    }
}

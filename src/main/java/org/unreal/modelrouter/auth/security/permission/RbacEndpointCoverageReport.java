package org.unreal.modelrouter.auth.security.permission;

import java.util.List;

/**
 * RBAC 端点覆盖自检结果（只读快照，#128 Phase 2）。
 *
 * <p>统计 {@link PermissionRuleRegistry} 与 {@link RbacExemptEndpoints} 对已映射
 * 请求端点的覆盖情况：
 * <ul>
 *   <li>covered = 能命中权限规则；</li>
 *   <li>exempt = 未命中规则但命中显式豁免清单（有意不登记，见 {@link RbacExemptEndpoints}）；</li>
 *   <li>missing = 既无规则也未豁免——<b>可行动信号</b>，应补登记规则或加入豁免清单；</li>
 *   <li>excluded = 不经过 {@link PermissionAuthorizationManager} 的路径（见
 *       {@link RbacEndpointCoverageChecker} 排除清单）。</li>
 * </ul>
 *
 * @param checkedCount     参与判定的端点数（covered + exempt + missing，不含 excluded）
 * @param coveredCount     至少命中一条权限规则的端点数
 * @param exemptCount      命中显式豁免清单的端点数
 * @param missingCount     既无规则也未豁免的端点数（可行动缺口）
 * @param excludedCount    按排除清单跳过的端点数
 * @param exemptEndpoints  豁免端点清单，形如 {@code POST /api/v1/chat/completions}，已排序去重
 * @param missingEndpoints 缺口端点清单，形如 {@code GET /api/foo/{id}}，已排序去重（可行动信号）
 * @author JAiRouter Team
 * @since 3.0.4
 */
public record RbacEndpointCoverageReport(
        int checkedCount,
        int coveredCount,
        int exemptCount,
        int missingCount,
        int excludedCount,
        List<String> exemptEndpoints,
        List<String> missingEndpoints) {

    public RbacEndpointCoverageReport {
        exemptEndpoints = List.copyOf(exemptEndpoints);
        missingEndpoints = List.copyOf(missingEndpoints);
    }

    public static RbacEndpointCoverageReport empty() {
        return new RbacEndpointCoverageReport(0, 0, 0, 0, 0, List.of(), List.of());
    }
}

package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #128 Phase 3：未命中姿态配置解析与两键优先级。
 *
 * <p>约定（两键永远不会矛盾）：
 * <ol>
 *   <li>{@code jairouter.security.rbac.unmatched-policy} 显式配置 → 优先，布尔键被忽略</li>
 *   <li>否则遗留 {@code jairouter.security.rbac.write-fail-closed.enabled}：
 *       {@code false}→AUTHENTICATED，{@code true}→DENY_WRITES</li>
 *   <li>都未配置 → DENY_ALL（Phase 3 默认）</li>
 * </ol>
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@DisplayName("RbacUnmatchedPolicy 配置解析与两键优先级（#128 Phase 3）")
class RbacUnmatchedPolicyResolutionTest {

    @Test
    @DisplayName("两键都未配置 → DENY_ALL（Phase 3 默认）")
    void defaultIsDenyAll() {
        assertEquals(RbacUnmatchedPolicy.DENY_ALL,
                PermissionAuthorizationManager.resolvePolicy("", ""),
                "默认姿态必须是 DENY_ALL");
        assertEquals(RbacUnmatchedPolicy.DENY_ALL,
                PermissionAuthorizationManager.resolvePolicy(null, null));
    }

    @Test
    @DisplayName("unmatched-policy 显式配置优先，忽略 write-fail-closed（不会矛盾）")
    void unmatchedPolicyWinsOverLegacyBoolean() {
        assertEquals(RbacUnmatchedPolicy.AUTHENTICATED,
                PermissionAuthorizationManager.resolvePolicy("AUTHENTICATED", "true"),
                "unmatched-policy=AUTHENTICATED 必须压过 write-fail-closed=true");
        assertEquals(RbacUnmatchedPolicy.DENY_ALL,
                PermissionAuthorizationManager.resolvePolicy("DENY_ALL", "false"),
                "unmatched-policy=DENY_ALL 必须压过 write-fail-closed=false");
        assertEquals(RbacUnmatchedPolicy.DENY_WRITES,
                PermissionAuthorizationManager.resolvePolicy("deny_writes", "false"),
                "大小写不敏感，且压过布尔键");
    }

    @Test
    @DisplayName("仅遗留布尔键：false→AUTHENTICATED，true→DENY_WRITES")
    void legacyBooleanMapping() {
        assertEquals(RbacUnmatchedPolicy.AUTHENTICATED,
                PermissionAuthorizationManager.resolvePolicy("", "false"),
                "write-fail-closed=false 必须恢复遗留 fail-open");
        assertEquals(RbacUnmatchedPolicy.AUTHENTICATED,
                PermissionAuthorizationManager.resolvePolicy(null, "false"));
        assertEquals(RbacUnmatchedPolicy.DENY_WRITES,
                PermissionAuthorizationManager.resolvePolicy("", "true"),
                "write-fail-closed=true 对应 Phase 2 姿态");
    }

    @Test
    @DisplayName("姿态语义：AUTHENTICATED 全放行，DENY_WRITES 仅写拒绝，DENY_ALL 全拒绝")
    void postureSemantics() {
        assertEquals(false, RbacUnmatchedPolicy.AUTHENTICATED.shouldDenyUnmatched(null));
        assertEquals(false, RbacUnmatchedPolicy.AUTHENTICATED.shouldDenyUnmatched(
                org.springframework.http.HttpMethod.GET));
        assertEquals(false, RbacUnmatchedPolicy.AUTHENTICATED.shouldDenyUnmatched(
                org.springframework.http.HttpMethod.POST));

        assertEquals(false, RbacUnmatchedPolicy.DENY_WRITES.shouldDenyUnmatched(
                org.springframework.http.HttpMethod.GET));
        assertEquals(true, RbacUnmatchedPolicy.DENY_WRITES.shouldDenyUnmatched(
                org.springframework.http.HttpMethod.POST));

        assertEquals(true, RbacUnmatchedPolicy.DENY_ALL.shouldDenyUnmatched(
                org.springframework.http.HttpMethod.GET));
        assertEquals(true, RbacUnmatchedPolicy.DENY_ALL.shouldDenyUnmatched(
                org.springframework.http.HttpMethod.POST));
    }

    @Test
    @DisplayName("非法取值抛 IllegalArgumentException")
    void invalidValueRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RbacUnmatchedPolicy.parse("STRICT"));
        assertThrows(IllegalArgumentException.class,
                () -> PermissionAuthorizationManager.resolvePolicy("STRICT", ""));
    }
}

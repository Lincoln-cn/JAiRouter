package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URL 权限规则表守卫（#567 权限语义化）
 *
 * <p>三条断言：
 * <ul>
 *   <li><b>码必须存在</b>：每条规则的 {@code permissionCode} 都必须在
 *   {@link PermissionCodes#ALL_PERMISSION_CODES} 里 —— 防「规则里拼错一个码，那条规则永远匹配不上任何人」
 *   这种静默失效。</li>
 *   <li><b>不许「用写码读」</b>：只放行 GET 的规则不得要求 {@code :write} / {@code :manage} 码
 *   （#567 之前 accounts / blacklist / permissions / api-keys / sanitization / tracing-security 的
 *   读面全挂管理码，等于「看一眼列表」必须先拿到写权限）。</li>
 *   <li><b>读写分流矩阵</b>：#567 的 8 组资源，GET 落到 {@code :read}、写方法仍落到原 {@code :manage}。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.3.1
 */
@DisplayName("PermissionRuleRegistry 规则表语义（#567）")
class PermissionRuleRegistryTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    @Test
    @DisplayName("规则表里出现的每个权限码都存在于 ALL_PERMISSION_CODES（防拼写漂移）")
    void everyRuleCodeExistsInCatalog() {
        List<String> unknown = registry.getRules().stream()
                .map(PermissionRule::permissionCode)
                .filter(code -> !PermissionCodes.ALL_PERMISSION_CODES.contains(code))
                .distinct()
                .collect(Collectors.toList());
        assertTrue(unknown.isEmpty(), "规则表引用了未登记的权限码（拼写漂移？）: " + unknown);
    }

    @Test
    @DisplayName("只放行 GET 的规则不得挂 :write / :manage 码（不许用写码读）")
    void getOnlyRulesNeverRequireWriteOrManageCodes() {
        List<String> offenders = registry.getRules().stream()
                .filter(rule -> rule.methods().equals(Set.of(HttpMethod.GET)))
                .filter(rule -> rule.permissionCode().endsWith(":write")
                        || rule.permissionCode().endsWith(":manage"))
                .map(rule -> rule.pathPattern() + " -> " + rule.permissionCode())
                .collect(Collectors.toList());
        assertTrue(offenders.isEmpty(), "存在「用写码读」的 GET 规则: " + offenders);
    }

    @Test
    @DisplayName("#567 8 组资源的 GET 读面绑定到对应 :read 码")
    void readEndpointsAreBoundToReadCodes() {
        assertRuleCode(HttpMethod.GET, "/api/security/jwt/accounts", PermissionCodes.SYSTEM_ACCOUNTS_READ);
        assertRuleCode(HttpMethod.GET, "/api/security/jwt/accounts/admin", PermissionCodes.SYSTEM_ACCOUNTS_READ);
        assertRuleCode(HttpMethod.GET, "/api/security/blacklist/list", PermissionCodes.SECURITY_BLACKLIST_READ);
        assertRuleCode(HttpMethod.GET, "/api/security/blacklist/stats", PermissionCodes.SECURITY_BLACKLIST_READ);
        assertRuleCode(HttpMethod.GET, "/api/security/permissions", PermissionCodes.SYSTEM_PERMISSIONS_READ);
        assertRuleCode(HttpMethod.GET, "/api/security/permissions/roles", PermissionCodes.SYSTEM_PERMISSIONS_READ);
        assertRuleCode(HttpMethod.GET, "/api/config/sanitization", PermissionCodes.SECURITY_SANITIZATION_READ);
        assertRuleCode(HttpMethod.GET, "/api/config/sanitization/rules", PermissionCodes.SECURITY_SANITIZATION_READ);
        assertRuleCode(HttpMethod.GET, "/api/config/tracing/security/overview", PermissionCodes.TRACING_CONFIG_READ);
        assertRuleCode(HttpMethod.GET, "/api/loadbalancer/config/global", PermissionCodes.LB_CONFIG_READ);
        assertRuleCode(HttpMethod.GET, "/api/loadbalancer/config/chat", PermissionCodes.LB_CONFIG_READ);
        assertRuleCode(HttpMethod.GET, "/api/auth/api-keys", PermissionCodes.SECURITY_APIKEYS_READ);
        assertRuleCode(HttpMethod.GET, "/api/auth/api-keys/k1/quota", PermissionCodes.SECURITY_APIKEYS_READ);
        assertRuleCode(HttpMethod.GET, "/api/auth/jwt/cleanup/stats", PermissionCodes.SECURITY_JWTTOKENS_READ);
        assertRuleCode(HttpMethod.GET, "/api/auth/jwt/blacklist/stats", PermissionCodes.SECURITY_BLACKLIST_READ);
        assertRuleCode(HttpMethod.GET, "/api/auth/jwt/tokens/abc-123", PermissionCodes.SECURITY_JWTTOKENS_READ);
    }

    @Test
    @DisplayName("#567 写/管理方法仍绑定原来的 :write / :manage 码（语义不放松）")
    void writeEndpointsStillRequireManageCodes() {
        assertRuleCode(HttpMethod.POST, "/api/security/jwt/accounts", PermissionCodes.SYSTEM_ACCOUNTS_MANAGE);
        assertRuleCode(HttpMethod.PUT, "/api/security/jwt/accounts/admin", PermissionCodes.SYSTEM_ACCOUNTS_MANAGE);
        assertRuleCode(HttpMethod.PATCH, "/api/security/jwt/accounts/admin/status",
                PermissionCodes.SYSTEM_ACCOUNTS_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/security/jwt/accounts/admin/verify",
                PermissionCodes.SYSTEM_ACCOUNTS_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/security/blacklist/add", PermissionCodes.SECURITY_BLACKLIST_MANAGE);
        assertRuleCode(HttpMethod.DELETE, "/api/security/blacklist/1", PermissionCodes.SECURITY_BLACKLIST_MANAGE);
        assertRuleCode(HttpMethod.PUT, "/api/security/permissions/roles/USER",
                PermissionCodes.SYSTEM_PERMISSIONS_MANAGE);
        assertRuleCode(HttpMethod.PUT, "/api/config/sanitization", PermissionCodes.SECURITY_SANITIZATION_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/config/sanitization/test",
                PermissionCodes.SECURITY_SANITIZATION_MANAGE);
        assertRuleCode(HttpMethod.DELETE, "/api/config/tracing/security/access/cache",
                PermissionCodes.TRACING_CONFIG_MANAGE);
        assertRuleCode(HttpMethod.PUT, "/api/loadbalancer/config/chat", PermissionCodes.LB_CONFIG_WRITE);
        assertRuleCode(HttpMethod.POST, "/api/auth/api-keys", PermissionCodes.SECURITY_APIKEYS_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/auth/jwt/cleanup", PermissionCodes.SECURITY_JWTTOKENS_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/auth/jwt/revoke/batch", PermissionCodes.SECURITY_JWTTOKENS_MANAGE);
    }

    @Test
    @DisplayName("负载均衡：配置读与监控读仍然分离，具体路径优先")
    void loadBalancerConfigReadIsSeparatedFromMonitoringRead() {
        assertRuleCode(HttpMethod.GET, "/api/loadbalancer/config/global", PermissionCodes.LB_CONFIG_READ);
        assertRuleCode(HttpMethod.GET, "/api/loadbalancer/status", PermissionCodes.LB_MONITORING_READ);
        assertRuleCode(HttpMethod.GET, "/api/loadbalancer/strategies", PermissionCodes.LB_MONITORING_READ);
        assertRuleCode(HttpMethod.GET, "/api/loadbalancer/stats", PermissionCodes.LB_MONITORING_READ);
    }

    @Test
    @DisplayName("自助端点仍无规则：#567 没有顺手给它加码（避免把自助能力收进管理面）")
    void selfServiceJwtEndpointsStayUnregistered() {
        List<String> stillUnregistered = new ArrayList<>();
        for (String[] probe : new String[][]{
                {"POST", "/api/auth/jwt/revoke"},
                {"GET", "/api/auth/jwt/tokens"},
                {"POST", "/api/auth/jwt/refresh"}}) {
            if (registry.findRule(HttpMethod.valueOf(probe[0]), probe[1]).isPresent()) {
                stillUnregistered.add(probe[0] + " " + probe[1]);
            }
        }
        assertTrue(stillUnregistered.isEmpty(), "这些自助端点不应被规则匹配到: " + stillUnregistered);
    }

    private void assertRuleCode(final HttpMethod method, final String path, final String expectedCode) {
        PermissionRule rule = registry.findRule(method, path)
                .orElseThrow(() -> new AssertionError(method + " " + path + " 未命中任何权限规则"));
        assertEquals(expectedCode, rule.permissionCode(),
                method + " " + path + " 的权限码不符（命中规则 " + rule.pathPattern() + "）");
        assertFalse(rule.permissionCode().isBlank());
    }
}

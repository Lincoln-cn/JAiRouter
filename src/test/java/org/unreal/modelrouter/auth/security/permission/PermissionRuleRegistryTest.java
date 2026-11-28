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
                .flatMap(rule -> rule.permissionCodes().stream())
                .filter(code -> !PermissionCodes.ALL_PERMISSION_CODES.contains(code))
                .distinct()
                .collect(Collectors.toList());
        assertTrue(unknown.isEmpty(), "规则表引用了未登记的权限码（拼写漂移？）: " + unknown);
    }

    @Test
    @DisplayName("#1183：数据面读规则同时接受「全局读码」与「本人读码」")
    void dataScopeRulesAcceptBothGlobalAndSelfCodes() {
        List<String> dataPaths = List.of("/api/call-history/**", "/api/token-usage/**", "/api/exceptions/**");
        for (String path : dataPaths) {
            PermissionRule rule = registry.findRule(HttpMethod.GET, path.replace("/**", "/x"))
                    .orElseThrow(() -> new AssertionError("数据面路径未登记 GET 规则: " + path));
            assertTrue(rule.permissionCodes().size() >= 2,
                    path + " 应同时接受全局读码与本人读码，实际: " + rule.permissionCodes());
            assertTrue(rule.permissionCodes().stream().anyMatch(code -> code.endsWith(":self:read")),
                    path + " 缺少本人读码: " + rule.permissionCodes());
            assertTrue(rule.permissionCodes().stream().anyMatch(code -> !code.endsWith(":self:read")),
                    path + " 缺少全局读码（观察者/运维仍需全局读面）: " + rule.permissionCodes());
        }
    }

    @Test
    @DisplayName("#1183：写规则不得接受本人读码（self 码只放行读）")
    void writeRulesNeverAcceptSelfCodes() {
        List<String> offenders = registry.getRules().stream()
                .filter(rule -> rule.methods().contains(HttpMethod.PUT)
                        || rule.methods().contains(HttpMethod.DELETE))
                .filter(rule -> rule.permissionCodes().stream().anyMatch(code -> code.contains(":self:")))
                .map(rule -> rule.pathPattern() + " -> " + rule.permissionCodes())
                .collect(Collectors.toList());
        assertTrue(offenders.isEmpty(), "写规则挂了本人读码: " + offenders);
    }

    @Test
    @DisplayName("只放行 GET 的规则不得挂 :write / :manage 码（不许用写码读）")
    void getOnlyRulesNeverRequireWriteOrManageCodes() {
        List<String> offenders = registry.getRules().stream()
                .filter(rule -> rule.methods().equals(Set.of(HttpMethod.GET)))
                // 只要求「已认证」的规则（permissionCodes 为空）不参与本断言：
                // 它们不绑定任何码，自然也不会「用写码读」
                .filter(rule -> {
                    final String code = rule.permissionCode();
                    return code != null && (code.endsWith(":write") || code.endsWith(":manage"));
                })
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
        assertRuleCode(HttpMethod.DELETE, "/api/config/tracing/security/access/cache",
                PermissionCodes.TRACING_CONFIG_MANAGE);
        assertRuleCode(HttpMethod.PUT, "/api/loadbalancer/config/chat", PermissionCodes.LB_CONFIG_WRITE);
        assertRuleCode(HttpMethod.POST, "/api/auth/api-keys", PermissionCodes.SECURITY_APIKEYS_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/auth/jwt/cleanup", PermissionCodes.SECURITY_JWTTOKENS_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/auth/jwt/revoke/batch", PermissionCodes.SECURITY_JWTTOKENS_MANAGE);
    }

    @Test
    @DisplayName("只用 POST 传参的只读端点挂 :read，同类写端点仍挂 :manage（精确规则先于前缀命中）")
    void readOnlyPostEndpointsRequireReadCode() {
        // 试脱敏与范围预览都只拿当前规则/scope 做试算，不落库、不改配置
        assertRuleCode(HttpMethod.POST, "/api/config/sanitization/test",
                PermissionCodes.SECURITY_SANITIZATION_READ);
        assertRuleCode(HttpMethod.POST, "/api/config/sanitization/scope/preview",
                PermissionCodes.SECURITY_SANITIZATION_READ);
        // 导出是读库生成导出件；清理才是写
        assertRuleCode(HttpMethod.POST, "/api/tracing/query/export", PermissionCodes.TRACING_SEARCH_READ);
        assertRuleCode(HttpMethod.POST, "/api/tracing/query/cleanup",
                PermissionCodes.TRACING_CONFIG_MANAGE);
    }

    @Test
    @DisplayName("#1173：应急操作影响面预览挂 monitoring:config:read，且精确规则先于 /api/monitoring/** 通配")
    void emergencyPreviewRequiresConfigRead() {
        // 精确规则：只读预览，不要求写码；但也不该落到通配的 monitoring:metrics:read 上 ——
        // 那会把「运维配置面」的现状暴露给只持 metrics:read 的角色。
        assertRuleCode(HttpMethod.GET, "/api/monitoring/emergency/preview",
                PermissionCodes.MONITORING_CONFIG_READ);
        // 通配规则仍然生效：其余只读端点还是 monitoring:metrics:read
        assertRuleCode(HttpMethod.GET, "/api/monitoring/health", PermissionCodes.MONITORING_METRICS_READ);
        assertRuleCode(HttpMethod.GET, "/api/monitoring/cache/stats",
                PermissionCodes.MONITORING_METRICS_READ);
        // 写端点仍是 monitoring:config:write，没有因为新增读码而放松
        assertRuleCode(HttpMethod.PUT, "/api/monitoring/config/prefix",
                PermissionCodes.MONITORING_CONFIG_WRITE);
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

    @Test
    @DisplayName("登录保护：查询用读码、解锁用管理码（issue #1176）")
    void loginGuardReadAndManageAreSeparated() {
        assertRuleCode(HttpMethod.GET, "/api/security/login-guard", PermissionCodes.SECURITY_LOGINGUARD_READ);
        assertRuleCode(HttpMethod.GET, "/api/security/login-guard/locked", PermissionCodes.SECURITY_LOGINGUARD_READ);
        assertRuleCode(HttpMethod.PUT, "/api/security/login-guard", PermissionCodes.SECURITY_LOGINGUARD_MANAGE);
        assertRuleCode(HttpMethod.POST, "/api/security/login-guard/unlock",
                PermissionCodes.SECURITY_LOGINGUARD_MANAGE);
    }

    @Test
    @DisplayName("限流配置的写操作要求 config:ratelimit:write（issue #1241）")
    void rateLimitWriteRequiresWriteLevelCode() {
        assertRuleCode(HttpMethod.PUT, "/api/services/chat/ratelimit", PermissionCodes.CONFIG_RATELIMIT_WRITE);
        assertRuleCode(HttpMethod.POST, "/api/services/chat/ratelimit", PermissionCodes.CONFIG_RATELIMIT_WRITE);
        assertRuleCode(HttpMethod.DELETE, "/api/services/chat/ratelimit", PermissionCodes.CONFIG_RATELIMIT_WRITE);
        // 读面保持监控读码是刻意的：只读不构成越权，改它会破坏按 rl:monitoring:read 读该路径的客户端
        assertRuleCode(HttpMethod.GET, "/api/services/chat/ratelimit", PermissionCodes.RL_MONITORING_READ);
    }

    /**
     * 只读语义的 POST 端点（用 POST 传查询体，如审计查询 / 导出 / 脱敏试跑）。
     *
     * <p>它们由 {@link PermissionRule#post(String, String)} 登记、挂读码是刻意设计，
     * 见该方法 javadoc。新增条目必须在这里登记并说明理由，避免「真写操作」混进来。
     */
    private static final Set<String> READ_ONLY_POST_PATTERNS = Set.of(
            "/api/security/audit/extended/query",
            "/api/tracing/query/export",
            "/api/config/sanitization/test",
            "/api/config/sanitization/scope/preview");

    @Test
    @DisplayName("PUT/PATCH/DELETE 不得只要求 :read 码（issue #1241 的通用守卫）")
    void modifyingMethodsDoNotRequireOnlyReadLevelCode() {
        List<String> offenders = registry.getRules().stream()
                .filter(rule -> rule.methods().stream()
                        .anyMatch(method -> method == HttpMethod.PUT
                                || method == HttpMethod.PATCH
                                || method == HttpMethod.DELETE))
                .filter(rule -> !rule.permissionCodes().isEmpty())
                .filter(rule -> rule.permissionCodes().stream().allMatch(code -> code.endsWith(":read")))
                .map(rule -> rule.methods() + " " + rule.pathPattern() + " -> " + rule.permissionCodes())
                .collect(Collectors.toList());
        assertTrue(offenders.isEmpty(),
                "这些改状态的 HTTP 方法只要求了 :read 码，任何持该读码的主体都能执行写操作（#1241）: " + offenders);
    }

    @Test
    @DisplayName("POST 只挂 :read 码的，必须是已登记的只读语义端点")
    void readOnlyPostRulesAreExplicitlyRegistered() {
        List<String> unregistered = registry.getRules().stream()
                .filter(rule -> rule.methods().size() == 1 && rule.methods().contains(HttpMethod.POST))
                .filter(rule -> !rule.permissionCodes().isEmpty())
                .filter(rule -> rule.permissionCodes().stream().allMatch(code -> code.endsWith(":read")))
                .map(PermissionRule::pathPattern)
                .filter(pattern -> !READ_ONLY_POST_PATTERNS.contains(pattern))
                .collect(Collectors.toList());
        assertTrue(unregistered.isEmpty(),
                "这些 POST 规则只挂读码但没登记为只读语义端点；若它真的会改状态，"
                        + "请改用 :write 码，否则加入 READ_ONLY_POST_PATTERNS（#1241）: " + unregistered);
    }

    private void assertRuleCode(final HttpMethod method, final String path, final String expectedCode) {
        PermissionRule rule = registry.findRule(method, path)
                .orElseThrow(() -> new AssertionError(method + " " + path + " 未命中任何权限规则"));
        assertEquals(expectedCode, rule.permissionCode(),
                method + " " + path + " 的权限码不符（命中规则 " + rule.pathPattern() + "）");
        assertFalse(rule.permissionCode().isBlank());
    }
}

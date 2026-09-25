package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #128 Phase 3 前置门禁：Phase 1 实测的 19 个未覆盖端点必须全部「已登记规则 或 显式豁免」。
 *
 * <p>清单来自 Phase 1 真实 boot 自检（checked=313, covered=294, uncovered=19, excluded=45）。
 * 任何一条回到 MISSING 都意味着缺口回潮，禁止继续翻转 GET fail-closed。
 *
 * <p>判定使用<b>真实</b> {@link PermissionRuleRegistry} + {@link RbacExemptEndpoints}，
 * 不手抄预期结果，避免清单与实现漂移。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@DisplayName("RBAC Phase 1 遗留 19 缺口清零门禁（#128 Phase 3 前置）")
class RbacPrePhase2UncoveredEndpointsGateTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    /** 一条 Phase 1 缺口：HTTP 方法 + 具体请求路径 */
    private record Uncovered(HttpMethod method, String path) {
        String label() {
            return method.name() + " " + path;
        }
    }

    private static List<Uncovered> phase1UncoveredNineteen() {
        return List.of(
                new Uncovered(HttpMethod.GET, "/api/auth/jwt/blacklist/stats"),
                new Uncovered(HttpMethod.GET, "/api/auth/jwt/cleanup/stats"),
                new Uncovered(HttpMethod.GET, "/api/auth/jwt/tokens"),
                new Uncovered(HttpMethod.GET, "/api/auth/jwt/tokens/tok-1"),
                new Uncovered(HttpMethod.GET, "/api/auth/permissions"),
                new Uncovered(HttpMethod.GET, "/api/models"),
                new Uncovered(HttpMethod.POST, "/api/auth/jwt/cleanup"),
                new Uncovered(HttpMethod.POST, "/api/auth/jwt/refresh"),
                new Uncovered(HttpMethod.POST, "/api/auth/jwt/revoke"),
                new Uncovered(HttpMethod.POST, "/api/auth/jwt/revoke/batch"),
                new Uncovered(HttpMethod.POST, "/api/token-usage/record"),
                new Uncovered(HttpMethod.POST, "/api/token-usage/record/batch"),
                new Uncovered(HttpMethod.POST, "/api/v1/audio/speech"),
                new Uncovered(HttpMethod.POST, "/api/v1/audio/transcriptions"),
                new Uncovered(HttpMethod.POST, "/api/v1/chat/completions"),
                new Uncovered(HttpMethod.POST, "/api/v1/embeddings"),
                new Uncovered(HttpMethod.POST, "/api/v1/images/edits"),
                new Uncovered(HttpMethod.POST, "/api/v1/images/generations"),
                new Uncovered(HttpMethod.POST, "/api/v1/rerank")
        );
    }

    @Test
    @DisplayName("Phase 1 的 19 个缺口：每个都已登记规则或显式豁免，MISSING 必须为 0")
    void everyPrePhase2UncoveredEndpointIsMatchedOrExempt() {
        List<Uncovered> endpoints = phase1UncoveredNineteen();
        assertEquals(19, endpoints.size(), "门禁清单必须恰好 19 条（Phase 1 实测值）");

        List<String> missing = new ArrayList<>();
        for (Uncovered endpoint : endpoints) {
            boolean matched = registry.findRule(endpoint.method(), endpoint.path()).isPresent();
            boolean exempt = RbacExemptEndpoints.isExempt(endpoint.method(), endpoint.path());
            if (!matched && !exempt) {
                missing.add(endpoint.label());
            }
        }
        assertEquals(List.of(), missing,
                "未豁免且未登记清单必须为空（#128 门禁）——发现 MISSING 时禁止翻转 GET fail-closed");
    }

    @Test
    @DisplayName("19 条缺口的分类可追溯：至少 6 GET 中有规则命中，至少有豁免命中（防清单空转）")
    void classificationIsMeaningful() {
        int matched = 0;
        int exempt = 0;
        for (Uncovered endpoint : phase1UncoveredNineteen()) {
            if (registry.findRule(endpoint.method(), endpoint.path()).isPresent()) {
                matched++;
            } else if (RbacExemptEndpoints.isExempt(endpoint.method(), endpoint.path())) {
                exempt++;
            }
        }
        assertTrue(matched >= 5, "至少 5 条应由规则覆盖（Phase 2 已补 JWT 运维规则），实际 matched=" + matched);
        assertTrue(exempt >= 14, "至少 14 条应命中豁免清单（服务代理/自助/摄取），实际 exempt=" + exempt);
        assertEquals(19, matched + exempt, "19 条必须被规则或豁免完全瓜分");
    }
}

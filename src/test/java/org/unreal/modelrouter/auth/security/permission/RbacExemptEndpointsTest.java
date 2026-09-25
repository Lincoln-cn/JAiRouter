package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 显式豁免清单（#128 Phase 2）单元测试。
 *
 * <p>清单是 fail-closed 写策略的安全阀：条目过宽会重新打开缺口，过窄会误伤
 * 服务代理面/自助端点。逐条验证匹配边界与理由完整性。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@DisplayName("RbacExemptEndpoints 显式豁免清单测试")
class RbacExemptEndpointsTest {

    private static final List<RbacExemptEndpoints.Exemption> ENTRIES = RbacExemptEndpoints.exemptions();

    @Test
    @DisplayName("每条豁免都携带非空理由（审查可追溯）")
    void everyEntryHasNonBlankReason() {
        assertFalse(ENTRIES.isEmpty(), "豁免清单不应为空");
        for (RbacExemptEndpoints.Exemption entry : ENTRIES) {
            assertTrue(entry.reason() != null && !entry.reason().isBlank(),
                    "豁免条目缺少理由: " + entry.pathPattern());
        }
    }

    @Test
    @DisplayName("豁免清单包含 19 缺口端点中应豁免的全部路径（按端点级）")
    void allPlannedExemptEndpointsAreListed() {
        // AI 服务代理面（7 个 POST）
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/chat/completions"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/embeddings"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/rerank"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/audio/speech"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/audio/transcriptions"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/images/generations"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/images/edits"));
        // JWT 自助
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/auth/jwt/refresh"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/auth/jwt/revoke"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/auth/jwt/tokens"));
        // 控制台公共读取
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/auth/permissions"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/models"));
        // Token 用量上报摄取
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/token-usage/record"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/token-usage/record/batch"));
    }

    @Test
    @DisplayName("应保护的端点不得命中豁免清单")
    void protectedEndpointsAreNotExempt() {
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/auth/jwt/cleanup"),
                "令牌清理是运维操作，必须走权限规则");
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/auth/jwt/cleanup/stats"),
                "清理统计是运维查询，必须走权限规则");
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/auth/jwt/blacklist/stats"),
                "黑名单统计是运维查询，必须走权限规则");
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/auth/jwt/revoke/batch"),
                "批量撤销是管理员操作，必须走权限规则");
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/auth/jwt/tokens/x"),
                "令牌详情是管理员操作，必须走权限规则");
    }

    @Test
    @DisplayName("/api/v1/** 豁免限定写方法：GET 不豁免")
    void apiV1ExemptionIsWriteOnly() {
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/anything"));
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.DELETE, "/api/v1/anything"));
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/v1/anything"),
                "GET /api/v1/** 不豁免，未登记时应作为缺口暴露");
    }

    @Test
    @DisplayName("自助端点豁免限定精确方法/路径，不越界")
    void selfServiceExemptionsArePrecise() {
        // GET /api/auth/jwt/tokens 豁免，但 detail 子路径不豁免
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/auth/jwt/tokens"));
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/auth/jwt/tokens/abc"));
        // POST /api/auth/jwt/revoke 豁免，但 batch 子路径不豁免
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/auth/jwt/revoke"));
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/auth/jwt/revoke/batch"));
        // GET-only 豁免不覆盖写方法
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/models"));
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/auth/permissions"));
    }

    @Test
    @DisplayName("豁免条目方法集合语义：空集合=任意方法，否则精确匹配")
    void exemptionMethodSemantics() {
        // write 工厂覆盖四种写方法
        RbacExemptEndpoints.Exemption write = RbacExemptEndpoints.Exemption.write("/probe/**", "reason");
        assertEquals(Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
                write.methods());
        // get/post 工厂
        assertEquals(Set.of(HttpMethod.GET), RbacExemptEndpoints.Exemption.get("/p", "r").methods());
        assertEquals(Set.of(HttpMethod.POST), RbacExemptEndpoints.Exemption.post("/p", "r").methods());
        // 实际清单里没有「任意方法」条目——服务面仅写方法豁免
        for (RbacExemptEndpoints.Exemption entry : ENTRIES) {
            assertFalse(entry.methods().isEmpty(),
                    "当前清单不应有任意方法豁免（过宽）: " + entry.pathPattern());
        }
    }

    @Test
    @DisplayName("豁免不与规则覆盖端点重叠（规则优先，豁免是未登记路径的语义）")
    void exemptionsDoNotShadowRegisteredRules() {
        PermissionRuleRegistry registry = new PermissionRuleRegistry();
        for (RbacExemptEndpoints.Exemption entry : ENTRIES) {
            String sample = RbacEndpointCoverageChecker.toSamplePath(entry.pathPattern());
            for (HttpMethod method : entry.methods()) {
                assertTrue(registry.findRule(method, sample).isEmpty(),
                        "豁免端点不应同时被规则覆盖（会掩盖豁免语义）: " + method + " " + entry.pathPattern());
            }
        }
    }
}

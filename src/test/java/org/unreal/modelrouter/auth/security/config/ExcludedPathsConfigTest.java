package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据脱敏排除路径契约（v3.2.0 设计裁决）.
 *
 * <p>PII 主战场是聊天调用历史/日志/追踪记录，经
 * {@code SanitizationService.sanitizeForStorage} 处理；
 * ResponseSanitizationFilter 只作用于未排除的 HTTP JSON 响应，
 * 且必须排除管理台与 AI 实时路径。</p>
 */
@DisplayName("ExcludedPathsConfig 脱敏排除路径")
class ExcludedPathsConfigTest {

    @ParameterizedTest(name = "[{index}] 管理台路径应排除网关响应脱敏: {0}")
    @ValueSource(strings = {
            "/api/config/quota",
            "/api/config/sanitization",
            "/api/monitoring/quota/usage",
            "/api/auth/api-keys/key-1/quota",
            "/api/call-history/list",
            "/api/security/audit/extended",
            "/admin/index.html"
    })
    void adminPaths_shouldBeExcludedFromGatewayMasking(String path) {
        assertTrue(ExcludedPathsConfig.isDataMaskExcluded(path),
                path + " 不应被网关响应脱敏改写");
    }

    @ParameterizedTest(name = "[{index}] AI 实时路径应排除网关响应脱敏: {0}")
    @ValueSource(strings = {
            "/api/v1/chat/completions",
            "/api/v1/embeddings",
            "/v1/messages",
            "/v1/models"
    })
    void aiRealtimePaths_shouldBeExcludedFromGatewayMasking(String path) {
        assertTrue(ExcludedPathsConfig.isDataMaskExcluded(path),
                path + " 不应被网关响应脱敏改写（客户端模型输出完整性）");
    }

    @ParameterizedTest(name = "[{index}] 运维端点应排除: {0}")
    @ValueSource(strings = {
            "/actuator/health",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/favicon.ico"
    })
    void opsPaths_shouldBeExcluded(String path) {
        assertTrue(ExcludedPathsConfig.isDataMaskExcluded(path));
    }

    @Test
    @DisplayName("认证排除与脱敏排除语义独立：登录路径认证排除，且脱敏也排除")
    void authLogin_isBothAuthAndMaskExcluded() {
        assertTrue(ExcludedPathsConfig.isAuthExcluded("/api/auth/jwt/login"));
        // /api/ 前缀已覆盖
        assertTrue(ExcludedPathsConfig.isDataMaskExcluded("/api/auth/jwt/login"));
    }

    @Test
    @DisplayName("P1：/ws/** 不得排除认证（监控 WebSocket 需校验 token）")
    void wsPaths_mustNotBeAuthExcluded() {
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/ws/routing-monitor"),
                "/ws/** 应由 Spring Security 认证保护，不得进入认证排除列表");
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/ws/circuit-breaker-monitor"));
    }

    @Test
    @DisplayName("P1：/api/health-status/** 不得排除认证")
    void healthStatusSse_mustNotBeAuthExcluded() {
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/api/health-status/stream"));
    }

    // ------------------------------------------------------------------ issue #340

    @ParameterizedTest(name = "[{index}] 匿名放行清单必须仍排除认证: {0}")
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus"
    })
    void anonymousOpsPaths_stayAuthExcluded(String path) {
        assertTrue(ExcludedPathsConfig.isAuthExcluded(path),
                path + " 必须保持匿名可访问（k8s 探针无凭据 / Prometheus 抓取）");
    }

    @ParameterizedTest(name = "[{index}] 其余 /actuator/** 不得排除认证: {0}")
    @ValueSource(strings = {
            "/actuator/metrics",
            "/actuator/metrics/status",
            "/actuator/metrics/performance",
            "/actuator/jwt-persistence-health"
    })
    void otherActuatorPaths_mustNotBeAuthExcluded(String path) {
        // issue #340：这些端点此前被**整段**排除在认证之外，而 SecurityConfiguration 又要求
        // ROLE_ADMIN ⇒ 两者互相抵消（带 ADMIN JWT 也是 AUTH_MISSING/401，文档里的 curl 全都跑不通）。
        // 现在它们走正常认证链，管理面 JWT 才能满足 hasRole("ADMIN")。
        assertFalse(ExcludedPathsConfig.isAuthExcluded(path),
                path + " 必须走认证链，否则「要求 ROLE_ADMIN」与「排除认证」会互相抵消（issue #340）");
    }

    @Test
    @DisplayName("设计说明：记录侧脱敏不依赖本过滤器排除列表")
    void recordSideSanitization_isIndependentOfExcludedPaths() {
        // 契约文档化：调用历史 SUMMARY 走 sanitizeForStorage，即使路径被排除也应脱敏
        // （此处仅断言排除列表不会误伤「非 HTTP 记录」场景的概念边界）
        assertFalse(ExcludedPathsConfig.isDataMaskExcluded("call-history:record:summary"));
    }
}

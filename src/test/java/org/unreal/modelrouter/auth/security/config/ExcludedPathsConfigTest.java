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
    @DisplayName("设计说明：记录侧脱敏不依赖本过滤器排除列表")
    void recordSideSanitization_isIndependentOfExcludedPaths() {
        // 契约文档化：调用历史 SUMMARY 走 sanitizeForStorage，即使路径被排除也应脱敏
        // （此处仅断言排除列表不会误伤「非 HTTP 记录」场景的概念边界）
        assertFalse(ExcludedPathsConfig.isDataMaskExcluded("call-history:record:summary"));
    }
}

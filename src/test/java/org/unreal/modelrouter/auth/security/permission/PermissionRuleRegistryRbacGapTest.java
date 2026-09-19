package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2-P1-03：关键管理端点必须登记权限规则，禁止回退「任意登录用户」。
 */
@DisplayName("PermissionRuleRegistry RBAC 缺口补齐")
class PermissionRuleRegistryRbacGapTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    private void assertRuleExists(HttpMethod method, String path, String why) {
        assertTrue(registry.findRule(method, path).isPresent(),
                why + " 必须登记权限规则: " + method + " " + path);
    }

    @Test
    @DisplayName("/api/exceptions/** 必须有规则（查询/删除）")
    void exceptionsApi_hasRules() {
        assertRuleExists(HttpMethod.GET, "/api/exceptions", "异常事件查询");
        assertRuleExists(HttpMethod.DELETE, "/api/exceptions/1", "异常事件删除");
        assertRuleExists(HttpMethod.GET, "/api/exceptions/dashboard", "异常仪表盘");
    }

    @Test
    @DisplayName("/api/config/sources 与 environment-variables 必须有规则")
    void configValidationGuide_hasRules() {
        assertRuleExists(HttpMethod.GET, "/api/config/sources", "配置来源");
        assertRuleExists(HttpMethod.GET, "/api/config/environment-variables", "环境变量指南");
    }

    @Test
    @DisplayName("已登记的配置写路径仍保持写权限（回归）")
    void instanceWrite_stillProtected() {
        assertTrue(registry.findRule(HttpMethod.POST, "/api/config/instance/chat").isPresent());
    }
}

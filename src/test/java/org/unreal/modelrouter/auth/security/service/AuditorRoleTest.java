package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AUDITOR 角色守卫（issue #1183）
 *
 * <p>用户明确要求「给第三方审计用就严格一点」：本角色是**对外授权面**，任何加码都等于把
 * 客户的业务数据/配置暴露给外部审计方。因此把「恰好三码」写成硬断言 ——
 * 想让审计方多看一页，必须先改这条测试并在评审里说明理由。
 *
 * <p>AUDITOR 只含：概览、审计事件、管理操作记录。刻意不含：调用记录（业务数据，且带正文风险）、
 * {@code config:*:read}（能看到上游地址与密钥前缀）、任何写码。
 *
 * @author JAiRouter Team
 * @since 3.8.9
 */
@DisplayName("AUDITOR 第三方审计最小集（#1183）")
class AuditorRoleTest {

    private final List<String> codes = RolePermissionSeeder.DEFAULT_ROLE_TEMPLATES.get("AUDITOR");

    @Test
    @DisplayName("恰好三码：概览 + 审计事件 + 管理操作记录")
    void exactlyThreeCodes() {
        assertEquals(List.of(
                PermissionCodes.OVERVIEW_DASHBOARD_READ,
                PermissionCodes.SECURITY_AUDIT_READ,
                PermissionCodes.MONITORING_CONSOLE_READ), codes);
    }

    @Test
    @DisplayName("不含任何写/管理码")
    void noWriteOrManageCodes() {
        assertTrue(codes.stream().noneMatch(code -> code.endsWith(":write") || code.endsWith(":manage")),
                "AUDITOR 不得含写/管理码: " + codes);
    }

    @Test
    @DisplayName("不含业务数据面：调用记录（含正文）、Token 用量、异常、慢 SQL")
    void noBusinessDataCodes() {
        assertTrue(codes.stream().noneMatch(code -> code.startsWith("callhistory:")),
                "AUDITOR 不得读调用记录（业务数据）: " + codes);
        assertTrue(codes.stream().noneMatch(code -> code.startsWith("monitoring:tokenusage")),
                "AUDITOR 不得读 Token 用量: " + codes);
        assertTrue(codes.stream().noneMatch(code -> code.startsWith("monitoring:exceptions")),
                "AUDITOR 不得读异常明细: " + codes);
        assertTrue(codes.stream().noneMatch(code -> code.startsWith("monitoring:slowquery")),
                "AUDITOR 不得读慢查询: " + codes);
    }

    @Test
    @DisplayName("不含任何配置读码（能看到上游地址与密钥前缀）")
    void noConfigReadCodes() {
        assertTrue(codes.stream().noneMatch(code -> code.startsWith("config:")),
                "AUDITOR 不得读配置: " + codes);
        assertTrue(codes.stream().noneMatch(code -> code.startsWith("system:")),
                "AUDITOR 不得读账号/权限/SLA: " + codes);
    }

    @Test
    @DisplayName("不含安全对象读码（密钥/令牌/黑名单/脱敏/登录保护）")
    void noSecurityObjectCodes() {
        assertFalse(codes.contains(PermissionCodes.SECURITY_APIKEYS_READ));
        assertFalse(codes.contains(PermissionCodes.SECURITY_JWTTOKENS_READ));
        assertFalse(codes.contains(PermissionCodes.SECURITY_BLACKLIST_READ));
        assertFalse(codes.contains(PermissionCodes.SECURITY_SANITIZATION_READ));
        assertFalse(codes.contains(PermissionCodes.SECURITY_LOGINGUARD_READ));
    }
}

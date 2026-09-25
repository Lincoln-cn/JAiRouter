package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2-P1-03 / #116：关键管理端点必须登记权限规则，禁止回退「任意登录用户」。
 */
@DisplayName("PermissionRuleRegistry RBAC 缺口补齐")
class PermissionRuleRegistryRbacGapTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    private void assertRuleExists(HttpMethod method, String path, String why) {
        assertTrue(registry.findRule(method, path).isPresent(),
                why + " 必须登记权限规则: " + method + " " + path);
    }

    private void assertRuleCode(HttpMethod method, String path, String expectedCode, String why) {
        PermissionRule rule = registry.findRule(method, path)
                .orElseThrow(() -> new AssertionError(why + " 必须登记权限规则: " + method + " " + path));
        assertEquals(expectedCode, rule.permissionCode(),
                why + " 权限码不正确: " + method + " " + path);
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

    @Test
    @DisplayName("/api/monitoring 管理端点写操作必须登记 monitoring:config:write（#116）")
    void monitoringAdminWrites_haveWriteRule() {
        assertRuleCode(HttpMethod.PUT, "/api/monitoring/config/enabled",
                PermissionCodes.MONITORING_CONFIG_WRITE, "监控开关");
        assertRuleCode(HttpMethod.PUT, "/api/monitoring/config/prefix",
                PermissionCodes.MONITORING_CONFIG_WRITE, "指标前缀");
        assertRuleCode(HttpMethod.POST, "/api/monitoring/circuit-breaker/force-open",
                PermissionCodes.MONITORING_CONFIG_WRITE, "熔断强制开启");
        assertRuleCode(HttpMethod.POST, "/api/monitoring/circuit-breaker/force-close",
                PermissionCodes.MONITORING_CONFIG_WRITE, "熔断强制关闭");
        assertRuleCode(HttpMethod.POST, "/api/monitoring/cache/clear",
                PermissionCodes.MONITORING_CONFIG_WRITE, "缓存清理");
        assertRuleCode(HttpMethod.POST, "/api/monitoring/degradation/level",
                PermissionCodes.MONITORING_CONFIG_WRITE, "降级级别");
        assertRuleCode(HttpMethod.POST, "/api/monitoring/degradation/auto-mode",
                PermissionCodes.MONITORING_CONFIG_WRITE, "降级自动模式");
        assertRuleCode(HttpMethod.POST, "/api/monitoring/errors/reset",
                PermissionCodes.MONITORING_CONFIG_WRITE, "错误统计重置");
    }

    @Test
    @DisplayName("/api/monitoring 查询端点必须登记读规则（#116）")
    void monitoringReads_haveGetRule() {
        assertRuleCode(HttpMethod.GET, "/api/monitoring/config",
                PermissionCodes.MONITORING_METRICS_READ, "监控配置查询");
        assertRuleCode(HttpMethod.GET, "/api/monitoring/health",
                PermissionCodes.MONITORING_METRICS_READ, "监控健康");
        assertRuleCode(HttpMethod.GET, "/api/monitoring/overview",
                PermissionCodes.MONITORING_METRICS_READ, "监控概览");
        assertRuleCode(HttpMethod.GET, "/api/monitoring/circuit-breaker/stats",
                PermissionCodes.MONITORING_METRICS_READ, "熔断统计");
    }

    @Test
    @DisplayName("/api/config/type/** 必须登记实例配置读写规则（#116）")
    void serviceType_hasInstanceConfigRules() {
        assertRuleCode(HttpMethod.GET, "/api/config/type/services/chat",
                PermissionCodes.CONFIG_INSTANCES_READ, "服务类型查询");
        assertRuleCode(HttpMethod.POST, "/api/config/type/services/chat",
                PermissionCodes.CONFIG_INSTANCES_WRITE, "服务类型创建");
        assertRuleCode(HttpMethod.PUT, "/api/config/type/services/chat",
                PermissionCodes.CONFIG_INSTANCES_WRITE, "服务类型更新");
        assertRuleCode(HttpMethod.DELETE, "/api/config/type/services/chat",
                PermissionCodes.CONFIG_INSTANCES_WRITE, "服务类型删除");
        assertRuleCode(HttpMethod.POST, "/api/config/type/reset",
                PermissionCodes.CONFIG_INSTANCES_WRITE, "服务类型重置");
    }

    @Test
    @DisplayName("/api/security/audit/extended 写操作必须登记 security:audit:write（#116）")
    void extendedAuditWrites_haveWriteRule() {
        assertRuleCode(HttpMethod.POST, "/api/security/audit/extended/query",
                PermissionCodes.SECURITY_AUDIT_WRITE, "审计复杂查询");
        assertRuleCode(HttpMethod.POST, "/api/security/audit/extended/events/batch",
                PermissionCodes.SECURITY_AUDIT_WRITE, "审计批量写入");
        assertRuleCode(HttpMethod.POST, "/api/security/audit/extended/test-data/generate",
                PermissionCodes.SECURITY_AUDIT_WRITE, "审计测试数据生成");
    }

    @Test
    @DisplayName("既有 GET 规则回归：/api/monitoring/metrics/** 仍为 monitoring:metrics:read")
    void monitoringMetricsGet_unchanged() {
        assertRuleCode(HttpMethod.GET, "/api/monitoring/metrics/statistics",
                PermissionCodes.MONITORING_METRICS_READ, "指标监控查询");
    }

    @Test
    @DisplayName("既有 GET 规则回归：/api/security/audit/extended/** 仍为 security:audit:read")
    void extendedAuditGet_unchanged() {
        assertRuleCode(HttpMethod.GET, "/api/security/audit/extended/jwt-tokens",
                PermissionCodes.SECURITY_AUDIT_READ, "审计查询");
    }

    @Test
    @DisplayName("既有 GET 规则回归：/api/monitoring/slow-queries/** 与 quota/** 不被通配规则改写")
    void specificMonitoringGets_unchanged() {
        assertRuleCode(HttpMethod.GET, "/api/monitoring/slow-queries/stats",
                PermissionCodes.MONITORING_SLOWQUERY_READ, "慢查询统计");
        assertRuleCode(HttpMethod.GET, "/api/monitoring/quota/status",
                PermissionCodes.MONITORING_QUOTA_READ, "配额监控");
    }
}

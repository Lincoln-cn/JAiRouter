package org.unreal.modelrouter.auth.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * URL 权限规则注册表（v2.9.8 RBAC 数据驱动 URL 权限矩阵）
 *
 * <p>登记权限码对应的 URL 规则：将原 SecurityConfiguration 硬编码 URL 矩阵
 * （/api/security/jwt/accounts、/api/call-history、/api/config/call-history、
 * /api/config/tracing/security）+ 26 个同步返回 controller 的权限语义映射为
 * {method, pathPattern, permissionCode} 规则，供 {@link PermissionAuthorizationManager} 使用。
 *
 * <p>规则匹配为"首条命中"：路径模式越具体的规则应登记在越前面。
 * 未登记规则的 /api/** 端点回退为 authenticated（向后兼容，opt-in 迁移）。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Slf4j
@Component
public class PermissionRuleRegistry {

    /** 预编译后的规则条目 */
    private final List<RuleEntry> entries;

    public PermissionRuleRegistry() {
        PathPatternParser parser = new PathPatternParser();
        this.entries = buildDefaultRules().stream()
                .map(rule -> new RuleEntry(rule, parser.parse(rule.pathPattern())))
                .collect(Collectors.toList());
        log.info("PermissionRuleRegistry 初始化完成，共登记 {} 条 URL 权限规则", entries.size());
    }

    /**
     * 查找匹配指定方法与路径的权限规则
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 命中的规则（无匹配返回 Optional.empty）
     */
    public Optional<PermissionRule> findRule(final HttpMethod method, final String path) {
        for (RuleEntry entry : entries) {
            if (entry.rule().matches(method, entry.pattern(), path)) {
                return Optional.of(entry.rule());
            }
        }
        return Optional.empty();
    }

    /**
     * 返回全部登记规则（供管理 API / 调试使用）
     *
     * @return 规则列表
     */
    public List<PermissionRule> getRules() {
        return entries.stream().map(RuleEntry::rule).collect(Collectors.toList());
    }

    private static List<PermissionRule> buildDefaultRules() {
        return List.of(
                // ===== security 模块（原硬编码 ADMIN 规则迁移） =====
                // 账户管理（JwtAccountController，同步）→ system:accounts:manage（菜单「系统管理-账户管理」）
                PermissionRule.any("/api/security/jwt/accounts/**", PermissionCodes.SYSTEM_ACCOUNTS_MANAGE),
                PermissionRule.any("/api/security/blacklist/**", PermissionCodes.SECURITY_BLACKLIST_MANAGE),
                // v2.9.8 Phase 2: 权限管理 API（PermissionManagementController）→ system:permissions:manage
                PermissionRule.any("/api/security/permissions/**", PermissionCodes.SYSTEM_PERMISSIONS_MANAGE),

                // ===== 调用历史（原硬编码 ADMIN 规则迁移） =====
                PermissionRule.any("/api/call-history/**", PermissionCodes.CALLHISTORY_VIEW),
                PermissionRule.get("/api/config/call-history/**", PermissionCodes.CONFIG_CALLHISTORY_READ),
                PermissionRule.write("/api/config/call-history/**", PermissionCodes.CONFIG_CALLHISTORY_WRITE),

                // ===== 追踪安全配置（原硬编码 ADMIN 规则迁移） =====
                PermissionRule.any("/api/config/tracing/security/**", PermissionCodes.TRACING_CONFIG_MANAGE),

                // ===== 服务子路径规则（保留：前端 service.ts 仍在使用） =====
                PermissionRule.get("/api/services/*/circuitbreaker/**", PermissionCodes.CONFIG_CIRCUITBREAKER_READ),
                PermissionRule.write("/api/services/*/circuitbreaker/**", PermissionCodes.CONFIG_CIRCUITBREAKER_WRITE),
                PermissionRule.get("/api/services/*/ratelimit/**", PermissionCodes.RL_MONITORING_READ),
                PermissionRule.write("/api/services/*/ratelimit/**", PermissionCodes.RL_MONITORING_READ),

                // ===== 实例配置（/api/config/instance 仍活跃） =====
                PermissionRule.get("/api/config/instance/**", PermissionCodes.CONFIG_INSTANCES_READ),
                PermissionRule.write("/api/config/instance/**", PermissionCodes.CONFIG_INSTANCES_WRITE),

                // ===== 熔断器配置 =====
                PermissionRule.get("/api/config/circuit-breaker/**", PermissionCodes.CONFIG_CIRCUITBREAKER_READ),
                PermissionRule.write("/api/config/circuit-breaker/**", PermissionCodes.CONFIG_CIRCUITBREAKER_WRITE),

                // ===== 适配器配置 =====
                PermissionRule.get("/api/config/adapter/**", PermissionCodes.CONFIG_ADAPTERS_READ),
                PermissionRule.write("/api/config/adapter/**", PermissionCodes.CONFIG_ADAPTERS_WRITE),

                // ===== 规则 / 资源池配置 =====
                PermissionRule.get("/api/config/rules/**", PermissionCodes.CONFIG_RULES_READ),
                PermissionRule.write("/api/config/rules/**", PermissionCodes.CONFIG_RULES_WRITE),
                PermissionRule.get("/api/config/pools/**", PermissionCodes.CONFIG_POOLS_READ),
                PermissionRule.write("/api/config/pools/**", PermissionCodes.CONFIG_POOLS_WRITE),

                // ===== 监控（Token 用量 / 熔断监控 / 限流监控 / 路由监控 / 指标） =====
                PermissionRule.get("/api/token-usage/**", PermissionCodes.MONITORING_TOKENUSAGE_READ),
                PermissionRule.get("/api/v1/circuit-breaker-monitor/**", PermissionCodes.CB_MONITORING_READ),
                PermissionRule.write("/api/v1/circuit-breaker-monitor/**", PermissionCodes.CONFIG_CIRCUITBREAKER_WRITE),
                PermissionRule.any("/api/rate-limiter/**", PermissionCodes.RL_MONITORING_READ),
                PermissionRule.get("/api/v1/routing-monitor/**", PermissionCodes.MONITORING_ROUTING_READ),
                PermissionRule.write("/api/v1/routing-monitor/**", PermissionCodes.LB_CONFIG_WRITE),
                PermissionRule.get("/api/monitoring/metrics/**", PermissionCodes.MONITORING_METRICS_READ),

                // ===== 负载均衡 =====
                PermissionRule.get("/api/loadbalancer/**", PermissionCodes.LB_MONITORING_READ),
                PermissionRule.write("/api/loadbalancer/**", PermissionCodes.LB_CONFIG_WRITE),

                // ===== 响应缓存配置（v2.9.10） =====
                PermissionRule.get("/api/config/cache/response", PermissionCodes.CONFIG_CACHE_READ),
                PermissionRule.write("/api/config/cache/**", PermissionCodes.CONFIG_CACHE_WRITE),

                // ===== B2b 渐进补齐：API Key 管理（ApiKeyManagementController → 菜单「安全管理-API Key」） =====
                PermissionRule.any("/api/auth/api-keys/**", PermissionCodes.SECURITY_APIKEYS_MANAGE),

                // ===== B2b 渐进补齐：安全审计扩展（ExtendedSecurityAuditController → 菜单「安全管理-审计日志」） =====
                PermissionRule.get("/api/security/audit/extended/**", PermissionCodes.SECURITY_AUDIT_READ),

                // ===== B2b 渐进补齐：慢查询分析（SlowQueryAnalysisController → 菜单「监控-慢查询」） =====
                PermissionRule.get("/api/monitoring/slow-queries/**", PermissionCodes.MONITORING_SLOWQUERY_READ),

                // ===== B2b 渐进补齐：模型统计（ModelStatsController → 菜单「监控-模型统计」） =====
                PermissionRule.get("/api/models/stats/**", PermissionCodes.MONITORING_MODELSTATS_READ),

                // ===== B2b 渐进补齐：配置版本管理（ConfigurationVersionController → 菜单「配置-版本管理」） =====
                PermissionRule.get("/api/config/version/**", PermissionCodes.CONFIG_VERSIONS_READ),
                PermissionRule.write("/api/config/version/**", PermissionCodes.CONFIG_VERSIONS_WRITE),

                // ===== B2b 渐进补齐：状态持久化管理（StatePersistenceManagementController → 菜单「配置-状态持久化」） =====
                PermissionRule.get("/api/state-persistence/**", PermissionCodes.CONFIG_PERSISTENCE_READ),
                PermissionRule.write("/api/state-persistence/**", PermissionCodes.CONFIG_PERSISTENCE_WRITE),

                // ===== B2b 渐进补齐：追踪仪表盘（TracingController + TracingPerformanceController → 菜单「追踪-仪表盘/性能」） =====
                PermissionRule.get("/api/tracing/actuator/**", PermissionCodes.TRACING_DASHBOARD_READ),
                PermissionRule.write("/api/tracing/actuator/**", PermissionCodes.TRACING_CONFIG_MANAGE),
                PermissionRule.get("/api/tracing/performance/**", PermissionCodes.TRACING_DASHBOARD_READ),
                PermissionRule.write("/api/tracing/performance/**", PermissionCodes.TRACING_CONFIG_MANAGE),

                // ===== B2b 渐进补齐：追踪检索（TracingQueryController → 菜单「追踪-查询/搜索」） =====
                PermissionRule.get("/api/tracing/query/**", PermissionCodes.TRACING_SEARCH_READ),
                PermissionRule.write("/api/tracing/query/**", PermissionCodes.TRACING_CONFIG_MANAGE),

                // ===== B2b 渐进补齐：概览仪表盘（DashboardMetricsController → 菜单「概览」） =====
                PermissionRule.get("/api/dashboard/**", PermissionCodes.OVERVIEW_DASHBOARD_READ)
        );
    }

    /** 规则 + 预编译路径模式 */
    private record RuleEntry(PermissionRule rule, PathPattern pattern) {
    }
}

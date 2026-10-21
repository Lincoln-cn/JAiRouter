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
 * 未登记规则的 /api/** 端点按 {@link RbacUnmatchedPolicy} 姿态判定
 * （Phase 3 默认 {@code DENY_ALL}，豁免清单见 {@link RbacExemptEndpoints}）。
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
                // 账户管理（JwtAccountController，同步）→ 读走 system:accounts:read、写走 system:accounts:manage
                // （#567：此前 GET 也要求管理码，等于「看一眼账户列表」必须拿到写权限；现在按方法语义拆开）
                PermissionRule.get("/api/security/jwt/accounts/**", PermissionCodes.SYSTEM_ACCOUNTS_READ),
                PermissionRule.write("/api/security/jwt/accounts/**", PermissionCodes.SYSTEM_ACCOUNTS_MANAGE),
                // 黑名单（SecurityBlacklistController）→ GET（list/stats/{id}/check）读、POST/DELETE 写
                PermissionRule.get("/api/security/blacklist/**", PermissionCodes.SECURITY_BLACKLIST_READ),
                PermissionRule.write("/api/security/blacklist/**", PermissionCodes.SECURITY_BLACKLIST_MANAGE),
                // v2.9.8 Phase 2: 权限管理 API（PermissionManagementController）→ 读 :read、写（PUT 角色权限）:manage
                PermissionRule.get("/api/security/permissions/**", PermissionCodes.SYSTEM_PERMISSIONS_READ),
                PermissionRule.write("/api/security/permissions/**", PermissionCodes.SYSTEM_PERMISSIONS_MANAGE),

                // ===== 调用历史（原硬编码 ADMIN 规则迁移） =====
                // issue #514：这里原来是 any(...)（空方法集 = 所有方法），于是「查看」权限码
                // 同时放行了 POST /record（写入一条调用记录）与 DELETE /cleanup（删除历史）。
                // 现在只给 GET 一条规则：写/删**不匹配任何规则**，由 RbacUnmatchedPolicy 裁决
                // （默认 DENY_ALL ⇒ 仅 ROLE_ADMIN 直通）。这样既不新增权限码、也不动角色模板，
                // 权限语义就与「view」这个名字一致了。
                PermissionRule.get("/api/call-history/**", PermissionCodes.CALLHISTORY_VIEW),
                PermissionRule.get("/api/config/call-history/**", PermissionCodes.CONFIG_CALLHISTORY_READ),
                PermissionRule.write("/api/config/call-history/**", PermissionCodes.CONFIG_CALLHISTORY_WRITE),

                // ===== 追踪安全配置（原硬编码 ADMIN 规则迁移） =====
                // #567：GET（敏感字段清单 / 访问历史 / 概览）收敛到 tracing:config:read；
                // POST/DELETE（改敏感字段、清访问缓存、轮换密钥、清理加密数据）仍要 tracing:config:manage。
                PermissionRule.get("/api/config/tracing/security/**", PermissionCodes.TRACING_CONFIG_READ),
                PermissionRule.write("/api/config/tracing/security/**", PermissionCodes.TRACING_CONFIG_MANAGE),

                // ===== 服务子路径规则（保留：前端 service.ts 仍在使用） =====
                PermissionRule.get("/api/services/*/circuitbreaker/**", PermissionCodes.CONFIG_CIRCUITBREAKER_READ),
                PermissionRule.write("/api/services/*/circuitbreaker/**", PermissionCodes.CONFIG_CIRCUITBREAKER_WRITE),
                PermissionRule.get("/api/services/*/ratelimit/**", PermissionCodes.RL_MONITORING_READ),
                PermissionRule.write("/api/services/*/ratelimit/**", PermissionCodes.RL_MONITORING_READ),

                // ===== 实例配置（/api/config/instance 仍活跃） =====
                PermissionRule.get("/api/config/instance/**", PermissionCodes.CONFIG_INSTANCES_READ),
                PermissionRule.write("/api/config/instance/**", PermissionCodes.CONFIG_INSTANCES_WRITE),

                // ===== 服务类型管理（ServiceTypeController，#116）→ 与实例配置同码 =====
                // issue #468 复核过"要不要改成 config:services:*"，结论是**保持现状**，理由两条：
                // 1) 这个前缀是**共享读面**：概览页（api/dashboard.ts）与服务管理页（api/service.ts）
                //    都读 GET /api/config/type/**，绑到任一页的 :read 都会让另一页的角色配置变玄学；
                // 2) 内置角色下改与不改**没有差别**（ADMIN/OPERATOR/VIEWER 两类码都有或都没有），
                //    只有自定义角色会被静默改权 —— 那属于独立的权限设计变更，不该随一条 UX 修复顺带做。
                // 前端页内门控按"端点实际要求的码"挂（ServiceManagement 的写按钮挂 config:instances:write），
                // 因此按钮显隐与后端裁决始终一致。
                PermissionRule.get("/api/config/type/**", PermissionCodes.CONFIG_INSTANCES_READ),
                PermissionRule.write("/api/config/type/**", PermissionCodes.CONFIG_INSTANCES_WRITE),

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
                // issue #454：这一前缀此前只有 GET 规则，于是 10 个写端点（注册/更新/删除/清理/校验）
                // 只能靠 RbacUnmatchedPolicy 的默认 DENY_ALL（仅 ROLE_ADMIN）兜住 —— 不是显式设计。
                // 现在补显式写规则，让"谁能注册指标"成为一条可授的权限，而不是「默认拒绝」的副产品。
                PermissionRule.write("/api/monitoring/metrics/**", PermissionCodes.MONITORING_METRICS_WRITE),

                // ===== 负载均衡 =====
                // #567：/config/**（全局配置 + 按服务类型的策略配置）属于「配置读取」，用 lb:config:read；
                // /status、/strategies、/stats 仍是监控读（lb:monitoring:read）。
                // 具体路径必须先于下面那条宽泛 GET 登记（首条命中优先）。
                PermissionRule.get("/api/loadbalancer/config/**", PermissionCodes.LB_CONFIG_READ),
                PermissionRule.get("/api/loadbalancer/**", PermissionCodes.LB_MONITORING_READ),
                PermissionRule.write("/api/loadbalancer/**", PermissionCodes.LB_CONFIG_WRITE),

                // ===== 响应缓存配置（v2.9.10） =====
                PermissionRule.get("/api/config/cache/response", PermissionCodes.CONFIG_CACHE_READ),
                PermissionRule.write("/api/config/cache/**", PermissionCodes.CONFIG_CACHE_WRITE),

                // ===== B2b 渐进补齐：API Key 管理（ApiKeyManagementController → 菜单「安全管理-API Key」） =====
                // #567：GET（列表/详情/导出/配额）走 security:apikeys:read，写与轮换仍走 :manage。
                // 注意：该 controller 每个方法另有 @PreAuthorize("hasRole('ADMIN')")，所以 URL 规则放宽到
                // read 码并不会真的让非 ADMIN 读到 —— 方法级注解是第二道闸，属既有设计。
                PermissionRule.get("/api/auth/api-keys/**", PermissionCodes.SECURITY_APIKEYS_READ),
                PermissionRule.write("/api/auth/api-keys/**", PermissionCodes.SECURITY_APIKEYS_MANAGE),

                // ===== B2b 渐进补齐：安全审计扩展（ExtendedSecurityAuditController → 菜单「安全管理-审计日志」） =====
                PermissionRule.get("/api/security/audit/extended/**", PermissionCodes.SECURITY_AUDIT_READ),
                PermissionRule.write("/api/security/audit/extended/**", PermissionCodes.SECURITY_AUDIT_WRITE),

                // ===== B2b 渐进补齐：慢查询分析（SlowQueryAnalysisController → 菜单「监控-慢查询」） =====
                // issue #468：此前这一前缀只登记了 GET 规则，而页面上的「重置全部慢查询统计」是
                // DELETE /api/monitoring/slow-queries/stats ⇒ 命不中任何规则，只能靠
                // RbacUnmatchedPolicy 的默认 DENY_ALL 兜住（实测 403，但那是「未匹配」的副产品）。
                // 现在补显式写规则，让"谁能重置慢查询统计"成为一条可授的权限（与 #454 对
                // /api/monitoring/metrics/** 的处置同款）。
                PermissionRule.get("/api/monitoring/slow-queries/**", PermissionCodes.MONITORING_SLOWQUERY_READ),
                PermissionRule.write("/api/monitoring/slow-queries/**", PermissionCodes.MONITORING_SLOWQUERY_WRITE),

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
                PermissionRule.get("/api/dashboard/**", PermissionCodes.OVERVIEW_DASHBOARD_READ),

                // ===== 配额配置管理（QuotaConfigController） =====
                PermissionRule.get("/api/config/quota/**", PermissionCodes.CONFIG_QUOTA_READ),
                PermissionRule.write("/api/config/quota/**", PermissionCodes.CONFIG_QUOTA_WRITE),

                // ===== 配额监控（QuotaMonitoringController） =====
                PermissionRule.get("/api/monitoring/quota/**", PermissionCodes.MONITORING_QUOTA_READ),

                // ===== 监控配置/运维端点（MonitoringController，#116） =====
                // 注意：必须登记在 metrics/slow-queries/quota 的更具体 GET 规则之后（首条命中优先），
                // 以免改写已生效的 monitoring:metrics|slowquery|quota:read 绑定。
                PermissionRule.get("/api/monitoring/**", PermissionCodes.MONITORING_METRICS_READ),
                PermissionRule.write("/api/monitoring/**", PermissionCodes.MONITORING_CONFIG_WRITE),

                // ===== R2-P1-03：异常管理（原未登记，任意登录用户可访问） =====
                PermissionRule.get("/api/exceptions/**", PermissionCodes.MONITORING_EXCEPTIONS_READ),
                PermissionRule.write("/api/exceptions/**", PermissionCodes.MONITORING_EXCEPTIONS_WRITE),

                // ===== R2-P1-03：配置校验/来源指南 =====
                PermissionRule.get("/api/config/sources", PermissionCodes.CONFIG_VALIDATION_READ),
                PermissionRule.get("/api/config/environment-variables", PermissionCodes.CONFIG_VALIDATION_READ),
                PermissionRule.get("/api/config/validation-rules", PermissionCodes.CONFIG_VALIDATION_READ),

                // ===== PII / 脱敏配置管理（SanitizationConfigController） =====
                // #567：GET（当前配置 / 规则清单）走 security:sanitization:read；
                // PUT 配置、POST /test 仍走 :manage。
                PermissionRule.get("/api/config/sanitization/**", PermissionCodes.SECURITY_SANITIZATION_READ),
                PermissionRule.write("/api/config/sanitization/**", PermissionCodes.SECURITY_SANITIZATION_MANAGE),

                // ===== #128 Phase 2: JWT 令牌运维端点（方法级 @PreAuthorize 均为 hasRole('ADMIN')） =====
                // 登记规则使覆盖自检不再报缺口，且与 ApiKeyManagementController 同风格（URL 规则 + 方法级双保险）。
                // 注意：GET /api/auth/jwt/tokens（自助列表）与 POST /api/auth/jwt/revoke（自助撤销）不在此列，
                // 它们在 RbacExemptEndpoints 豁免清单中（方法级 @PreAuthorize 约束属主）。
                // 具体路径必须登记在任何更宽泛模式之前（首条命中优先）。
                // #567：这三个 GET 原先挂的是 manage 码（「用写码读」的错配），现改挂对应 :read；
                // 写端点（cleanup / revoke/batch）仍要 security:jwttokens:manage。
                PermissionRule.get("/api/auth/jwt/blacklist/stats", PermissionCodes.SECURITY_BLACKLIST_READ),
                PermissionRule.get("/api/auth/jwt/cleanup/stats", PermissionCodes.SECURITY_JWTTOKENS_READ),
                PermissionRule.write("/api/auth/jwt/cleanup", PermissionCodes.SECURITY_JWTTOKENS_MANAGE),
                PermissionRule.write("/api/auth/jwt/revoke/batch", PermissionCodes.SECURITY_JWTTOKENS_MANAGE),
                PermissionRule.get("/api/auth/jwt/tokens/{tokenId}", PermissionCodes.SECURITY_JWTTOKENS_READ)
        );
    }

    /** 规则 + 预编译路径模式 */
    private record RuleEntry(PermissionRule rule, PathPattern pattern) {
    }
}

package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #128 Phase 3 前置安全检查：控制台（前端）实际调用的每个 GET 路径必须
 * 「已登记规则 或 显式豁免」。否则翻转 GET fail-closed 后普通用户会在控制台看到 403。
 *
 * <p>清单来源：{@code frontend/src/api/**} 与 {@code frontend/src/views/**} 中
 * 全部 {@code request.get(...)} 调用（baseURL=/api）。模板段以样例值代入。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@DisplayName("控制台 GET 面覆盖检查（#128 Phase 3 前置）")
class ConsoleGetSurfaceCoverageTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    /**
     * 前端 request.get 实际调用的 GET 路径（已代入样例值，前缀 /api）。
     * 来源文件：api/{account,adapter,apiKey,auditLog,callHistory,dashboard,exception,
     * instance,jwtToken,limitMetrics,models,permission,pools,quota,responseCache,rules,
     * sanitization,service,slowQuery,tokenUsage,tracing,version}.ts
     * + views/{circuit-breaker,config,load-balancer,rate-limiter}/**.vue
     */
    private static List<String> consoleGetPaths() {
        return List.of(
                // 账户 / API Key
                "/api/security/jwt/accounts",
                "/api/auth/api-keys",
                "/api/auth/api-keys/k1",
                "/api/auth/api-keys/k1/quota",
                "/api/auth/api-keys/export",
                "/api/auth/api-keys/quota/alerts",
                "/api/auth/api-keys/quota/overview",
                // JWT 令牌
                "/api/auth/jwt/blacklist/stats",
                "/api/auth/jwt/tokens",
                "/api/auth/jwt/tokens/t1",
                // 调用历史
                "/api/call-history",
                "/api/call-history/count",
                "/api/call-history/dashboard",
                "/api/call-history/recent",
                "/api/call-history/slow",
                "/api/call-history/statistics",
                "/api/call-history/trace/tx-1",
                "/api/config/call-history",
                // 实例 / 服务类型 / 模型
                "/api/config/instance/chat",
                "/api/config/instance/chat/i1",
                "/api/config/instance/chat/i1/circuit-breaker",
                "/api/config/instance/chat/i1/rate-limit",
                "/api/config/instance/circuit-breaker/states",
                "/api/config/type",
                "/api/config/type/services",
                "/api/config/type/services/batch",
                "/api/config/type/services/chat",
                "/api/config/type/chat/models",
                "/api/models/stats",
                // 适配器
                "/api/config/adapter",
                "/api/config/adapter/list",
                "/api/config/adapter/parents",
                "/api/config/adapter/ad1",
                "/api/config/adapter/templates",
                "/api/config/adapter/templates/t1",
                // 规则 / 池 / 缓存 / 配额 / 脱敏
                "/api/config/rules/list",
                "/api/config/rules/r1",
                "/api/config/rules/stats",
                "/api/config/rules/templates",
                "/api/config/pools/list",
                "/api/config/pools/p1",
                "/api/config/cache/response",
                "/api/config/quota",
                "/api/config/sanitization",
                "/api/config/sanitization/rules",
                // 配置版本
                "/api/config/version/info",
                "/api/config/version/v1",
                // 熔断器配置/历史
                "/api/config/circuit-breaker/global-config",
                "/api/config/circuit-breaker/history",
                "/api/config/circuit-breaker/history/stats",
                // 监控 / 概览 / 仪表盘
                "/api/dashboard/metrics",
                "/api/monitoring/overview",
                "/api/monitoring/quota/status",
                "/api/monitoring/quota/usage",
                "/api/monitoring/slow-queries/stats",
                "/api/monitoring/slow-queries/stats/op1",
                "/api/monitoring/slow-queries/count",
                "/api/monitoring/slow-queries/hotspots",
                "/api/monitoring/slow-queries/trends",
                "/api/monitoring/slow-queries/alerts/stats",
                "/api/monitoring/slow-queries/alerts/status",
                // 限流 / 负载均衡 / 状态持久化
                "/api/rate-limiter/metrics",
                "/api/rate-limiter/summary",
                "/api/rate-limiter/prometheus-info",
                "/api/loadbalancer/status",
                "/api/loadbalancer/status/chat",
                "/api/loadbalancer/config/global",
                "/api/loadbalancer/config/chat",
                "/api/loadbalancer/strategies",
                "/api/loadbalancer/stats",
                "/api/loadbalancer/history",
                "/api/loadbalancer/export/json",
                "/api/state-persistence/status",
                "/api/state-persistence/tiers",
                "/api/state-persistence/details",
                // 追踪
                "/api/tracing/actuator/config",
                "/api/tracing/actuator/health",
                "/api/tracing/actuator/stats",
                "/api/tracing/actuator/status",
                "/api/tracing/query/performance/errors",
                "/api/tracing/query/performance/latency",
                "/api/tracing/query/performance/throughput",
                "/api/tracing/query/recent",
                "/api/tracing/query/search",
                "/api/tracing/query/services",
                "/api/tracing/query/statistics",
                "/api/tracing/query/trace/t1",
                // 异常
                "/api/exceptions",
                "/api/exceptions/e1",
                "/api/exceptions/dashboard",
                "/api/exceptions/recent",
                "/api/exceptions/recent/TYPE",
                "/api/exceptions/statistics",
                // Token 用量
                "/api/token-usage/recent",
                "/api/token-usage/statistics",
                // 权限管理
                "/api/security/permissions",
                "/api/security/permissions/roles",
                // 审计
                "/api/security/audit/extended/api-keys",
                "/api/security/audit/extended/jwt-tokens",
                "/api/security/audit/extended/security-events",
                "/api/security/audit/extended/reports/security",
                "/api/security/audit/extended/statistics/extended",
                "/api/security/audit/extended/users/u1/events",
                "/api/security/audit/extended/ip-addresses/1.2.3.4/events",
                // /api/v1 监控面（控制台唯一 GET 消费者）
                "/api/v1/routing-monitor/status",
                "/api/v1/routing-monitor/stats",
                "/api/v1/circuit-breaker-monitor/status",
                "/api/v1/circuit-breaker-monitor/history",
                "/api/v1/circuit-breaker-monitor/export/json"
        );
    }

    @Test
    @DisplayName("控制台每个 GET 路径都已登记规则或显式豁免（翻转后不会误伤 403）")
    void everyConsoleGetIsMatchedOrExempt() {
        List<String> paths = consoleGetPaths();
        assertFalse(paths.isEmpty(), "控制台 GET 清单不应为空");

        java.util.List<String> gaps = new java.util.ArrayList<>();
        for (String path : paths) {
            boolean matched = registry.findRule(HttpMethod.GET, path).isPresent();
            boolean exempt = RbacExemptEndpoints.isExempt(HttpMethod.GET, path);
            if (!matched && !exempt) {
                gaps.add(path);
            }
        }
        assertTrue(gaps.isEmpty(),
                "控制台 GET 既未登记也未豁免，翻转后普通用户将 403: " + gaps);
    }

    @Test
    @DisplayName("控制台 GET 清单本身非空且包含 /api/v1 监控面（防止清单被掏空）")
    void consoleGetListIsMeaningful() {
        List<String> paths = consoleGetPaths();
        assertTrue(paths.size() > 80, "控制台 GET 路径数量应覆盖全部 API 模块，实际=" + paths.size());
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("/api/v1/routing-monitor/")),
                "必须包含前端 limitMetrics.ts 调用的 /api/v1/routing-monitor GET");
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("/api/v1/circuit-breaker-monitor/")),
                "必须包含前端 circuit-breaker Monitoring.vue 调用的 /api/v1/circuit-breaker-monitor GET");
    }
}

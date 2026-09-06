package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B2b 渐进补齐：新登记 URL 权限规则的授权/拒绝断言
 *
 * <p>覆盖 PermissionRuleRegistry 中 B2b 批次新增的 18 条规则（12 个路径模式），
 * 每条规则至少包含：
 * <ul>
 *   <li>已认证 + 缺少权限码 → 拒绝（403 语义）</li>
 *   <li>已认证 + 携带正确权限码 → 放行</li>
 * </ul>
 * 部分规则另含未认证 → 拒绝（401 语义）断言。
 *
 * @author JAiRouter Team
 * @since 3.0.2
 */
@DisplayName("B2b PermissionClosure - 新登记 URL 权限规则断言")
class PermissionClosureTest {

    private PermissionAuthorizationManager manager;

    @BeforeEach
    void setUp() {
        manager = new PermissionAuthorizationManager(new PermissionRuleRegistry());
    }

    // ==================== API Key 管理 ====================

    @Nested
    @DisplayName("API Key 管理 /api/auth/api-keys/** → security:apikeys:manage")
    class ApiKeyManagementTests {

        @Test
        @DisplayName("GET /api/auth/api-keys - 无 security:apikeys:manage → 拒绝(403)")
        void listApiKeysWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/auth/api-keys");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/auth/api-keys - 携带 security:apikeys:manage → 放行")
        void listApiKeysWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/auth/api-keys");
            JwtAuthentication auth = authenticated(
                    "admin", List.of("ADMIN"), List.of(PermissionCodes.SECURITY_APIKEYS_MANAGE));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST /api/auth/api-keys - 无权限 → 拒绝")
        void createApiKeyWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.POST, "/api/auth/api-keys");
            JwtAuthentication auth = authenticated("operator", List.of("OPERATOR"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("DELETE /api/auth/api-keys/123 - 无 token → 拒绝(401)")
        void deleteApiKeyUnauthenticatedDenied() {
            AuthorizationContext ctx = context(HttpMethod.DELETE, "/api/auth/api-keys/123");

            StepVerifier.create(manager.check(Mono.empty(), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 安全审计扩展 ====================

    @Nested
    @DisplayName("安全审计扩展 /api/security/audit/extended/** → security:audit:read")
    class ExtendedAuditTests {

        @Test
        @DisplayName("GET /api/security/audit/extended/jwt-tokens - 无 security:audit:read → 拒绝(403)")
        void auditQueryWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/security/audit/extended/jwt-tokens");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/security/audit/extended/jwt-tokens - 携带 security:audit:read → 放行")
        void auditQueryWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/security/audit/extended/jwt-tokens");
            JwtAuthentication auth = authenticated(
                    "operator", List.of("OPERATOR"), List.of(PermissionCodes.SECURITY_AUDIT_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/security/audit/extended/api-keys - 无 token → 拒绝(401)")
        void auditQueryUnauthenticatedDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/security/audit/extended/api-keys");

            StepVerifier.create(manager.check(Mono.empty(), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 慢查询分析 ====================

    @Nested
    @DisplayName("慢查询分析 /api/monitoring/slow-queries/** → monitoring:slowquery:read")
    class SlowQueryTests {

        @Test
        @DisplayName("GET /api/monitoring/slow-queries/stats - 无 monitoring:slowquery:read → 拒绝(403)")
        void slowQueryWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/monitoring/slow-queries/stats");
            JwtAuthentication auth = authenticated("viewer", List.of("VIEWER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/monitoring/slow-queries/stats - 携带 monitoring:slowquery:read → 放行")
        void slowQueryWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/monitoring/slow-queries/stats");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.MONITORING_SLOWQUERY_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 模型统计 ====================

    @Nested
    @DisplayName("模型统计 /api/models/stats/** → monitoring:modelstats:read")
    class ModelStatsTests {

        @Test
        @DisplayName("GET /api/models/stats - 无 monitoring:modelstats:read → 拒绝(403)")
        void modelStatsWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/models/stats");
            JwtAuthentication auth = authenticated("viewer", List.of("VIEWER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/models/stats - 携带 monitoring:modelstats:read → 放行")
        void modelStatsWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/models/stats");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.MONITORING_MODELSTATS_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 配置版本管理 ====================

    @Nested
    @DisplayName("配置版本管理 /api/config/version/** → config:versions:read/write")
    class ConfigVersionTests {

        @Test
        @DisplayName("GET /api/config/version/history - 无 config:versions:read → 拒绝(403)")
        void versionReadWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/config/version/history");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/config/version/history - 携带 config:versions:read → 放行")
        void versionReadWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/config/version/history");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_VERSIONS_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST /api/config/version/rollback - 仅 config:versions:read 无 write → 拒绝")
        void versionWriteWithOnlyReadDenied() {
            AuthorizationContext ctx = context(HttpMethod.POST, "/api/config/version/rollback/1");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_VERSIONS_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST /api/config/version/rollback - 携带 config:versions:write → 放行")
        void versionWriteWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.POST, "/api/config/version/rollback/1");
            JwtAuthentication auth = authenticated(
                    "operator", List.of("OPERATOR"), List.of(PermissionCodes.CONFIG_VERSIONS_WRITE));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 状态持久化管理 ====================

    @Nested
    @DisplayName("状态持久化管理 /api/state-persistence/** → config:persistence:read/write")
    class StatePersistenceTests {

        @Test
        @DisplayName("GET /api/state-persistence/status - 无 config:persistence:read → 拒绝(403)")
        void persistenceReadWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/state-persistence/status");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/state-persistence/status - 携带 config:persistence:read → 放行")
        void persistenceReadWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/state-persistence/status");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_PERSISTENCE_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST /api/state-persistence/sync - 仅 read 无 write → 拒绝")
        void persistenceWriteWithOnlyReadDenied() {
            AuthorizationContext ctx = context(HttpMethod.POST, "/api/state-persistence/sync");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_PERSISTENCE_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST /api/state-persistence/sync - 携带 config:persistence:write → 放行")
        void persistenceWriteWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.POST, "/api/state-persistence/sync");
            JwtAuthentication auth = authenticated(
                    "operator", List.of("OPERATOR"), List.of(PermissionCodes.CONFIG_PERSISTENCE_WRITE));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 追踪仪表盘 ====================

    @Nested
    @DisplayName("追踪仪表盘 /api/tracing/actuator/** → tracing:dashboard:read / tracing:config:manage")
    class TracingDashboardTests {

        @Test
        @DisplayName("GET /api/tracing/actuator/status - 无 tracing:dashboard:read → 拒绝(403)")
        void tracingStatusWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/tracing/actuator/status");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/tracing/actuator/status - 携带 tracing:dashboard:read → 放行")
        void tracingStatusWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/tracing/actuator/status");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.TRACING_DASHBOARD_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("PUT /api/tracing/actuator/config - 仅 tracing:dashboard:read 无 config:manage → 拒绝")
        void tracingConfigWriteWithOnlyReadDenied() {
            AuthorizationContext ctx = context(HttpMethod.PUT, "/api/tracing/actuator/config");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.TRACING_DASHBOARD_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("PUT /api/tracing/actuator/config - ADMIN 放行（tracing:config:manage 超集）")
        void tracingConfigWriteAdminGranted() {
            AuthorizationContext ctx = context(HttpMethod.PUT, "/api/tracing/actuator/config");
            JwtAuthentication auth = authenticated("admin", List.of("ADMIN"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 追踪性能 ====================

    @Nested
    @DisplayName("追踪性能 /api/tracing/performance/** → tracing:dashboard:read / tracing:config:manage")
    class TracingPerformanceTests {

        @Test
        @DisplayName("GET /api/tracing/performance/stats - 无 tracing:dashboard:read → 拒绝(403)")
        void perfStatsWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/tracing/performance/stats");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/tracing/performance/stats - 携带 tracing:dashboard:read → 放行")
        void perfStatsWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/tracing/performance/stats");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.TRACING_DASHBOARD_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST /api/tracing/performance/optimize - 仅 read 无 config:manage → 拒绝")
        void perfOptimizeWithOnlyReadDenied() {
            AuthorizationContext ctx = context(HttpMethod.POST, "/api/tracing/performance/optimize");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.TRACING_DASHBOARD_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 追踪检索 ====================

    @Nested
    @DisplayName("追踪检索 /api/tracing/query/** → tracing:search:read / tracing:config:manage")
    class TracingSearchTests {

        @Test
        @DisplayName("GET /api/tracing/query/search - 无 tracing:search:read → 拒绝(403)")
        void tracingSearchWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/tracing/query/search");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/tracing/query/search - 携带 tracing:search:read → 放行")
        void tracingSearchWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/tracing/query/search");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.TRACING_SEARCH_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST /api/tracing/query/export - 仅 read 无 config:manage → 拒绝")
        void tracingExportWithOnlyReadDenied() {
            AuthorizationContext ctx = context(HttpMethod.POST, "/api/tracing/query/export");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.TRACING_SEARCH_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/tracing/query/trace/abc-123 - 携带 tracing:search:read → 放行")
        void traceDetailWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/tracing/query/trace/abc-123");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.TRACING_SEARCH_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 概览仪表盘 ====================

    @Nested
    @DisplayName("概览仪表盘 /api/dashboard/** → overview:dashboard:read")
    class DashboardOverviewTests {

        @Test
        @DisplayName("GET /api/dashboard/metrics - 无 overview:dashboard:read → 拒绝(403)")
        void dashboardWithoutPermissionDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/dashboard/metrics");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/dashboard/metrics - 携带 overview:dashboard:read → 放行")
        void dashboardWithPermissionGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/dashboard/metrics");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ));

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/dashboard/metrics - ADMIN 直通放行")
        void dashboardAdminBypassGranted() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/dashboard/metrics");
            JwtAuthentication auth = authenticated("admin", List.of("ADMIN"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/dashboard/metrics - 无 token → 拒绝(401)")
        void dashboardUnauthenticatedDenied() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/dashboard/metrics");

            StepVerifier.create(manager.check(Mono.empty(), ctx))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 回退行为不变验证 ====================

    @Nested
    @DisplayName("回退行为不变验证（未登记路径仍 authenticated）")
    class FallbackBehaviorTests {

        @Test
        @DisplayName("未登记路径 + 已认证 -> 仍放行（authenticated 回退不变）")
        void unregisteredPathStillAuthenticatedFallback() {
            AuthorizationContext ctx = context(HttpMethod.GET, "/api/some-unregistered-path");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), ctx))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 辅助方法 ====================

    /** 构造已认证（authenticated=true）的 JwtAuthentication */
    private static JwtAuthentication authenticated(final String subject,
                                                   final List<String> roles,
                                                   final List<String> permissions) {
        JwtAuthentication auth = new JwtAuthentication(subject, "token", roles, permissions);
        auth.setAuthenticated(true);
        return auth;
    }

    private static AuthorizationContext context(final HttpMethod method, final String path) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath requestPath = mock(RequestPath.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getMethod()).thenReturn(method);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn(path);
        return new AuthorizationContext(exchange);
    }
}

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
 * PermissionAuthorizationManager 单元测试（v2.9.8 RBAC）
 *
 * 覆盖：规则命中授权、权限码缺失拒绝、ADMIN 直通、无规则回退 authenticated、方法感知、未认证拒绝。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@DisplayName("PermissionAuthorizationManager 测试")
class PermissionAuthorizationManagerTest {

    private PermissionAuthorizationManager manager;

    @BeforeEach
    void setUp() {
        manager = new PermissionAuthorizationManager(new PermissionRuleRegistry());
    }

    @Nested
    @DisplayName("规则命中授权")
    class RuleMatchTests {

        @Test
        @DisplayName("命中规则且携带权限码 -> 放行")
        void ruleMatchedWithPermissionGranted() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/services");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_SERVICES_READ));

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("命中规则但缺少权限码 -> 拒绝(403)")
        void ruleMatchedWithoutPermissionDenied() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/services");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("命中规则但拥有其他权限码 -> 拒绝")
        void ruleMatchedWithWrongPermissionDenied() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/services");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_POOLS_READ));

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("ADMIN 角色直通（即使无权限码）")
        void adminRoleBypassesPermissionCheck() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/services");
            JwtAuthentication auth = authenticated("admin", List.of("ADMIN"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("写方法规则：POST 需 write 权限码，GET 规则不适用")
        void writeMethodRequiresWriteCode() {
            AuthorizationContext context = context(HttpMethod.POST, "/api/services");
            JwtAuthentication reader = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_SERVICES_READ));

            // 只有 read 权限码，POST /api/services 应被拒绝（需要 write 权限码）
            StepVerifier.create(manager.check(Mono.just(reader), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("未认证请求命中规则 -> 拒绝")
        void unauthenticatedOnRuleDenied() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/services");

            StepVerifier.create(manager.check(Mono.empty(), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("无规则回退")
    class NoRuleFallbackTests {

        @Test
        @DisplayName("未登记路径 + 已认证 -> 放行(authenticated 回退)")
        void noRuleAuthenticatedGranted() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/unregistered-endpoint");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("未登记路径 + 未认证 -> 拒绝(401)")
        void noRuleUnauthenticatedDenied() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/unregistered-endpoint");

            StepVerifier.create(manager.check(Mono.empty(), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("规则匹配语义")
    class RuleSemanticsTests {

        @Test
        @DisplayName("call-history 任意方法 -> callhistory:view")
        void callHistoryAnyMethodRule() {
            AuthorizationContext context = context(HttpMethod.DELETE, "/api/call-history/123");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CALLHISTORY_VIEW));

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("services 子路径规则优先：/api/services/chat/circuitbreaker 不套用 services 规则")
        void serviceSubPathRuleTakesPrecedence() {
            // 只有 config:services:read，无 config:circuitbreaker:read -> 拒绝
            AuthorizationContext context = context(HttpMethod.GET, "/api/services/chat/circuitbreaker");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_SERVICES_READ));

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("services 子路径规则优先：携带 circuitbreaker 权限码 -> 放行")
        void serviceSubPathRuleGrantsWithCircuitBreakerCode() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/services/chat/circuitbreaker");
            JwtAuthentication auth = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_CIRCUITBREAKER_READ));

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("账户管理规则：/api/security/jwt/accounts/** 需 system:accounts:manage")
        void jwtAccountsRuleRequiresSystemAccountsManage() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/security/jwt/accounts");
            JwtAuthentication holder = authenticated(
                    "operator", List.of("OPERATOR"), List.of(PermissionCodes.SYSTEM_ACCOUNTS_MANAGE));
            JwtAuthentication tokenManager = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.SECURITY_JWTTOKENS_MANAGE));

            // 携带 system:accounts:manage -> 放行
            StepVerifier.create(manager.check(Mono.just(holder), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
            // 仅携带 security:jwttokens:manage -> 拒绝（账户管理不归 JWT 令牌管理）
            StepVerifier.create(manager.check(Mono.just(tokenManager), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("权限管理 URL 规则（v2.9.8 Phase 2）")
    class PermissionManagementRuleTests {

        @Test
        @DisplayName("ADMIN 访问 /api/security/permissions -> 放行(200)")
        void adminGrantedOnPermissionManagement() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/security/permissions");
            JwtAuthentication auth = authenticated("admin", List.of("ADMIN"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("携带 system:permissions:manage -> 放行")
        void permissionCodeHolderGranted() {
            AuthorizationContext context = context(HttpMethod.PUT, "/api/security/permissions/roles/USER");
            JwtAuthentication auth = authenticated(
                    "operator", List.of("USER"), List.of(PermissionCodes.SYSTEM_PERMISSIONS_MANAGE));

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("无 token -> 拒绝(401)")
        void unauthenticatedDeniedOnPermissionManagement() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/security/permissions");

            StepVerifier.create(manager.check(Mono.empty(), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("已认证但无 system:permissions:manage -> 拒绝(403)")
        void authenticatedWithoutPermissionDenied() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/security/permissions");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/auth/permissions 未登记规则 -> 已认证放行(200)")
        void currentUserPermissionsRequiresAuthenticatedOnly() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/auth/permissions");
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(manager.check(Mono.just(auth), context))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("GET /api/auth/permissions 无 token -> 拒绝(401)")
        void currentUserPermissionsUnauthenticatedDenied() {
            AuthorizationContext context = context(HttpMethod.GET, "/api/auth/permissions");

            StepVerifier.create(manager.check(Mono.empty(), context))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    /** 构造已认证（authenticated=true）的 JwtAuthentication（与生产代码 DefaultJwtTokenValidator 一致） */
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

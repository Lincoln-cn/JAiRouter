package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #128 Phase 3：未命中 GET 也 fail-closed（默认 DENY_ALL），含三种姿态回归。
 *
 * <p>姿态矩阵（{@code jairouter.security.rbac.unmatched-policy}）：
 * <ul>
 *   <li>{@code DENY_ALL}（Phase 3 默认）：未命中且未豁免 → 仅 ADMIN 直通，其余 DENY（含 GET）</li>
 *   <li>{@code DENY_WRITES}（Phase 2）：写方法 fail-closed，GET 仍回退 authenticated</li>
 *   <li>{@code AUTHENTICATED}（遗留）：全部回退 authenticated（fail-open）</li>
 * </ul>
 *
 * <p>ROLE_ADMIN 短路不受姿态影响；豁免清单与规则语义不变。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@DisplayName("PermissionAuthorizationManager 未命中 GET fail-closed（#128 Phase 3）")
class PermissionUnmatchedPolicyTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    /** Phase 3 默认姿态 */
    private PermissionAuthorizationManager denyAll() {
        return new PermissionAuthorizationManager(registry, RbacUnmatchedPolicy.DENY_ALL);
    }

    /** Phase 2 姿态 */
    private PermissionAuthorizationManager denyWrites() {
        return new PermissionAuthorizationManager(registry, RbacUnmatchedPolicy.DENY_WRITES);
    }

    /** 遗留 fail-open */
    private PermissionAuthorizationManager authenticatedFallback() {
        return new PermissionAuthorizationManager(registry, RbacUnmatchedPolicy.AUTHENTICATED);
    }

    // ==================== 1：未命中 GET → DENY（Phase 3 核心） ====================

    @Nested
    @DisplayName("DENY_ALL：未命中 GET fail-closed")
    class UnmatchedGetDenyTests {

        @Test
        @DisplayName("未登记 GET + 已认证非管理员 → DENY（Phase 3 核心行为，改前必须失败）")
        void unmatchedGetDeniedForAuthenticatedNonAdmin() {
            String unmatched = "/api/unmatched-get-fail-closed-probe";
            assertFalse(registry.findRule(HttpMethod.GET, unmatched).isPresent(), "探针必须未登记");
            assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, unmatched), "探针必须未豁免");

            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(denyAll().check(Mono.just(user), context(HttpMethod.GET, unmatched)))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("未登记 GET + 已认证 + 携带任意无关权限码（非 ADMIN）→ 仍 DENY")
        void unmatchedGetDeniedEvenWithUnrelatedPermissionCode() {
            String unmatched = "/api/unmatched-get-with-code-probe";
            JwtAuthentication holder = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_SERVICES_READ));
            StepVerifier.create(denyAll().check(Mono.just(holder), context(HttpMethod.GET, unmatched)))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("未登记 GET + 未认证 → DENY（401 语义不变）")
        void unmatchedGetStillDeniedForAnonymous() {
            StepVerifier.create(denyAll().check(Mono.empty(),
                            context(HttpMethod.GET, "/api/unmatched-get-anon-probe")))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 2：未命中 POST 仍 DENY（Phase 2 保留） ====================

    @Nested
    @DisplayName("DENY_ALL：写方法 fail-closed（Phase 2 行为保留）")
    class UnmatchedWriteDenyTests {

        @Test
        @DisplayName("未登记 POST + 已认证非管理员 → DENY（Phase 2 保留）")
        void unmatchedPostStillDenied() {
            String unmatched = "/api/unmatched-write-p3-probe";
            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(denyAll().check(Mono.just(user), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("未登记 PUT/DELETE/PATCH + 已认证非管理员 → DENY")
        void unmatchedWriteVariantsStillDenied() {
            String unmatched = "/api/unmatched-write-variant-p3-probe";
            for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)) {
                JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
                StepVerifier.create(denyAll().check(Mono.just(user), context(method, unmatched)))
                        .expectNextMatches(decision -> !decision.isGranted())
                        .verifyComplete();
            }
        }
    }

    // ==================== 3：豁免清单逐条可达 ====================

    @Nested
    @DisplayName("豁免清单逐条可达（由真实清单旋转生成，防漂移）")
    class ExemptionReachabilityTests {

        @Test
        @DisplayName("DENY_ALL 下豁免清单每个条目的每个方法 + 已认证非管理员 → ALLOW")
        void everyExemptionEntryStaysReachableUnderDenyAll() {
            PermissionAuthorizationManager manager = denyAll();
            List<RbacExemptEndpoints.Exemption> entries = RbacExemptEndpoints.exemptions();
            assertFalse(entries.isEmpty(), "豁免清单不应为空");

            for (RbacExemptEndpoints.Exemption entry : entries) {
                String samplePath = RbacEndpointCoverageChecker.toSamplePath(entry.pathPattern());
                for (HttpMethod method : entry.methods()) {
                    JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
                    String label = "豁免条目必须可达: " + method + " " + entry.pathPattern();
                    StepVerifier.create(manager.check(Mono.just(user), context(method, samplePath)))
                            .expectNextMatches(decision -> {
                                assertTrue(decision.isGranted(), label);
                                return true;
                            })
                            .verifyComplete();
                }
            }
        }
    }

    // ==================== 4：规则端点语义不变 ====================

    @Nested
    @DisplayName("规则命中语义不变")
    class RuleSemanticsTests {

        @Test
        @DisplayName("已登记 GET + 正确权限码 → ALLOW；无码 → DENY")
        void ruledGetRequiresCode() {
            String path = "/api/config/instance/chat";
            JwtAuthentication holder = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_INSTANCES_READ));
            JwtAuthentication bare = authenticated("user", List.of("USER"), List.of());

            StepVerifier.create(denyAll().check(Mono.just(holder), context(HttpMethod.GET, path)))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
            StepVerifier.create(denyAll().check(Mono.just(bare), context(HttpMethod.GET, path)))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 5：ROLE_ADMIN 短路 ====================

    @Nested
    @DisplayName("ROLE_ADMIN 短路（所有姿态）")
    class AdminShortCircuitTests {

        @Test
        @DisplayName("未登记 GET/POST + ROLE_ADMIN → ALLOW（DENY_ALL 不影响管理员）")
        void adminBypassesDenyAll() {
            JwtAuthentication admin = authenticated("admin", List.of("ADMIN"), List.of());
            StepVerifier.create(denyAll().check(Mono.just(admin),
                            context(HttpMethod.GET, "/api/unmatched-admin-get-probe")))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
            StepVerifier.create(denyAll().check(Mono.just(admin),
                            context(HttpMethod.POST, "/api/unmatched-admin-write-probe")))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("已登记规则路径 + ROLE_ADMIN 无权限码 → ALLOW（回归）")
        void adminBypassesRegisteredRule() {
            JwtAuthentication admin = authenticated("admin", List.of("ADMIN"), List.of());
            StepVerifier.create(denyAll().check(Mono.just(admin),
                            context(HttpMethod.GET, "/api/config/instance/chat")))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 6：AUTHENTICATED 遗留姿态 ====================

    @Nested
    @DisplayName("AUTHENTICATED 遗留姿态恢复 fail-open（GET + 写）")
    class AuthenticatedLegacyTests {

        @Test
        @DisplayName("AUTHENTICATED：未登记 GET + 已认证 → ALLOW（遗留 fail-open）")
        void legacyModeRestoresFailOpenForGet() {
            String unmatched = "/api/unmatched-get-legacy-p3-probe";
            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(authenticatedFallback().check(Mono.just(user), context(HttpMethod.GET, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUTHENTICATED：未登记 POST + 已认证 → ALLOW（遗留 fail-open）")
        void legacyModeRestoresFailOpenForWrites() {
            String unmatched = "/api/unmatched-write-legacy-p3-probe";
            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(authenticatedFallback().check(Mono.just(user), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUTHENTICATED：未认证仍 DENY（401 语义不变）")
        void legacyModeStillDeniesAnonymous() {
            StepVerifier.create(authenticatedFallback().check(Mono.empty(),
                            context(HttpMethod.GET, "/api/unmatched-get-legacy-p3-probe")))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 7：DENY_WRITES 姿态（Phase 2） ====================

    @Nested
    @DisplayName("DENY_WRITES 姿态（Phase 2 回归）")
    class DenyWritesPostureTests {

        @Test
        @DisplayName("DENY_WRITES：未登记 GET + 已认证 → ALLOW（GET 仍 fail-open）")
        void denyWritesKeepsGetFailOpen() {
            String unmatched = "/api/unmatched-get-denywrites-probe";
            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(denyWrites().check(Mono.just(user), context(HttpMethod.GET, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("DENY_WRITES：未登记 POST + 已认证非管理员 → DENY（写 fail-closed）")
        void denyWritesKeepsWriteFailClosed() {
            String unmatched = "/api/unmatched-write-denywrites-probe";
            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(denyWrites().check(Mono.just(user), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 8：API-Key 主体访问 /api/v1/** ====================

    @Nested
    @DisplayName("API-Key 服务主体访问代理面（DENY_ALL 下回归）")
    class ApiKeyPrincipalTests {

        @Test
        @DisplayName("API-Key 主体（ROLE_CHAT，无权限码）POST 全部 7 个服务端点 → ALLOW")
        void apiKeyStylePrincipalAllowedOnAllServiceEndpoints() {
            ApiKeyAuthentication apiKeyAuth = new ApiKeyAuthentication("key-1", "secret", List.of("chat"));
            apiKeyAuth.setAuthenticated(true);
            List<String> servicePaths = List.of(
                    "/api/v1/chat/completions", "/api/v1/embeddings", "/api/v1/rerank",
                    "/api/v1/audio/speech", "/api/v1/audio/transcriptions",
                    "/api/v1/images/generations", "/api/v1/images/edits");
            for (String path : servicePaths) {
                String label = "API-Key 主体必须可访问服务代理面: " + path;
                StepVerifier.create(denyAll().check(
                                Mono.just(apiKeyAuth), context(HttpMethod.POST, path)))
                        .expectNextMatches(decision -> {
                            assertTrue(decision.isGranted(), label);
                            return true;
                        })
                        .verifyComplete();
            }
        }

        @Test
        @DisplayName("API-Key 主体 GET /api/v1/** 服务面路径：无服务代理 GET 端点，监控 GET 走规则（与未命中姿态无关）")
        void apiKeyStylePrincipalOnApiV1GetSurface() {
            ApiKeyAuthentication apiKeyAuth = new ApiKeyAuthentication("key-1", "secret", List.of("chat"));
            apiKeyAuth.setAuthenticated(true);

            // 服务代理面（UniversalController）当前仅 POST；此处用 GET 探测同路径，
            // DENY_ALL 下未命中且未豁免 → DENY（fail-closed，符合「无服务代理 GET」现实）。
            // 若未来服务面新增 GET，必须加入 RbacExemptEndpoints，否则 API-Key 会被误伤。
            StepVerifier.create(denyAll().check(
                            Mono.just(apiKeyAuth), context(HttpMethod.GET, "/api/v1/chat/completions")))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();

            // 控制台监控 GET（/api/v1/routing-monitor/**）已有规则，语义与未命中姿态无关：
            // 无权限码 → DENY（既有规则行为，Phase 3 不改变）。
            StepVerifier.create(denyAll().check(
                            Mono.just(apiKeyAuth), context(HttpMethod.GET, "/api/v1/routing-monitor/status")))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 辅助 ====================

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

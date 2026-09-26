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
 * #128 Phase 2：写方法 fail-closed 策略测试（DENY_WRITES 姿态）。
 *
 * <p>Phase 2 仅对写方法（POST/PUT/DELETE/PATCH）实施 fail-closed：
 * 未命中规则且未豁免 → DENY。GET 在 DENY_WRITES 姿态下保持 fail-open
 * （Phase 3 的 DENY_ALL 见 {@link PermissionUnmatchedPolicyTest}）。
 * 布尔构造器 {@code true}→DENY_WRITES，{@code false}→AUTHENTICATED。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@DisplayName("PermissionAuthorizationManager 写方法 fail-closed（#128 Phase 2）")
class PermissionWriteFailClosedTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    /** Phase 2 默认策略（写 fail-closed） */
    private PermissionAuthorizationManager phase2Manager() {
        return new PermissionAuthorizationManager(registry, true);
    }

    /** 遗留模式（逃生阀开启，写 fail-open） */
    private PermissionAuthorizationManager legacyManager() {
        return new PermissionAuthorizationManager(registry, false);
    }

    // ==================== 测试 1：未命中写路径 → DENY ====================

    @Nested
    @DisplayName("未命中写路径 fail-closed")
    class UnmatchedWriteDenyTests {

        @Test
        @DisplayName("未登记 POST + 已认证非管理员 → DENY（Phase 2 核心行为）")
        void unmatchedWriteDeniedForAuthenticatedNonAdmin() {
            String unmatched = "/api/unmatched-write-fail-closed-probe";
            assertFalse(registry.findRule(HttpMethod.POST, unmatched).isPresent(),
                    "探针路径必须未登记");
            assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.POST, unmatched),
                    "探针路径必须未豁免");

            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(phase2Manager().check(Mono.just(user), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }

        @Test
        @DisplayName("未登记 PUT/DELETE/PATCH + 已认证非管理员 → DENY")
        void unmatchedWriteVariantsDenied() {
            String unmatched = "/api/unmatched-write-variant-probe";
            for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)) {
                assertFalse(registry.findRule(method, unmatched).isPresent());
                assertFalse(RbacExemptEndpoints.isExempt(method, unmatched));
                JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
                StepVerifier.create(phase2Manager().check(Mono.just(user), context(method, unmatched)))
                        .expectNextMatches(decision -> !decision.isGranted())
                        .verifyComplete();
            }
        }

        @Test
        @DisplayName("未登记写路径 + 携带任意权限码（非 ADMIN）→ 仍 DENY（无规则时权限码无效）")
        void unmatchedWriteDeniedEvenWithUnrelatedPermissionCode() {
            String unmatched = "/api/unmatched-write-with-code-probe";
            JwtAuthentication holder = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_SERVICES_READ));
            StepVerifier.create(phase2Manager().check(Mono.just(holder), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 测试 2：未命中 GET 仍 fail-open ====================

    @Nested
    @DisplayName("未命中 GET 保持 fail-open（Phase 3 不得混入）")
    class UnmatchedGetFailOpenTests {

        @Test
        @DisplayName("未登记 GET + 已认证 → ALLOW（fail-open 完好）")
        void unmatchedGetStillAllowedForAuthenticatedUser() {
            String unmatched = "/api/unmatched-get-fail-open-probe";
            assertFalse(registry.findRule(HttpMethod.GET, unmatched).isPresent());
            assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, unmatched));

            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(phase2Manager().check(Mono.just(user), context(HttpMethod.GET, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("未登记 GET + 未认证 → 仍 DENY（401 语义不变）")
        void unmatchedGetStillDeniedForAnonymous() {
            StepVerifier.create(phase2Manager().check(Mono.empty(),
                            context(HttpMethod.GET, "/api/unmatched-get-fail-open-probe")))
                    .expectNextMatches(decision -> !decision.isGranted())
                    .verifyComplete();
        }
    }

    // ==================== 测试 3：豁免清单逐条可达 ====================

    @Nested
    @DisplayName("豁免清单逐条可达（由真实清单生成，防漂移）")
    class ExemptionReachabilityTests {

        @Test
        @DisplayName("豁免清单每个条目的每个方法 + 已认证非管理员 → ALLOW")
        void everyExemptionEntryStaysReachable() {
            PermissionAuthorizationManager manager = phase2Manager();
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

    // ==================== 测试 5：ROLE_ADMIN 短路 ====================

    @Nested
    @DisplayName("ROLE_ADMIN 短路")
    class AdminShortCircuitTests {

        @Test
        @DisplayName("未登记写路径 + ROLE_ADMIN → ALLOW（管理员不受 fail-closed 影响）")
        void adminBypassesWriteFailClosed() {
            String unmatched = "/api/unmatched-write-admin-probe";
            JwtAuthentication admin = authenticated("admin", List.of("ADMIN"), List.of());
            StepVerifier.create(phase2Manager().check(Mono.just(admin), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("已登记规则路径 + ROLE_ADMIN 无权限码 → ALLOW（回归）")
        void adminBypassesRegisteredRule() {
            JwtAuthentication admin = authenticated("admin", List.of("ADMIN"), List.of());
            StepVerifier.create(phase2Manager().check(Mono.just(admin),
                            context(HttpMethod.GET, "/api/config/instance/chat")))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }
    }

    // ==================== 测试 6/7：逃生阀 + 遗留模式回归 ====================

    @Nested
    @DisplayName("逃生阀与遗留模式")
    class EscapeHatchTests {

        @Test
        @DisplayName("逃生阀开启（legacy）：未登记写路径 + 已认证 → ALLOW（旧行为）")
        void legacyModeRestoresFailOpenForWrites() {
            String unmatched = "/api/unmatched-write-legacy-probe";
            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            StepVerifier.create(legacyManager().check(Mono.just(user), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("遗留模式与 Phase 1 行为字节级一致（未命中路径/规则路径/未认证）")
        void legacyModeByteIdenticalToPrePhase2() {
            PermissionAuthorizationManager legacy = legacyManager();
            String unmatched = "/api/unmatched-legacy-regression-probe";
            JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
            JwtAuthentication holder = authenticated(
                    "user", List.of("USER"), List.of(PermissionCodes.CONFIG_INSTANCES_READ));

            // 未命中 GET：allow
            StepVerifier.create(legacy.check(Mono.just(user), context(HttpMethod.GET, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted).verifyComplete();
            // 未命中 POST：allow（旧 fail-open）
            StepVerifier.create(legacy.check(Mono.just(user), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(AuthorizationDecision::isGranted).verifyComplete();
            // 未命中 + 未认证：deny
            StepVerifier.create(legacy.check(Mono.empty(), context(HttpMethod.POST, unmatched)))
                    .expectNextMatches(decision -> !decision.isGranted()).verifyComplete();
            // 规则命中 + 有码：allow
            StepVerifier.create(legacy.check(Mono.just(holder),
                            context(HttpMethod.GET, "/api/config/instance/chat")))
                    .expectNextMatches(AuthorizationDecision::isGranted).verifyComplete();
            // 规则命中 + 无码：deny
            StepVerifier.create(legacy.check(Mono.just(user),
                            context(HttpMethod.GET, "/api/config/instance/chat")))
                    .expectNextMatches(decision -> !decision.isGranted()).verifyComplete();
        }

        @Test
        @DisplayName("遗留模式下豁免端点行为不变（仍可达）")
        void legacyModeExemptionsStillReachable() {
            PermissionAuthorizationManager legacy = legacyManager();
            for (RbacExemptEndpoints.Exemption entry : RbacExemptEndpoints.exemptions()) {
                String samplePath = RbacEndpointCoverageChecker.toSamplePath(entry.pathPattern());
                for (HttpMethod method : entry.methods()) {
                    JwtAuthentication user = authenticated("user", List.of("USER"), List.of());
                    StepVerifier.create(legacy.check(Mono.just(user), context(method, samplePath)))
                            .expectNextMatches(AuthorizationDecision::isGranted)
                            .verifyComplete();
                }
            }
        }
    }

    // ==================== 测试 8：API-Key 主体访问 /api/v1/** ====================

    @Nested
    @DisplayName("API-Key 服务主体访问代理面（事实 3 守卫）")
    class ApiKeyPrincipalTests {

        @Test
        @DisplayName("API-Key 主体（ROLE_CHAT，无权限码）POST /api/v1/chat/completions → ALLOW")
        void apiKeyStylePrincipalAllowedOnServiceProxy() {
            ApiKeyAuthentication apiKeyAuth = new ApiKeyAuthentication("key-1", "secret", List.of("chat"));
            apiKeyAuth.setAuthenticated(true);
            assertTrue(apiKeyAuth.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_CHAT".equals(a.getAuthority())),
                    "API-Key 主体必须携带 ROLE_CHAT");

            StepVerifier.create(phase2Manager().check(
                            Mono.just(apiKeyAuth), context(HttpMethod.POST, "/api/v1/chat/completions")))
                    .expectNextMatches(AuthorizationDecision::isGranted)
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-Key 主体 POST 全部 7 个服务端点 → ALLOW（豁免清单覆盖）")
        void apiKeyStylePrincipalAllowedOnAllServiceEndpoints() {
            ApiKeyAuthentication apiKeyAuth = new ApiKeyAuthentication("key-1", "secret", List.of("chat"));
            apiKeyAuth.setAuthenticated(true);
            List<String> servicePaths = List.of(
                    "/api/v1/chat/completions", "/api/v1/embeddings", "/api/v1/rerank",
                    "/api/v1/audio/speech", "/api/v1/audio/transcriptions",
                    "/api/v1/images/generations", "/api/v1/images/edits");
            for (String path : servicePaths) {
                String label = "API-Key 主体必须可访问服务代理面: " + path;
                StepVerifier.create(phase2Manager().check(
                                Mono.just(apiKeyAuth), context(HttpMethod.POST, path)))
                        .expectNextMatches(decision -> {
                            assertTrue(decision.isGranted(), label);
                            return true;
                        })
                        .verifyComplete();
            }
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

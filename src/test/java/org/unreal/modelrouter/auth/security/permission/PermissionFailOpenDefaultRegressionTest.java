package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #128 Phase 1 回归门禁：默认授权行为必须保持不变。
 *
 * <p>Phase 1 只增加「端点覆盖自检」可观测性，<b>不得</b>把无规则路径的回退从
 * authenticated（fail-open）改为 DENY。后续 fail-closed 切换必须走独立阶段、
 * 完整端点审计 + 前端回归，并显式开启配置。
 *
 * @author JAiRouter Team
 * @since 3.0.3
 */
@DisplayName("RBAC fail-open 默认行为回归（#128 Phase 1 不得改变授权）")
class PermissionFailOpenDefaultRegressionTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    @DisplayName("无任何 strict-mode 配置时，未登记路径 + 已认证 → ALLOW（fail-open 完好）")
    @Test
    void unmatchedPathStillAllowedForAuthenticatedUserByDefault() {
        PermissionAuthorizationManager manager = new PermissionAuthorizationManager(registry);
        String unmatched = "/api/unmatched-endpoint-regression-probe";
        assertTrue(registry.findRule(HttpMethod.GET, unmatched).isEmpty(),
                "回归探针路径必须是未登记路径");

        AuthorizationContext context = context(HttpMethod.GET, unmatched);
        JwtAuthentication auth = authenticated("user", List.of("USER"), List.of());

        StepVerifier.create(manager.check(Mono.just(auth), context))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

    @DisplayName("未登记路径 + 未认证 → 仍为 DENY（401 语义，原行为保留）")
    @Test
    void unmatchedPathStillDeniedForAnonymous() {
        PermissionAuthorizationManager manager = new PermissionAuthorizationManager(registry);
        AuthorizationContext context = context(HttpMethod.GET, "/api/unmatched-endpoint-regression-probe");

        StepVerifier.create(manager.check(Mono.empty(), context))
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();
    }

    @DisplayName("已登记规则路径的授权语义不受自检影响（拒绝/放行保持）")
    @Test
    void matchedRuleSemanticsUnchanged() {
        PermissionAuthorizationManager manager = new PermissionAuthorizationManager(registry);
        JwtAuthentication withoutCode = authenticated("user", List.of("USER"), List.of());
        JwtAuthentication withCode = authenticated(
                "user", List.of("USER"), List.of(PermissionCodes.CONFIG_INSTANCES_READ));

        StepVerifier.create(manager.check(
                        Mono.just(withoutCode),
                        context(HttpMethod.GET, "/api/config/instance/chat")))
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();

        StepVerifier.create(manager.check(
                        Mono.just(withCode),
                        context(HttpMethod.GET, "/api/config/instance/chat")))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

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

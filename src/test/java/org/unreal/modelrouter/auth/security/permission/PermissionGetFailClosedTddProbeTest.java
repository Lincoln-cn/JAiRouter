package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TDD 探针（#128 Phase 3）：默认姿态下未命中 GET 必须 DENY。
 * 本探针只用既有构造器，用于在行为翻转前验证「必须失败」。
 */
@DisplayName("TDD 探针：默认姿态未命中 GET → DENY（改前必须失败）")
class PermissionGetFailClosedTddProbeTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    @Test
    @DisplayName("默认构造器：未登记 GET + 已认证非管理员 → DENY")
    void unmatchedGetDeniedByDefault() {
        String unmatched = "/api/tdd-unmatched-get-probe";
        assertFalse(registry.findRule(HttpMethod.GET, unmatched).isPresent());
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, unmatched));

        PermissionAuthorizationManager manager = new PermissionAuthorizationManager(registry);
        JwtAuthentication user = new JwtAuthentication("user", "token", List.of("USER"), List.of());
        user.setAuthenticated(true);

        StepVerifier.create(manager.check(Mono.just(user), context(HttpMethod.GET, unmatched)))
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();
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

package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 控制台页面访问统计（B4）的权限面测试。
 *
 * <p>三条容易被改坏、且改坏了不会报错的边界：
 * <ol>
 *   <li>上报端点必须只要求「已认证」——它排在 {@code /api/monitoring/**} 兜底之前，
 *       否则普通用户/观察者上报自己看过哪一页会 403，热度表只剩管理员的数据</li>
 *   <li>汇总端点必须要求 {@code security:audit:read}——它是别人的访问数据</li>
 *   <li>兜底规则不能被这两条挤坏：其它 {@code /api/monitoring/**} 端点仍按原样判码</li>
 * </ol>
 *
 * @author JAiRouter Team
 * @since 3.3.8
 */
@DisplayName("控制台访问统计权限面")
class UiAccessPermissionTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    private final PermissionAuthorizationManager manager =
            new PermissionAuthorizationManager(registry);

    @Test
    @DisplayName("上报端点：命中「仅认证」规则，不绑定任何权限码")
    void reportEndpointRequiresOnlyAuthentication() {
        Optional<PermissionRule> rule = registry.findRule(HttpMethod.POST, "/api/monitoring/ui-access");

        assertTrue(rule.isPresent(), "POST /api/monitoring/ui-access 必须登记规则");
        assertTrue(rule.get().permissionCodes().isEmpty(),
                "上报端点不应要求权限码，实际: " + rule.get().permissionCodes());
        assertEquals("/api/monitoring/ui-access", rule.get().pathPattern(),
                "必须命中精确规则，而不是 /api/monitoring/** 兜底");
    }

    @Test
    @DisplayName("汇总端点：要求 security:audit:read")
    void summaryEndpointRequiresAuditRead() {
        Optional<PermissionRule> rule = registry.findRule(HttpMethod.GET, "/api/monitoring/ui-access/summary");

        assertTrue(rule.isPresent(), "GET /api/monitoring/ui-access/summary 必须登记规则");
        assertEquals(java.util.Set.of(PermissionCodes.SECURITY_AUDIT_READ), rule.get().permissionCodes());
    }

    @Test
    @DisplayName("兜底未被挤坏：其它监控端点仍按 metrics:read / config:write 判码")
    void monitoringFallbackStillIntact() {
        assertEquals(java.util.Set.of(PermissionCodes.MONITORING_METRICS_READ),
                registry.findRule(HttpMethod.GET, "/api/monitoring/dashboard").orElseThrow().permissionCodes());
        assertEquals(java.util.Set.of(PermissionCodes.MONITORING_CONFIG_WRITE),
                registry.findRule(HttpMethod.POST, "/api/monitoring/emergency/degrade").orElseThrow().permissionCodes());
    }

    @Test
    @DisplayName("授权：已认证但无任何权限码的用户可以上报（否则热度表只剩管理员数据）")
    void authenticatedUserWithoutAnyCodeCanReport() {
        JwtAuthentication viewer = authenticated("viewer1", List.of("VIEWER"), List.of());

        StepVerifier.create(manager.authorize(Mono.just(viewer),
                        context(HttpMethod.POST, "/api/monitoring/ui-access")))
                .expectNextMatches(AuthorizationResult::isGranted)
                .verifyComplete();
    }

    @Test
    @DisplayName("授权：匿名请求不能上报")
    void anonymousCannotReport() {
        StepVerifier.create(manager.authorize(Mono.empty(),
                        context(HttpMethod.POST, "/api/monitoring/ui-access")))
                .expectNextMatches(result -> !result.isGranted())
                .verifyComplete();
    }

    @Test
    @DisplayName("授权：无 security:audit:read 的用户能上报但不能读汇总")
    void userCanReportButNotReadSummary() {
        JwtAuthentication operator = authenticated("ops1", List.of("OPERATOR"), List.of());

        StepVerifier.create(manager.authorize(Mono.just(operator),
                        context(HttpMethod.GET, "/api/monitoring/ui-access/summary")))
                .expectNextMatches(result -> !result.isGranted())
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

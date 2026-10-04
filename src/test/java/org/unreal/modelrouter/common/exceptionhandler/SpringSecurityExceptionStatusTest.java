package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Spring Security 异常应映射为 401/403，而不是落进「系统异常 → 500」（issue #205）。
 *
 * <p>背景（真实集群实测）：`deploy/k8s/base/deployment.yaml` 的探针打 `/actuator/health/liveness`，
 * 当时该路径未被匿名放行，Spring Security 抛 `AuthenticationCredentialsNotFoundException`；
 * 本仓的全局异常处理器只认自己的 `AuthenticationException` / `SecurityAuthenticationException`，
 * 该类异常不匹配任何分支 ⇒ 落到最后的 500 兜底 ⇒ kubelet 事件里出现
 * `Startup probe failed: HTTP probe failed with statuscode: 500`，把排查引向"应用故障"。
 *
 * <p>本测试直接断言状态码：未认证 → 401，授权不足 → 403。
 */
@DisplayName("Spring Security 异常的响应状态（issue #205）")
class SpringSecurityExceptionStatusTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ReactiveGlobalExceptionHandler handler =
            new ReactiveGlobalExceptionHandler(new ErrorTracker(mock(StructuredLogger.class)));

    @Test
    @DisplayName("未认证（AuthenticationCredentialsNotFoundException）应回 401，而不是 500")
    void unauthenticatedIsUnauthorized() {
        final MockServerWebExchange exchange = exchangeOf("/api/config/adapter/list");

        handler.handle(exchange, new AuthenticationCredentialsNotFoundException("Not Authenticated"))
                .block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(),
                "未认证是客户端问题，应回 401；此前落到 500 兜底，会让探针/客户端误以为是服务端故障");
    }

    @Test
    @DisplayName("授权不足（AccessDeniedException）应回 403")
    void accessDeniedIsForbidden() {
        final MockServerWebExchange exchange = exchangeOf("/api/config/adapter/list");

        handler.handle(exchange, new AccessDeniedException("Access Denied")).block(TIMEOUT);

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode(),
                "授权不足是客户端权限问题，应回 403");
    }

    private static MockServerWebExchange exchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}

package org.unreal.modelrouter.common.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.util.SecurityUtils;
import org.unreal.modelrouter.config.event.AuditLogEvent;
import org.unreal.modelrouter.monitor.tracing.helper.ConsoleOperationNames;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制台写操作的审计过滤器
 *
 * <p>{@code AuditLogEvent} / {@code ConfigAuditEventListener} / {@code config_audit_log}
 * 这条链路一直是完整的，但**没有任何地方发布事件**，所以表里没有记录、管理操作页的
 * 「操作人」永远是未知。本过滤器在控制台写方法上补上发布点（#1160）。</p>
 *
 * <p>顺序排在 {@link RequestIdWebFilter} 之后（{@code HIGHEST_PRECEDENCE + 3}），
 * 这样发布事件时 MDC 里已经有 traceId，可以把操作记录与链路关联起来。</p>
 *
 * <p>刻意不记录请求体：控制台写接口的 body 可能含凭据、密钥等敏感字段，
 * 先只记录「谁、对什么、做了什么、结果如何」。改动前后值留给后续的
 * {@code ConfigChangeAuditService} 接入。</p>
 *
 * @since 3.8.8
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsoleAuditWebFilter implements WebFilter, Ordered {

    /** 控制台经 Vite/网关代理进入时的前缀 */
    private static final String ADMIN_API_PREFIX = "/admin/api/";

    /** 直连控制台时的前缀 */
    private static final String API_PREFIX = "/api/";

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final ServerHttpRequest request = exchange.getRequest();
        final String path = request.getPath().value();
        final HttpMethod method = request.getMethod();

        // 具体哪些路径算「管理面写操作」由 ConsoleOperationNames 的登记表决定；
        // 未登记的路径（含 /api/auth/** 这类登录接口）不会产生审计记录。
        if (method == null || !isConsoleApi(path) || !ConsoleOperationNames.isWriteMethod(method.name())) {
            return chain.filter(exchange);
        }

        // 操作名规则按去掉 /admin 前缀的路径登记（与 ServiceNameResolver 保持一致），
        // 这里必须先 strip 再匹配，否则所有写操作都命中不了。
        final String operation = ConsoleOperationNames.resolve(
            method.name(), ConsoleOperationNames.stripAdminPrefix(path));
        if (operation == null) {
            return chain.filter(exchange);
        }

        final String traceId = MDC.get(RequestIdWebFilter.MDC_TRACE_ID_KEY);
        final long startedAt = System.currentTimeMillis();

        return SecurityUtils.getCurrentUserIdReactive()
            .defaultIfEmpty("unknown")
            .onErrorReturn("unknown")
            .flatMap(userId -> chain.filter(exchange)
                .doFinally(signal -> publish(
                    operation, path, method.name(), startedAt, traceId,
                    resolveOperator(userId, exchange), exchange)));
    }

    /**
     * 操作人
     *
     * <p>认证过滤器排在审计过滤器之后，它的 {@code SecurityContext} 写在 Reactor Context 的内层，
     * 这里读不到；认证成功时它会把用户名放进 {@link SecurityUtils#USER_ID_ATTRIBUTE}。</p>
     */
    private static String resolveOperator(final String reactiveUserId, final ServerWebExchange exchange) {
        final Object attribute = exchange.getAttributes().get(SecurityUtils.USER_ID_ATTRIBUTE);
        if (attribute != null && !String.valueOf(attribute).isEmpty()) {
            return String.valueOf(attribute);
        }
        return reactiveUserId != null ? reactiveUserId : "unknown";
    }

    private static boolean isConsoleApi(final String path) {
        return path.startsWith(ADMIN_API_PREFIX) || path.startsWith(API_PREFIX);
    }

    private void publish(final String operation,
                         final String path,
                         final String method,
                         final long startedAt,
                         final String traceId,
                         final String userId,
                         final ServerWebExchange exchange) {
        try {
            final HttpStatusCode status = exchange.getResponse().getStatusCode();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("method", method);
            data.put("path", path);
            data.put("status", status != null ? status.value() : 0);
            data.put("duration_ms", System.currentTimeMillis() - startedAt);
            if (traceId != null && !traceId.isEmpty()) {
                data.put("traceId", traceId);
            }

            eventPublisher.publishEvent(AuditLogEvent.serviceConfig(
                operation, ConsoleOperationNames.stripAdminPrefix(path), data, userId));
        } catch (RuntimeException e) {
            // 审计失败不能影响业务响应
            log.debug("发布控制台审计事件失败: operation={}, path={}", operation, path, e);
        }
    }
}

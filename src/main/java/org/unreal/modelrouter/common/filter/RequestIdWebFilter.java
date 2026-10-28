package org.unreal.modelrouter.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.util.SecurityUtils;
import org.unreal.modelrouter.monitor.tracing.TracingConstants;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import reactor.core.publisher.Mono;

/**
 * 在响应侧统一回写请求 ID 的 WebFilter（issue #724）.
 *
 * <p>#724 之前，请求 ID 只有一个「自己生成时才写响应头」的工具方法
 * （{@link SecurityUtils#extractRequestId(ServerWebExchange)}），而它全仓零调用 ⇒ 客户端
 * 永远拿不到 {@code X-Request-Id}，报障时无法把一次调用与日志/调用历史对上。本过滤器把这条
 * 路径接线，并把它提到「所有请求都写」的语义。</p>
 *
 * <p>请求 ID 的来源优先级：入站 {@code X-Request-Id} 请求头 → 当前 tracing 的 traceId →
 * 新生成的 UUID。选定值既写入响应头，也放进 {@link #REQUEST_ID_ATTRIBUTE}，供审计、调用历史、
 * 错误体等下游复用（不要在别处再生成第二个 ID）。头名统一取自
 * {@link TracingConstants.Headers#X_REQUEST_ID}；{@code HttpHeaders} 大小写不敏感，因此历史上写的
 * {@code X-Request-ID} 与之等价。</p>
 *
 * <p>写头发生在 {@code chain.filter(...)} **之前**，所以即使下游提前终止（鉴权 401、参数 400、
 * 上游 5xx），客户端都能拿到该头 —— 排障最需要它的正是这些场景。</p>
 *
 * <p>顺序 = {@link Ordered#HIGHEST_PRECEDENCE} + 2：排在 {@code CachedBodyWebFilter}
 * （{@code +0}）、可写请求头装饰器（{@code +1}）之后；{@code TracingWebFilter} 是
 * {@code HIGHEST_PRECEDENCE}，先于本过滤器执行并已把 tracing 上下文放进交换属性，因此 traceId
 * 可复用；tracing 关闭或建根 span 失败时退化为生成 UUID。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.4
 */
@Slf4j
public class RequestIdWebFilter implements WebFilter, Ordered {

    /**
     * 请求 ID 在交换属性中的键.
     *
     * <p>下游（审计事件、调用历史、错误体渲染）需要「这次请求的 ID」时读这里，保证与响应头是同一个值。</p>
     */
    public static final String REQUEST_ID_ATTRIBUTE =
            RequestIdWebFilter.class.getName() + ".requestId";

    /**
     * 顺序：紧跟在 {@code CachedBodyWebFilter}（+0）与可写请求头装饰器（+1）之后.
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    /**
     * 解析并回写请求 ID，然后放行.
     *
     * @param exchange 当前交换
     * @param chain    过滤器链
     * @return 下游完成信号
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final String requestId = SecurityUtils.extractRequestId(exchange, resolveTraceId(exchange));
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        log.debug("请求 ID 已回写响应头 {}: {}", TracingConstants.Headers.X_REQUEST_ID, requestId);
        return chain.filter(exchange);
    }

    /**
     * 从 tracing 上下文取 traceId，作为请求 ID 的备选来源.
     *
     * <p>先看 {@link TracingConstants.ContextKeys#TRACING_CONTEXT}（tracing 启用时的主来源），
     * 再看 {@link TracingConstants.ContextKeys#TRACE_ID}（部分链路只透传这个属性），都没有则返回
     * {@code null} 交给调用方生成。</p>
     *
     * @param exchange 当前交换
     * @return traceId；未启用 tracing 或上下文尚未建立时为 {@code null}
     */
    static String resolveTraceId(final ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        final TracingContext context =
                exchange.getAttribute(TracingConstants.ContextKeys.TRACING_CONTEXT);
        if (context != null && context.getTraceId() != null && !context.getTraceId().isEmpty()) {
            return context.getTraceId();
        }
        final Object traceId = exchange.getAttribute(TracingConstants.ContextKeys.TRACE_ID);
        if (traceId instanceof String id && !id.isEmpty()) {
            return id;
        }
        return null;
    }
}

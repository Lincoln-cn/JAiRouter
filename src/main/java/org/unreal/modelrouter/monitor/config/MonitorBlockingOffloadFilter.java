package org.unreal.modelrouter.monitor.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 把「同步实现的监控管理端点」整体卸载到 {@link Schedulers#boundedElastic()}（#854）。
 *
 * <p>背景：监控包里有 7 个控制器（{@code /api/call-history}、{@code /api/config/call-history}、
 * {@code /api/exceptions}、{@code /api/monitoring/quota}、{@code /api/config/tracing/security}、
 * {@code /api/monitoring/metrics}、{@code /actuator/metrics}）仍然写成同步方法并直接调用 JPA 服务。
 * 本仓是纯 WebFlux 栈（只依赖 {@code spring-boot-starter-webflux}），同步方法体会在 Netty
 * 事件循环线程上跑完阻塞 JDBC —— 一个慢的管理查询就把业务请求一起堵住。</p>
 *
 * <p>逐个把 60+ 个端点改成 {@code Mono.fromCallable(...)} 改动面大且容易漏，因此在过滤器链
 * 最内层按<b>路径前缀</b>统一卸载：命中白名单的请求整条链路（含同步处理器方法）改在
 * {@code boundedElastic} 上执行，线程模型与 {@code Mono.fromCallable(...).subscribeOn(...)} 等价，
 * 而响应体、状态码、异常映射路径全部不变。</p>
 *
 * <p>为什么放在过滤器而不是控制器：事件循环线程的判据是「谁执行处理器方法」，而这由请求进入
 * 服务器后的订阅线程决定；在链尾切换订阅线程即可覆盖该前缀下的所有同步处理器。</p>
 *
 * <p>顺序取 {@link Ordered#LOWEST_PRECEDENCE}（最内层）：认证、权限、脱敏这些过滤器仍在外层，
 * 因此未授权的请求不会先被卸载到弹性线程池；只有已经通过鉴权、真正要执行处理器时才切换线程。</p>
 *
 * @author JAiRouter Team
 */
@Component
public class MonitorBlockingOffloadFilter implements WebFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitorBlockingOffloadFilter.class);

    /**
     * 同步实现的监控管理端点前缀（与各控制器上的 {@code @RequestMapping} 一致）。
     *
     * <p>新增同步监控控制器时必须在这里登记，否则它又会跑回事件循环线程上 ——
     * {@code MonitorBlockingOffloadFilterTest} 会核对这份清单与控制器实况。</p>
     */
    public static final List<String> OFFLOAD_PREFIXES = List.of(
            "/api/call-history",
            "/api/config/call-history",
            "/api/exceptions",
            "/api/monitoring/quota",
            "/api/config/tracing/security",
            "/api/monitoring/metrics",
            "/actuator/metrics");

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!matchesOffloadPrefix(path)) {
            return chain.filter(exchange);
        }
        LOGGER.debug("监控管理端点 {} 在 boundedElastic 上执行，避免阻塞事件循环", path);
        return chain.filter(exchange).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 前缀匹配带分隔符边界：{@code /api/exceptions} 命中 {@code /api/exceptions} 与
     * {@code /api/exceptions/abc}，但<b>不</b>命中 {@code /api/exceptions-extra}。
     *
     * @param path 应用内路径
     * @return 是否需要卸载
     */
    boolean matchesOffloadPrefix(final String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (final String prefix : OFFLOAD_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

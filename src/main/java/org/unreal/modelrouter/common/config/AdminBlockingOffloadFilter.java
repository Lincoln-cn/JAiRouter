package org.unreal.modelrouter.common.config;

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
 * 把「同步实现的管理端点」整体卸载到 {@link Schedulers#boundedElastic()}（#854、#864）。
 *
 * <p>本仓是纯 WebFlux 栈（只依赖 {@code spring-boot-starter-webflux}），但管理面有 30+ 个控制器
 * 仍写成同步方法并直接调用 JPA / 密码编码器：处理器方法跑在 Netty 事件循环线程上，一次慢查询
 * 或一次 bcrypt 校验就把业务请求一起堵住。#854 只按白名单覆盖了监控包的 7 个前缀，登录、
 * 账号与权限、服务实例、调用历史配置等同样同步的端点仍然漏在外面（#864）。</p>
 *
 * <p>逐个把上百个端点改成 {@code Mono.fromCallable(...)} 改动面大且容易漏，因此在过滤器链
 * 最内层按<b>路径</b>统一卸载，规则取「反向清单」而不是白名单：</p>
 *
 * <ul>
 *   <li>{@code /api/**} 一律卸载（管理面与控制台）——<b>新增管理端点无需再登记</b>；</li>
 *   <li>例外一：{@code /api/v1/**}（AI 代理面）。流式响应会长期占用线程，卸载会把
 *       {@code boundedElastic} 的线程借出去直到流结束，故保持原位；</li>
 *   <li>例外二：{@code /api/health-status/**}（SSE，内部已自行 {@code subscribeOn}）；</li>
 *   <li>额外：{@code /actuator/metrics}(/**)，指标查询同样会读库。</li>
 * </ul>
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
public class AdminBlockingOffloadFilter implements WebFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBlockingOffloadFilter.class);

    /** 管理面根前缀：其下端点按同步实现处理。 */
    static final String ADMIN_ROOT = "/api";

    /** AI 代理面：流式响应会长期占用线程，不能整体卸载。 */
    static final String AI_PROXY_PREFIX = "/api/v1";

    /** 自带线程调度的流式端点。 */
    static final List<String> SELF_SCHEDULED_PREFIXES = List.of("/api/health-status");

    /** 不在 {@code /api} 下、但同样同步查库的端点。 */
    static final List<String> EXTRA_PREFIXES = List.of("/actuator/metrics");

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!shouldOffload(path)) {
            return chain.filter(exchange);
        }
        LOGGER.debug("管理端点 {} 在 boundedElastic 上执行，避免阻塞事件循环", path);
        return chain.filter(exchange).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 判断某个应用内路径是否需要卸载到弹性线程池。
     *
     * @param path 去掉 context-path 后的请求路径
     * @return {@code true} 表示该路径的处理器应在 {@code boundedElastic} 上执行
     */
    boolean shouldOffload(final String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (matchesAny(path, EXTRA_PREFIXES)) {
            return true;
        }
        if (matchesAny(path, SELF_SCHEDULED_PREFIXES) || matches(path, AI_PROXY_PREFIX)) {
            return false;
        }
        return matches(path, ADMIN_ROOT);
    }

    /**
     * 前缀匹配带分隔符边界：{@code /api} 命中 {@code /api} 与 {@code /api/x}，
     * 但<b>不</b>命中 {@code /apix}。
     *
     * @param path   应用内路径
     * @param prefix 前缀
     * @return 是否命中
     */
    static boolean matches(final String path, final String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean matchesAny(final String path, final List<String> prefixes) {
        for (final String prefix : prefixes) {
            if (matches(path, prefix)) {
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

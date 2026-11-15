package org.unreal.modelrouter.monitor.tracing.helper;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把控制台的写操作路径翻译成可读的操作名（#1154）。
 *
 * <p>入口 span 原本用 {@code 方法 + 原始路径} 作为 operationName，例如
 * {@code PUT /admin/api/config/instance/chat/10}。管理员在链路列表里看到这一串，
 * 仍然要在脑子里把它翻译成「改了哪个实例」，所以写操作改成
 * {@code config.instance.update} 这样见名知意的名字。</p>
 *
 * <p>只覆盖写方法（POST / PUT / PATCH / DELETE）。读请求量大、语义单一，
 * 继续用「方法 + 路径」即可，不在这里登记。</p>
 *
 * <p>未登记的端点回落到原始名字，不做模糊匹配：宁可名字难看，也不要把
 * 两个不同的操作映射成同一个名字，那会让「谁改的」更难查。</p>
 */
public final class ConsoleOperationNames {

    /**
     * 单条映射规则。
     */
    private record Rule(String method, Pattern pattern, String operationName) {

        private Rule(final String method, final String regex, final String operationName) {
            this(method, Pattern.compile(regex), operationName);
        }
    }

    /**
     * 按顺序匹配，首个命中生效。顺序有意义：{@code /config/instance/{type}/{id}}
     * 必须排在 {@code /config/instance/{type}} 之前，否则 update 会被 create 抢走。
     */
    private static final List<Rule> RULES = Collections.unmodifiableList(List.of(
            new Rule("PUT", "^/api/config/instance/([^/]+)/([^/]+)$", "config.instance.update"),
            new Rule("DELETE", "^/api/config/instance/([^/]+)/([^/]+)$", "config.instance.delete"),
            new Rule("POST", "^/api/config/instance/([^/]+)$", "config.instance.create"),
            new Rule("PUT", "^/api/config/type/services/([^/]+)$", "config.type.update"),
            new Rule("PUT", "^/api/config/type/services/([^/]+)/instances$", "config.type.instances.update"),
            new Rule("POST", "^/api/auth/api-keys$", "apikey.create"),
            new Rule("POST", "^/api/auth/api-keys/([^/]+)/rotate$", "apikey.rotate"),
            new Rule("DELETE", "^/api/auth/api-keys/([^/]+)$", "apikey.revoke"),
            new Rule("PUT", "^/api/config/cluster$", "cluster.config.update"),
            new Rule("PUT", "^/api/config/health$", "health.config.update"),
            new Rule("PUT", "^/api/config/ratelimit$", "ratelimit.config.update"),
            new Rule("PUT", "^/api/config/cache$", "cache.config.update"),
            new Rule("PUT", "^/api/config/loadbalancer$", "loadbalancer.config.update")
    ));

    private ConsoleOperationNames() {
    }

    /**
     * 解析写操作的可读名字。
     *
     * @param method HTTP 方法（大写）
     * @param path   请求路径，可带 {@code /admin} 前缀
     * @return 可读操作名；读方法或未登记时返回 {@code null}
     */
    public static String resolve(final String method, final String path) {
        if (method == null || path == null) {
            return null;
        }
        if (!isWriteMethod(method)) {
            return null;
        }
        final String normalized = stripAdminPrefix(path);
        for (final Rule rule : RULES) {
            if (!rule.method().equalsIgnoreCase(method)) {
                continue;
            }
            final Matcher matcher = rule.pattern().matcher(normalized);
            if (matcher.matches()) {
                return rule.operationName();
            }
        }
        return null;
    }

    /**
     * 写方法判定。
     *
     * @param method HTTP 方法
     * @return 是否会产生副作用的写方法
     */
    public static boolean isWriteMethod(final String method) {
        if (method == null) {
            return false;
        }
        final String upper = method.toUpperCase(java.util.Locale.ROOT);
        return "POST".equals(upper) || "PUT".equals(upper)
                || "PATCH".equals(upper) || "DELETE".equals(upper);
    }

    /**
     * 判断一个 operationName 是不是本类映射出来的可读名。
     *
     * <p>可读名是 {@code config.instance.update} 这种点分形式，不含空格；
     * 原始名字是 {@code PUT /api/...}，一定带一个空格。控制台操作列表据此过滤。</p>
     *
     * @param operationName 入口 span 的操作名
     * @return 是否为已登记的写操作名
     */
    public static boolean isReadable(final String operationName) {
        if (operationName == null || operationName.isEmpty()) {
            return false;
        }
        return operationName.indexOf(' ') < 0 && operationName.indexOf('.') > 0;
    }

    /**
     * 控制台既可能通过 {@code /api/...} 直连，也可能经由 {@code /admin/api/...} 代理，
     * 两种前缀映射到同一组规则。
     *
     * @param path 原始路径
     * @return 去掉 {@code /admin} 前缀后的路径
     */
    public static String stripAdminPrefix(final String path) {
        if (path != null && path.startsWith("/admin/api/")) {
            return path.substring("/admin".length());
        }
        return path;
    }
}

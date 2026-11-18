package org.unreal.modelrouter.auth.security.permission;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * URL → 权限码映射规则（v2.9.8 RBAC 数据驱动 URL 权限矩阵）
 *
 * <p>一条规则描述：哪些 HTTP 方法 + 哪个路径模式（Ant 风格）需要哪个权限码。
 * {@code methods} 为空集合表示匹配任意 HTTP 方法。
 *
 * @param methods       匹配的 HTTP 方法集合（空集合 = 任意方法）
 * @param pathPattern   路径模式（Spring PathPattern 语法，如 /api/services/**）
 * @param permissionCode 需要的权限码（module:resource:action）
 * @author JAiRouter Team
 * @since 2.9.8
 */
public record PermissionRule(Set<HttpMethod> methods, String pathPattern, Set<String> permissionCodes) {

    /**
     * 单码便捷访问（日志/诊断/既有测试用）：返回集合中的第一个码。
     *
     * <p>数据层（{@code scope=mine}）引入后，一个路径可能同时接受「全局读码」与
     * 「本人读码」——判定用 {@link #permissionCodes()}，不要用本方法。
     */
    public String permissionCode() {
        return permissionCodes.isEmpty() ? null : permissionCodes.iterator().next();
    }

    /** 匹配任意 HTTP 方法的规则 */
    public static PermissionRule any(final String pathPattern, final String permissionCode) {
        return new PermissionRule(Set.of(), pathPattern, Set.of(permissionCode));
    }

    /** 仅匹配 GET 的规则（读操作） */
    public static PermissionRule get(final String pathPattern, final String permissionCode) {
        return new PermissionRule(Set.of(HttpMethod.GET), pathPattern, Set.of(permissionCode));
    }

    /**
     * 仅匹配 POST 的规则（用 POST 传查询体的**只读**端点）。
     *
     * <p>这类端点（如审计日志页的 {@code POST /api/security/audit/extended/query}）语义是读，
     * 但会被前缀上的 {@link #write} 规则要求写码，导致只读角色打得开页面却查不出数据。
     * 登记该规则时必须放在同前缀的 {@code write} 规则**之前** ——
     * {@link PermissionRuleRegistry#findRule} 是顺序匹配、首个命中即返回。
     */
    public static PermissionRule post(final String pathPattern, final String permissionCode) {
        return new PermissionRule(Set.of(HttpMethod.POST), pathPattern, Set.of(permissionCode));
    }

    /**
     * 仅匹配 GET、且**任一**给定权限码通过即可的规则。
     *
     * <p>用于「全局读」与「本人读」并存的数据面：持 {@code callhistory:read} 的人看全部，
     * 持 {@code callhistory:self:read} 的人只能看自己的（数据层再按 scope 过滤）。
     * <b>声明顺序有意义</b>：{@link #permissionCode()} 返回第一个码，主码写在前面。
     */
    public static PermissionRule getAny(final String pathPattern, final String... codes) {
        // 用 LinkedHashSet 保留声明顺序：Set.of(...) 的迭代顺序不保证，
        // 曾导致 permissionCode() 在不同 JVM 上返回 different 码（CI 红、本地绿）。
        return new PermissionRule(Set.of(HttpMethod.GET), pathPattern,
                Collections.unmodifiableSet(new LinkedHashSet<>(List.of(codes))));
    }

    /**
     * 仅要求「已认证」的规则（不需要任何权限码）。
     *
     * <p>用于「任何人都要能调用、但只有登录后能调用」的自助上报端点，例如
     * {@code POST /api/monitoring/ui-access}（记录当前用户看了哪一页）。
     * {@code permissionCodes} 为空集合，授权管理器据此只校验认证态。
     *
     * <p>登记时必须排在更宽的兜底规则<b>之前</b>（首条命中优先），否则会被兜底规则要求的权限码覆盖。
     */
    public static PermissionRule authenticated(final String pathPattern, final HttpMethod... methods) {
        return new PermissionRule(Set.of(methods), pathPattern, Set.of());
    }

    /** 匹配写操作（POST/PUT/DELETE/PATCH）的规则 */
    public static PermissionRule write(final String pathPattern, final String permissionCode) {
        return new PermissionRule(
                Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
                pathPattern, Set.of(permissionCode));
    }

    /**
     * 判断规则是否匹配指定方法与路径
     *
     * @param method  请求方法（可能为 null）
     * @param pattern 预编译的路径模式
     * @param path    请求路径
     * @return true 表示匹配
     */
    public boolean matches(final HttpMethod method, final PathPattern pattern, final String path) {
        if (!pattern.matches(PathContainer.parsePath(path))) {
            return false;
        }
        return methods.isEmpty() || (method != null && methods.contains(method));
    }
}

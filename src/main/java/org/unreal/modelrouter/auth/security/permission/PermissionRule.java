package org.unreal.modelrouter.auth.security.permission;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

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
public record PermissionRule(Set<HttpMethod> methods, String pathPattern, String permissionCode) {

    /** 匹配任意 HTTP 方法的规则 */
    public static PermissionRule any(final String pathPattern, final String permissionCode) {
        return new PermissionRule(Set.of(), pathPattern, permissionCode);
    }

    /** 仅匹配 GET 的规则（读操作） */
    public static PermissionRule get(final String pathPattern, final String permissionCode) {
        return new PermissionRule(Set.of(HttpMethod.GET), pathPattern, permissionCode);
    }

    /** 匹配写操作（POST/PUT/DELETE/PATCH）的规则 */
    public static PermissionRule write(final String pathPattern, final String permissionCode) {
        return new PermissionRule(
                Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
                pathPattern, permissionCode);
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

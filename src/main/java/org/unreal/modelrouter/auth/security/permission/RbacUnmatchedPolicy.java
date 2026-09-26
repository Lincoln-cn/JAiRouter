package org.unreal.modelrouter.auth.security.permission;

import org.springframework.http.HttpMethod;

import java.util.Locale;
import java.util.Set;

/**
 * 未命中 URL 权限规则时的姿态（#128 Phase 3）。
 *
 * <p>由 {@code jairouter.security.rbac.unmatched-policy} 配置，取代 Phase 2 的布尔
 * {@code jairouter.security.rbac.write-fail-closed.enabled}：
 * <ul>
 *   <li>{@link #AUTHENTICATED}——遗留 fail-open：未命中且未豁免 → 回退 authenticated
 *       （GET 与写方法皆然）</li>
 *   <li>{@link #DENY_WRITES}——Phase 2：未命中写方法且未豁免 → 仅 ADMIN 直通；
 *       GET 仍回退 authenticated</li>
 *   <li>{@link #DENY_ALL}——Phase 3 默认：未命中且未豁免（含 GET）→ 仅 ADMIN 直通</li>
 * </ul>
 *
 * <p>豁免清单命中时三种姿态一律回退 authenticated（豁免语义独立于姿态）。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
public enum RbacUnmatchedPolicy {

    /** 遗留 fail-open：未命中回退 authenticated（GET + 写） */
    AUTHENTICATED,

    /** Phase 2：写方法 fail-closed，GET fail-open */
    DENY_WRITES,

    /** Phase 3 默认：GET 与写方法均 fail-closed */
    DENY_ALL;

    /** 写方法集合 */
    private static final Set<HttpMethod> WRITE_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

    /**
     * 未命中且未豁免的请求是否应拒绝（仅 ADMIN 可通过）。
     *
     * @param method 请求方法（可为 null）
     * @return true 表示 fail-closed 拒绝
     */
    public boolean shouldDenyUnmatched(final HttpMethod method) {
        return switch (this) {
            case AUTHENTICATED -> false;
            case DENY_WRITES -> method != null && WRITE_METHODS.contains(method);
            case DENY_ALL -> true;
        };
    }

    /**
     * 解析配置字符串（大小写不敏感）。
     *
     * @param value 配置值
     * @return 对应姿态
     * @throws IllegalArgumentException 非法取值
     */
    public static RbacUnmatchedPolicy parse(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("unmatched-policy 不得为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

package org.unreal.modelrouter.common.ws;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 监控类 WebSocket 的控制消息授权判定（#916）。
 *
 * <p>背景：`/ws/**` 的安全规则只有 `authenticated()`——对「推送事件」而言这是对的，
 * 所有登录用户都应该能看监控；但这条通道里同时混进了写操作（`pause` / `resume` /
 * `updateConfig`），而 REST 侧的同名动作需要 {@code LB_CONFIG_WRITE} 之类的权限码
 * （见 {@code PermissionRuleRegistry}）。结果就是：同一把只有 {@code ROLE_CHAT}
 * 的 API Key，走 REST 会被 403 拦住，走 WebSocket 却能暂停整个实例的监控。
 *
 * <p>本类把「控制消息只允许平台管理员」的口径收敛到一处，两个 Handler 共用，
 * 避免它们各自漂移成两种判定。
 *
 * @author JAiRouter Team
 */
public final class WsControlAuthorization {

    /** 平台管理员角色名（与 {@code SecurityConfiguration} 的 {@code hasRole("ADMIN")} 对齐）。 */
    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private WsControlAuthorization() {
    }

    /**
     * 判定该握手主体是否为平台管理员。
     *
     * <p>只认 {@link Authentication}；非认证主体（例如裸 {@link Principal}）一律视为非管理员，
     * 因为无法从中读到权限集。凭据缺失同样返回 false——WS 连接本身已经被
     * {@code SecurityConfiguration} 的 {@code authenticated()} 拦过一次，这里只负责区分
     * 「普通用户」与「管理员」。
     *
     * @param principal 握手主体，可为 null
     * @return 是否具备 {@value #ADMIN_AUTHORITY}
     */
    public static boolean isPlatformAdmin(final Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return false;
        }
        return hasAdminAuthority(authentication);
    }

    /**
     * 判定该认证对象是否携带管理员角色。
     *
     * @param authentication 认证对象，可为 null
     * @return 是否具备 {@value #ADMIN_AUTHORITY}
     */
    public static boolean hasAdminAuthority(final Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getAuthorities() == null) {
            return false;
        }
        final Set<String> authorities = authentication.getAuthorities().stream()
                .filter(Objects::nonNull)
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toSet());
        return authorities.contains(ADMIN_AUTHORITY);
    }

    /**
     * 判断一条客户端消息是否属于「控制类」（会改变监控状态的那几类）。
     *
     * <p>抽出来是为了让 Handler 只在一处判断授权，且测试可以穷举控制类型的清单。
     *
     * @param type 消息类型原文，可为 null
     * @return 是否需要管理员权限
     */
    public static boolean isControlMessage(final String type) {
        if (type == null) {
            return false;
        }
        return switch (type.trim()) {
            case "pause", "resume", "updateConfig" -> true;
            default -> false;
        };
    }
}

package org.unreal.modelrouter.auth.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 数据驱动 URL 权限授权管理器（v2.9.8 RBAC）
 *
 * <p>替换 SecurityConfiguration 中 {@code /api/**} 的 {@code authenticated()} 规则：
 * <ol>
 *   <li>从 {@link PermissionRuleRegistry} 匹配 {method, path} → 权限码</li>
 *   <li>命中规则：ADMIN 角色直通，否则校验 authentication 是否携带该权限码 authority</li>
 *   <li>未命中规则：回退 authenticated（向后兼容，未迁移端点保持原行为）</li>
 * </ol>
 *
 * <p>authority 语义：角色为 {@code ROLE_} 前缀（hasRole），权限码为无前缀 authority
 * （与 API-Key 的 ROLE_* 权限语义隔离，避免冲突）。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Slf4j
@Component
public class PermissionAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    /** ADMIN 角色 authority（JwtAuthentication 将角色转为 ROLE_ 前缀） */
    private static final String ADMIN_ROLE_AUTHORITY = "ROLE_ADMIN";

    private final PermissionRuleRegistry permissionRuleRegistry;

    public PermissionAuthorizationManager(final PermissionRuleRegistry permissionRuleRegistry) {
        this.permissionRuleRegistry = permissionRuleRegistry;
    }

    @Override
    public Mono<AuthorizationDecision> check(
            final Mono<Authentication> authentication, final AuthorizationContext context) {
        HttpMethod method = context.getExchange().getRequest().getMethod();
        String path = context.getExchange().getRequest().getPath().value();

        Optional<PermissionRule> rule = permissionRuleRegistry.findRule(method, path);
        if (rule.isEmpty()) {
            // 无规则 → 回退 authenticated（未迁移端点向后兼容）
            return authentication
                    .map(auth -> new AuthorizationDecision(isAuthenticated(auth)))
                    .defaultIfEmpty(new AuthorizationDecision(false));
        }

        String requiredCode = rule.get().permissionCode();
        return authentication
                .map(auth -> new AuthorizationDecision(
                        isAuthenticated(auth)
                                && (hasAuthority(auth, ADMIN_ROLE_AUTHORITY)
                                || hasAuthority(auth, requiredCode))))
                .doOnNext(decision -> log.debug("URL 权限判定: method={}, path={}, code={}, decision={}",
                        method, path, requiredCode, decision.isGranted()))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean isAuthenticated(final Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    private boolean hasAuthority(final Authentication authentication, final String authority) {
        if (authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}

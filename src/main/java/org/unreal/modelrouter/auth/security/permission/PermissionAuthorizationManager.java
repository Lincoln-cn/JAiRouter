package org.unreal.modelrouter.auth.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

/**
 * 数据驱动 URL 权限授权管理器（v2.9.8 RBAC，#128 Phase 2）
 *
 * <p>替换 SecurityConfiguration 中 {@code /api/**} 的 {@code authenticated()} 规则：
 * <ol>
 *   <li>从 {@link PermissionRuleRegistry} 匹配 {method, path} → 权限码</li>
 *   <li>命中规则：ADMIN 角色直通，否则校验 authentication 是否携带该权限码 authority</li>
 *   <li>未命中规则：
 *     <ul>
 *       <li>写方法（POST/PUT/DELETE/PATCH）且不在 {@link RbacExemptEndpoints} 豁免清单 →
 *           <b>DENY</b>（Phase 2 fail-closed）；ADMIN 角色仍直通</li>
 *       <li>GET（或写方法命中豁免/逃生阀开启）→ 回退 authenticated（fail-open，
 *           GET 的 fail-closed 属 Phase 3）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>逃生阀：{@code jairouter.security.rbac.write-fail-closed.enabled=false} 恢复
 * Phase 1 的全量 fail-open（未命中写路径也回退 authenticated），供遇到未预见缺口时
 * 不经热修复即可回退。
 *
 * <p>authority 语义：角色为 {@code ROLE_} 前缀（hasRole），权限码为无前缀 authority
 * （与 API-Key 的 ROLE_* 权限语义隔离，避免冲突）。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@Slf4j
@Component
public class PermissionAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    /** ADMIN 角色 authority（JwtAuthentication 将角色转为 ROLE_ 前缀） */
    private static final String ADMIN_ROLE_AUTHORITY = "ROLE_ADMIN";

    /** 写方法 fail-closed 默认开启（#128 Phase 2 新默认） */
    static final boolean DEFAULT_WRITE_FAIL_CLOSED = true;

    /** 写方法集合（fail-closed 仅作用于这些方法） */
    private static final Set<HttpMethod> WRITE_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

    private final PermissionRuleRegistry permissionRuleRegistry;

    private final boolean writeFailClosed;

    public PermissionAuthorizationManager(final PermissionRuleRegistry permissionRuleRegistry) {
        this(permissionRuleRegistry, DEFAULT_WRITE_FAIL_CLOSED);
    }

    @Autowired
    public PermissionAuthorizationManager(final PermissionRuleRegistry permissionRuleRegistry,
                                          @Value("${jairouter.security.rbac.write-fail-closed.enabled:true}")
                                          final boolean writeFailClosed) {
        this.permissionRuleRegistry = permissionRuleRegistry;
        this.writeFailClosed = writeFailClosed;
    }

    @Override
    public Mono<AuthorizationDecision> check(
            final Mono<Authentication> authentication, final AuthorizationContext context) {
        HttpMethod method = context.getExchange().getRequest().getMethod();
        String path = context.getExchange().getRequest().getPath().value();

        Optional<PermissionRule> rule = permissionRuleRegistry.findRule(method, path);
        if (rule.isEmpty()) {
            return checkUnmatched(authentication, method, path);
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

    /**
     * 未命中规则时的判定（#128 Phase 2）：写方法 fail-closed，GET fail-open。
     */
    private Mono<AuthorizationDecision> checkUnmatched(
            final Mono<Authentication> authentication, final HttpMethod method, final String path) {
        if (writeFailClosed
                && method != null && WRITE_METHODS.contains(method)
                && !RbacExemptEndpoints.isExempt(method, path)) {
            // 写 fail-closed：未命中规则且未豁免 → 仅 ADMIN 直通，其余拒绝
            return authentication
                    .map(auth -> new AuthorizationDecision(
                            isAuthenticated(auth) && hasAuthority(auth, ADMIN_ROLE_AUTHORITY)))
                    .doOnNext(decision -> log.debug(
                            "URL 权限判定(fail-closed 写): method={}, path={}, decision={}",
                            method, path, decision.isGranted()))
                    .defaultIfEmpty(new AuthorizationDecision(false));
        }
        // fail-open：未命中规则回退 authenticated（GET 恒定；写方法在豁免/逃生阀下同样回退）
        return authentication
                .map(auth -> new AuthorizationDecision(isAuthenticated(auth)))
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

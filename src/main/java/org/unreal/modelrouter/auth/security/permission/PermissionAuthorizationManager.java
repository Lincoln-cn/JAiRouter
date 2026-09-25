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

/**
 * 数据驱动 URL 权限授权管理器（v2.9.8 RBAC，#128 Phase 3）
 *
 * <p>替换 SecurityConfiguration 中 {@code /api/**} 的 {@code authenticated()} 规则：
 * <ol>
 *   <li>从 {@link PermissionRuleRegistry} 匹配 {method, path} → 权限码</li>
 *   <li>命中规则：ADMIN 角色直通，否则校验 authentication 是否携带该权限码 authority</li>
 *   <li>未命中规则：按 {@link RbacUnmatchedPolicy} 姿态判定
 *     <ul>
 *       <li>不在 {@link RbacExemptEndpoints} 豁免清单 且 姿态要求拒绝 → <b>DENY</b>（仅 ADMIN 直通）</li>
 *       <li>豁免命中 或 姿态允许回退 → authenticated（fail-open）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>姿态配置（{@code jairouter.security.rbac.unmatched-policy}，Phase 3 默认 {@code DENY_ALL}）：
 * <ul>
 *   <li>{@code DENY_ALL}——未命中且未豁免（含 GET）→ 仅 ADMIN 直通</li>
 *   <li>{@code DENY_WRITES}——Phase 2：仅写方法 fail-closed，GET 仍回退 authenticated</li>
 *   <li>{@code AUTHENTICATED}——遗留 fail-open：全部回退 authenticated</li>
 * </ul>
 *
 * <p>兼容键 {@code jairouter.security.rbac.write-fail-closed.enabled}（Phase 2）仅在
 * {@code unmatched-policy} <b>未显式配置</b>时生效：{@code false}→{@code AUTHENTICATED}，
 * {@code true}→{@code DENY_WRITES}。两者同配时 {@code unmatched-policy} 优先，
 * 因此永远不会互相矛盾。回退到遗留行为无需改码：
 * {@code jairouter.security.rbac.unmatched-policy=AUTHENTICATED}。
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

    /**
     * Phase 3 默认姿态：未命中 GET 也 fail-closed。
     * （Phase 2 曾以 {@code DEFAULT_WRITE_FAIL_CLOSED=true} 表示 DENY_WRITES。）
     */
    static final RbacUnmatchedPolicy DEFAULT_POLICY = RbacUnmatchedPolicy.DENY_ALL;

    private final PermissionRuleRegistry permissionRuleRegistry;

    private final RbacUnmatchedPolicy unmatchedPolicy;

    public PermissionAuthorizationManager(final PermissionRuleRegistry permissionRuleRegistry) {
        this(permissionRuleRegistry, DEFAULT_POLICY);
    }

    public PermissionAuthorizationManager(final PermissionRuleRegistry permissionRuleRegistry,
                                          final RbacUnmatchedPolicy unmatchedPolicy) {
        this.permissionRuleRegistry = permissionRuleRegistry;
        this.unmatchedPolicy = unmatchedPolicy;
    }

    /**
     * Phase 2 兼容构造：{@code true}→{@link RbacUnmatchedPolicy#DENY_WRITES}，
     * {@code false}→{@link RbacUnmatchedPolicy#AUTHENTICATED}。
     */
    public PermissionAuthorizationManager(final PermissionRuleRegistry permissionRuleRegistry,
                                          final boolean writeFailClosed) {
        this(permissionRuleRegistry,
                writeFailClosed ? RbacUnmatchedPolicy.DENY_WRITES : RbacUnmatchedPolicy.AUTHENTICATED);
    }

    /**
     * Spring 装配入口：解析 {@code unmatched-policy} 与遗留 {@code write-fail-closed.enabled}。
     */
    @Autowired
    public PermissionAuthorizationManager(final PermissionRuleRegistry permissionRuleRegistry,
                                          @Value("${jairouter.security.rbac.unmatched-policy:}")
                                          final String unmatchedPolicyRaw,
                                          @Value("${jairouter.security.rbac.write-fail-closed.enabled:}")
                                          final String writeFailClosedRaw) {
        this(permissionRuleRegistry, resolvePolicy(unmatchedPolicyRaw, writeFailClosedRaw));
    }

    /**
     * 姿态解析（两键永远不会矛盾——unmatched-policy 显式配置时优先）：
     * <ol>
     *   <li>{@code unmatched-policy} 显式配置 → 直接采用</li>
     *   <li>否则 {@code write-fail-closed.enabled=false} → AUTHENTICATED；
     *       {@code =true} → DENY_WRITES</li>
     *   <li>都未配置 → {@link #DEFAULT_POLICY}（Phase 3 DENY_ALL）</li>
     * </ol>
     */
    static RbacUnmatchedPolicy resolvePolicy(final String unmatchedPolicyRaw,
                                             final String writeFailClosedRaw) {
        if (unmatchedPolicyRaw != null && !unmatchedPolicyRaw.isBlank()) {
            return RbacUnmatchedPolicy.parse(unmatchedPolicyRaw);
        }
        if (writeFailClosedRaw != null && !writeFailClosedRaw.isBlank()) {
            return Boolean.parseBoolean(writeFailClosedRaw.trim())
                    ? RbacUnmatchedPolicy.DENY_WRITES
                    : RbacUnmatchedPolicy.AUTHENTICATED;
        }
        return DEFAULT_POLICY;
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
     * 未命中规则时的判定：按 {@link RbacUnmatchedPolicy} 姿态 + 豁免清单决定。
     */
    private Mono<AuthorizationDecision> checkUnmatched(
            final Mono<Authentication> authentication, final HttpMethod method, final String path) {
        if (unmatchedPolicy.shouldDenyUnmatched(method) && !RbacExemptEndpoints.isExempt(method, path)) {
            // fail-closed：未命中规则且未豁免 → 仅 ADMIN 直通，其余拒绝
            return authentication
                    .map(auth -> new AuthorizationDecision(
                            isAuthenticated(auth) && hasAuthority(auth, ADMIN_ROLE_AUTHORITY)))
                    .doOnNext(decision -> log.debug(
                            "URL 权限判定(fail-closed {}): method={}, path={}, decision={}",
                            unmatchedPolicy, method, path, decision.isGranted()))
                    .defaultIfEmpty(new AuthorizationDecision(false));
        }
        // fail-open：豁免命中，或姿态允许回退 authenticated
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

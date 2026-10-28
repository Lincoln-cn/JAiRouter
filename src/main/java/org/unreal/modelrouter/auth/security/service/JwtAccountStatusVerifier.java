package org.unreal.modelrouter.auth.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * JWT 账户状态复核器（issue #704）。
 *
 * <p>验签与过期检查只能证明「令牌是本站签发的、且尚未过期」，不能证明「签发对象此刻仍是一个
 * 可用账户」。账户被删除或禁用后，旧令牌仍能在 {@code jairouter.security.jwt.expiration-minutes}
 * （默认 60）内通过鉴权——本类补上这一步复核，让账户状态变更对已签发令牌立即生效：既不需要等
 * 令牌自然过期，也不依赖「能否枚举某个账户已签发的全部令牌」。</p>
 *
 * <p><b>账户解析顺序与登录路径保持一致</b>（{@link AccountManager}）：先看 YAML 静态账户
 * （只认 {@code enabled=true}），未命中再看 {@code jwt_accounts} 表（{@code enabled} 为空视为启用）。
 * 这样保证「能登录的账户，令牌就一定有效」，不会出现「能拿到令牌但每次调用都被拒」的自锁；
 * 默认 {@code admin} 这类同时存在于 YAML 与数据库的账户，也不会因为 DB 镜像行被清掉就整体不可用。</p>
 *
 * @since 3.5.5
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class JwtAccountStatusVerifier {

    private final SecurityProperties securityProperties;
    private final JwtAccountRepository jwtAccountRepository;

    /**
     * 判断账户当前是否可用。
     *
     * @param username 令牌 subject（账户名）
     * @return {@code true} 表示账户仍可用；账户不存在、已禁用或入参为空都返回 {@code false}
     */
    public Mono<Boolean> isActive(final String username) {
        if (username == null || username.isBlank()) {
            return Mono.just(false);
        }
        // JPA 是阻塞调用，而鉴权发生在 Netty 事件循环上：必须让出线程（同 issue #262/#115 的处理口径）。
        return Mono.fromCallable(() -> resolveActive(username))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步解析账户状态（会查库，仅供 {@link #isActive(String)} 在弹性线程池上调用）。
     *
     * @param username 账户名
     * @return 账户是否可用
     */
    private boolean resolveActive(final String username) {
        final List<JwtAccountProperties> yamlAccounts =
                securityProperties.getJwt() != null ? securityProperties.getJwt().getAccounts() : null;
        if (yamlAccounts != null && yamlAccounts.stream()
                .anyMatch(account -> account.isEnabled() && username.equals(account.getUsername()))) {
            return true;
        }
        return jwtAccountRepository.findByUsername(username)
                .map(account -> !Boolean.FALSE.equals(account.getEnabled()))
                .orElse(false);
    }
}

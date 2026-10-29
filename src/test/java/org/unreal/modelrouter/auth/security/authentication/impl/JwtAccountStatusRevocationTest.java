package org.unreal.modelrouter.auth.security.authentication.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.JwtAccountStatusVerifier;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.SecurityException;
import org.unreal.modelrouter.persistence.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 账户被删除/禁用后，已签发的 JWT 令牌必须立刻失效（#704）。
 *
 * <p>修复前 {@code validateToken} 只查黑名单 + 验签 + 过期，完全不看账户状态：删掉或禁用账户后，
 * 旧令牌仍能在 {@code expiration-minutes}（默认 60 分钟）内通过鉴权，而且还能一路刷新续期。
 * 本测试在旧实现上是红的（会拿到正常的 {@link Authentication} 而不是 JWT_INVALID）。</p>
 *
 * <p>账户解析顺序刻意与登录路径（{@code AccountManager}）一致：YAML 静态账户优先，DB 兜底。
 * 否则默认 {@code admin}（YAML 与 DB 双写）在 DB 行被删后会「能登录、但每次调用 401」。</p>
 *
 * @since 3.5.5
 */
@DisplayName("#704 账户删除/禁用后令牌立即失效")
class JwtAccountStatusRevocationTest {

    private static final String SECRET = "test-secret-key-for-jwt-account-revoke-min-32-chars";
    private static final String ISSUER = "jairouter-test";
    private static final String USERNAME = "bob";

    private SecurityProperties securityProperties;
    private JwtConfig jwtConfig;
    private JwtAccountRepository jwtAccountRepository;
    private DefaultJwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        securityProperties = Mockito.mock(SecurityProperties.class);
        jwtConfig = Mockito.mock(JwtConfig.class);
        jwtAccountRepository = Mockito.mock(JwtAccountRepository.class);

        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.getSecret()).thenReturn(SECRET);
        when(jwtConfig.getIssuer()).thenReturn(ISSUER);
        when(jwtConfig.getExpirationMinutes()).thenReturn(60L);
        when(jwtConfig.getRefreshExpirationDays()).thenReturn(7L);
        when(jwtConfig.isBlacklistEnabled()).thenReturn(false);
        when(jwtConfig.getAccounts()).thenReturn(List.of());

        validator = new DefaultJwtTokenValidator(securityProperties, null);
        ReflectionTestUtils.setField(validator, "accountStatusVerifier",
                new JwtAccountStatusVerifier(securityProperties, jwtAccountRepository));
    }

    @Test
    @DisplayName("账户被删除（库中已无该行）后，旧令牌不得再通过鉴权")
    void tokenOfDeletedAccountIsRejected() {
        when(jwtAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        StepVerifier.create(validator.validateToken(token()))
                .expectErrorMatches(JwtAccountStatusRevocationTest::isRejectedAsInvalidToken)
                .verify();
    }

    @Test
    @DisplayName("账户被禁用后，旧令牌不得再通过鉴权")
    void tokenOfDisabledAccountIsRejected() {
        when(jwtAccountRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(account(USERNAME, Boolean.FALSE)));

        StepVerifier.create(validator.validateToken(token()))
                .expectErrorMatches(JwtAccountStatusRevocationTest::isRejectedAsInvalidToken)
                .verify();
    }

    @Test
    @DisplayName("账户仍启用时照常放行（回归保护：不得把正常账户一起拒掉）")
    void tokenOfActiveAccountStillPasses() {
        when(jwtAccountRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(account(USERNAME, Boolean.TRUE)));

        StepVerifier.create(validator.validateToken(token()))
                .assertNext(authentication -> assertEquals(USERNAME, authentication.getName()))
                .verifyComplete();
    }

    @Test
    @DisplayName("enabled 为空的账户按启用处理（与登录路径同口径）")
    void nullEnabledAccountCountsAsEnabled() {
        when(jwtAccountRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(account(USERNAME, null)));

        StepVerifier.create(validator.validateToken(token()))
                .assertNext(authentication -> assertEquals(USERNAME, authentication.getName()))
                .verifyComplete();
    }

    @Test
    @DisplayName("YAML 静态账户即使没有 DB 镜像行也放行（与登录路径同口径，避免自锁）")
    void yamlAccountStaysValidWithoutDbRow() {
        when(jwtConfig.getAccounts()).thenReturn(List.of(yamlAccount(USERNAME, true)));
        when(jwtAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        StepVerifier.create(validator.validateToken(token()))
                .assertNext(authentication -> assertEquals(USERNAME, authentication.getName()))
                .verifyComplete();
    }

    @Test
    @DisplayName("YAML 里已禁用的账户不能靠 DB 行复活")
    void disabledYamlAccountWithStaleDbRowIsStillRejected() {
        when(jwtConfig.getAccounts()).thenReturn(List.of(yamlAccount(USERNAME, false)));
        when(jwtAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        StepVerifier.create(validator.validateToken(token()))
                .expectErrorMatches(JwtAccountStatusRevocationTest::isRejectedAsInvalidToken)
                .verify();
    }

    @Test
    @DisplayName("#789 库中禁用优先：YAML 里仍启用也不能复活")
    void disabledDbRowOverridesEnabledYamlAccount() {
        when(jwtConfig.getAccounts()).thenReturn(List.of(yamlAccount(USERNAME, true)));
        when(jwtAccountRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(account(USERNAME, Boolean.FALSE)));

        StepVerifier.create(validator.validateToken(token()))
                .expectErrorMatches(JwtAccountStatusRevocationTest::isRejectedAsInvalidToken)
                .verify();
    }

    @Test
    @DisplayName("#789 库中软删除（deleted=true）后旧令牌失效")
    void softDeletedDbRowIsRejected() {
        JwtAccountEntity deleted = account(USERNAME, Boolean.TRUE);
        deleted.setDeleted(Boolean.TRUE);
        when(jwtAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(deleted));

        StepVerifier.create(validator.validateToken(token()))
                .expectErrorMatches(JwtAccountStatusRevocationTest::isRejectedAsInvalidToken)
                .verify();
    }

    @Test
    @DisplayName("账户被删除后，旧令牌也不能刷出新令牌")
    void deletedAccountCannotRefresh() {
        when(jwtAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        StepVerifier.create(validator.refreshToken(token()))
                .expectErrorMatches(JwtAccountStatusRevocationTest::isRejectedAsInvalidToken)
                .verify();
    }

    @Test
    @DisplayName("账户状态复核失败按 fail-closed 拒绝（不得因查库异常放行）")
    void accountLookupFailureIsFailClosed() {
        when(jwtAccountRepository.findByUsername(USERNAME))
                .thenThrow(new IllegalStateException("db down"));

        StepVerifier.create(validator.validateToken(token()))
                .expectErrorMatches(error -> error instanceof SecurityException security
                        && ErrorCode.JWT_VALIDATION_ERROR.code().equals(security.getErrorCode()))
                .verify();
    }

    private static boolean isRejectedAsInvalidToken(final Throwable error) {
        return error instanceof SecurityException security
                && ErrorCode.JWT_INVALID.code().equals(security.getErrorCode());
    }

    private String token() {
        return validator.generateToken(USERNAME, List.of("USER"), new HashMap<>());
    }

    private static JwtAccountEntity account(final String username, final Boolean enabled) {
        return JwtAccountEntity.builder()
                .username(username)
                .password("encoded")
                .roles("[\"USER\"]")
                .enabled(enabled)
                .build();
    }

    private static JwtAccountProperties yamlAccount(final String username, final boolean enabled) {
        JwtAccountProperties properties = new JwtAccountProperties();
        properties.setUsername(username);
        properties.setPassword("{noop}pwd");
        properties.setRoles(List.of("USER"));
        properties.setEnabled(enabled);
        return properties;
    }
}

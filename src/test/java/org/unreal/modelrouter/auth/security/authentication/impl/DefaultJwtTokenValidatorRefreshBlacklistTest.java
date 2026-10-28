package org.unreal.modelrouter.auth.security.authentication.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.EnhancedJwtBlacklistService;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.SecurityException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 刷新令牌必须先过黑名单（#694）。
 *
 * <p>修复前 {@code refreshToken} 只解析签名与过期时间就签发新令牌，并把旧令牌拉黑；
 * 于是「已吊销但未过期」的令牌仍能换出等价的新令牌，吊销形同虚设。
 * 本测试在旧实现上是红的（会拿到新令牌而不是 {@code JWT_BLACKLISTED}）。</p>
 *
 * @since 3.5.5
 */
@DisplayName("#694 刷新前查黑名单")
class DefaultJwtTokenValidatorRefreshBlacklistTest {

    private static final String SECRET = "test-secret-key-for-jwt-refresh-min-32-chars";
    private static final String ISSUER = "jairouter-test";

    private SecurityProperties securityProperties;
    private JwtConfig jwtConfig;
    private EnhancedJwtBlacklistService blacklistService;
    private DefaultJwtTokenValidator validator;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        securityProperties = Mockito.mock(SecurityProperties.class);
        jwtConfig = Mockito.mock(JwtConfig.class);
        blacklistService = Mockito.mock(EnhancedJwtBlacklistService.class);

        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.getSecret()).thenReturn(SECRET);
        when(jwtConfig.getIssuer()).thenReturn(ISSUER);
        when(jwtConfig.getExpirationMinutes()).thenReturn(30L);
        when(jwtConfig.getRefreshExpirationDays()).thenReturn(7L);
        when(jwtConfig.isBlacklistEnabled()).thenReturn(true);

        validator = new DefaultJwtTokenValidator(securityProperties, null);
        ReflectionTestUtils.setField(validator, "enhancedBlacklistService", blacklistService);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("已在黑名单的令牌不得换取新令牌")
    void blacklistedTokenCannotBeRefreshed() {
        when(blacklistService.isBlacklisted(anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(validator.refreshToken(token()))
                .expectErrorMatches(error -> error instanceof SecurityException security
                        && ErrorCode.JWT_BLACKLISTED.code().equals(security.getErrorCode()))
                .verify();
    }

    @Test
    @DisplayName("未进黑名单的令牌仍可正常刷新（回归保护）")
    void activeTokenCanStillBeRefreshed() {
        when(blacklistService.isBlacklisted(anyString())).thenReturn(Mono.just(false));

        final String oldToken = token();
        final String oldJti = claimsOf(oldToken).getId();
        StepVerifier.create(validator.refreshToken(oldToken))
                .assertNext(newToken -> {
                    assertNotEquals(oldToken, newToken, "应签发一枚新令牌（字符串不得与旧令牌相同）");
                    assertEquals("admin", claimsOf(newToken).getSubject(), "subject 应保持不变");
                    assertNotEquals(oldJti, claimsOf(newToken).getId(),
                            "jti 必须轮换：否则旧 jti 刚被拉黑，新令牌会立刻不可用");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("黑名单整体关闭时不查存储，刷新照常")
    void refreshWorksWhenBlacklistDisabled() {
        when(jwtConfig.isBlacklistEnabled()).thenReturn(false);

        StepVerifier.create(validator.refreshToken(token()))
                .assertNext(newToken -> assertNotEquals("", newToken))
                .verifyComplete();
    }

    private io.jsonwebtoken.Claims claimsOf(final String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private String token() {
        final Date now = new Date();
        final Date expiration = new Date(now.getTime() + 30 * 60 * 1000L);
        return Jwts.builder()
                .subject("admin")
                .issuer(ISSUER)
                .issuedAt(now)
                .expiration(expiration)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("ADMIN"))
                .signWith(key)
                .compact();
    }
}

package org.unreal.modelrouter.auth.security.authentication.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * 刷新窗口必须真的生效（issue #1073）。
 *
 * <p>修复前 {@code doRefreshToken} 第一步调用 {@code parseToken}，而 jjwt 的
 * {@code parseSignedClaims} 在解析阶段就校验 {@code exp} 并抛 {@link io.jsonwebtoken.ExpiredJwtException}
 * ⇒ 后面的「过期未超 N 天仍可续期」判断永远走不到，{@code refresh-expiration-days} 是能设、能回显、
 * 无效的配置项。</p>
 *
 * <p>本测试在旧实现上对 {@code expiredWithinWindowStillRefreshes} 是红的
 * （过期令牌一律刷新失败），这正是该配置项无效的判据。</p>
 */
@DisplayName("#1073 刷新窗口生效")
class DefaultJwtTokenValidatorRefreshWindowTest {

    private static final String SECRET = "test-secret-key-for-jwt-refresh-window-min-32-chars";
    private static final String ISSUER = "jairouter-test";

    /** 与 {@code JwtConfig#refreshExpirationDays} 的默认值一致（issue #1073 由 7 收紧到 1）。 */
    private static final long REFRESH_WINDOW_DAYS = 1L;

    private JwtConfig jwtConfig;
    private EnhancedJwtBlacklistService blacklistService;
    private DefaultJwtTokenValidator validator;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        final SecurityProperties securityProperties = Mockito.mock(SecurityProperties.class);
        jwtConfig = Mockito.mock(JwtConfig.class);
        blacklistService = Mockito.mock(EnhancedJwtBlacklistService.class);

        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.getSecret()).thenReturn(SECRET);
        when(jwtConfig.getIssuer()).thenReturn(ISSUER);
        when(jwtConfig.getExpirationMinutes()).thenReturn(30L);
        when(jwtConfig.getRefreshExpirationDays()).thenReturn(REFRESH_WINDOW_DAYS);
        when(jwtConfig.isBlacklistEnabled()).thenReturn(true);
        when(blacklistService.isBlacklisted(anyString())).thenReturn(Mono.just(false));

        validator = new DefaultJwtTokenValidator(securityProperties, null);
        ReflectionTestUtils.setField(validator, "enhancedBlacklistService", blacklistService);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("刷新")
    class Refresh {

        @Test
        @DisplayName("过期但仍在窗口内的令牌可以换出新令牌")
        void expiredWithinWindowStillRefreshes() {
            final String expired = tokenExpiredMillisAgo(60 * 60 * 1000L, ISSUER);

            StepVerifier.create(validator.refreshToken(expired))
                    .assertNext(newToken -> {
                        assertNotEquals(expired, newToken, "应签发一枚新令牌");
                        assertEquals("admin", claimsOf(newToken).getSubject(), "subject 应保持不变");
                        assertEquals(ISSUER, claimsOf(newToken).getIssuer());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("过期超过窗口的令牌被拒绝，错误码为 JWT_REFRESH_EXPIRED")
        void expiredBeyondWindowIsRejected() {
            final String expired = tokenExpiredMillisAgo(3L * 24 * 60 * 60 * 1000L, ISSUER);

            StepVerifier.create(validator.refreshToken(expired))
                    .expectErrorMatches(error -> error instanceof SecurityException security
                            && ErrorCode.JWT_REFRESH_EXPIRED.code().equals(security.getErrorCode()))
                    .verify();
        }

        @Test
        @DisplayName("过期令牌的签发者不匹配时拒绝，不得借刷新路径绕过 issuer 校验")
        void expiredTokenWithForeignIssuerIsRejected() {
            final String expired = tokenExpiredMillisAgo(60 * 60 * 1000L, "evil-issuer");

            StepVerifier.create(validator.refreshToken(expired))
                    .expectErrorMatches(error -> error instanceof SecurityException security
                            && ErrorCode.JWT_INVALID.code().equals(security.getErrorCode()))
                    .verify();
        }

        @Test
        @DisplayName("未过期令牌照常刷新（回归保护）")
        void nonExpiredTokenStillRefreshes() {
            final String active = tokenValidFor(15 * 60 * 1000L);

            StepVerifier.create(validator.refreshToken(active))
                    .assertNext(newToken -> assertNotEquals(active, newToken))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("校验")
    class Validation {

        @Test
        @DisplayName("过期令牌在校验侧仍被拒绝，且错误码是 JWT_EXPIRED 而不是 JWT_INVALID")
        void expiredTokenIsRejectedWithExpiredCode() {
            final String expired = tokenExpiredMillisAgo(60 * 60 * 1000L, ISSUER);

            StepVerifier.create(validator.validateToken(expired))
                    .expectErrorMatches(error -> error instanceof SecurityException security
                            && ErrorCode.JWT_EXPIRED.code().equals(security.getErrorCode()))
                    .verify();
        }
    }

    private io.jsonwebtoken.Claims claimsOf(final String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private String tokenExpiredMillisAgo(final long millisAgo, final String issuer) {
        final Date issuedAt = new Date(System.currentTimeMillis() - millisAgo - 60 * 60 * 1000L);
        final Date expiration = new Date(System.currentTimeMillis() - millisAgo);
        return build(issuer, issuedAt, expiration);
    }

    private String tokenValidFor(final long millisValid) {
        final Date now = new Date();
        return build(ISSUER, now, new Date(now.getTime() + millisValid));
    }

    private String build(final String issuer, final Date issuedAt, final Date expiration) {
        return Jwts.builder()
                .subject("admin")
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("ADMIN"))
                .signWith(key)
                .compact();
    }
}

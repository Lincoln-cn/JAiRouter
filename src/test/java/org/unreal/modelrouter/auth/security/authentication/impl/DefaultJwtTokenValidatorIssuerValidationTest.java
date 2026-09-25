/*
 * Copyright 2024 JAiRouter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.auth.security.authentication.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * issuer（iss）强制校验测试（issue #117）.
 *
 * <p>签发路径始终写入 iss，解析路径必须拒绝 iss 错误或缺失的令牌，
 * 且失败走与无效签名相同的 {@link AuthenticationException} 通道。</p>
 */
@DisplayName("JWT issuer 强制校验测试（issue #117）")
class DefaultJwtTokenValidatorIssuerValidationTest {

    private static final String TEST_SECRET = "test-secret-key-for-jwt-validation-min-32-chars";
    private static final String EXPECTED_ISSUER = "jairouter-test";

    private JwtConfig jwtConfig;
    private DefaultJwtTokenValidator validator;
    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setSecret(TEST_SECRET);
        jwtConfig.setIssuer(EXPECTED_ISSUER);
        jwtConfig.setExpirationMinutes(30L);
        jwtConfig.setRefreshExpirationDays(7L);
        jwtConfig.setBlacklistEnabled(false);

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setJwt(jwtConfig);

        // 黑名单关闭时不会触达 redisTemplate，可传 null
        validator = new DefaultJwtTokenValidator(securityProperties, null);
        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("ISS-001: 正确 issuer 的令牌被接受")
    void acceptsTokenWithExpectedIssuer() {
        String token = tokenWithIssuer(EXPECTED_ISSUER);

        StepVerifier.create(validator.validateToken(token))
            .assertNext(auth -> assertEquals("user123", auth.getName()))
            .verifyComplete();
    }

    @Test
    @DisplayName("ISS-002: 错误 issuer 的令牌被拒绝（与无效签名同一失败通道）")
    void rejectsTokenWithWrongIssuer() {
        String token = tokenWithIssuer("evil-issuer");

        StepVerifier.create(validator.validateToken(token))
            .expectErrorSatisfies(err -> {
                AuthenticationException ex =
                        assertInstanceOf(AuthenticationException.class, err);
                assertEquals("JWT_INVALID", ex.getErrorCode());
            })
            .verify();
    }

    @Test
    @DisplayName("ISS-003: 缺失 issuer 的令牌被拒绝（与无效签名同一失败通道）")
    void rejectsTokenWithMissingIssuer() {
        String token = tokenWithoutIssuer();

        StepVerifier.create(validator.validateToken(token))
            .expectErrorSatisfies(err -> {
                AuthenticationException ex =
                        assertInstanceOf(AuthenticationException.class, err);
                assertEquals("JWT_INVALID", ex.getErrorCode());
            })
            .verify();
    }

    @Test
    @DisplayName("ISS-004: 本校验器 generateToken 签发的令牌（含 iss）可被自身解析")
    void generatedTokenRoundTrips() {
        String token = validator.generateToken("user123", List.of("USER"), null);

        StepVerifier.create(validator.validateToken(token))
            .assertNext(auth -> assertEquals("user123", auth.getName()))
            .verifyComplete();
    }

    private String tokenWithIssuer(final String issuer) {
        Date now = new Date();
        return Jwts.builder()
            .subject("user123")
            .issuer(issuer)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + Duration.ofMinutes(30).toMillis()))
            .id(UUID.randomUUID().toString())
            .claim("roles", List.of("USER"))
            .signWith(testKey)
            .compact();
    }

    private String tokenWithoutIssuer() {
        Date now = new Date();
        return Jwts.builder()
            .subject("user123")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + Duration.ofMinutes(30).toMillis()))
            .id(UUID.randomUUID().toString())
            .claim("roles", List.of("USER"))
            .signWith(testKey)
            .compact();
    }
}

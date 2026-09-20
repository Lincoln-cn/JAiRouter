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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DefaultJwtTokenValidator 的 legacy 黑名单分支降级测试（issue #76）.
 *
 * <p>当增强黑名单服务未装配（{@code jwt.blacklist.redis.enabled=false}）时，
 * 校验走 legacy Redis 分支，原实现每个请求打两条 ERROR。这里锁定修复后的行为。</p>
 */
@DisplayName("DefaultJwtTokenValidator legacy 黑名单降级测试（issue #76）")
class DefaultJwtTokenValidatorBlacklistDegradationTest {

    private static final String TEST_SECRET = "test-secret-key-for-jwt-validation-min-32-chars";
    private static final String TEST_ISSUER = "jairouter-test";

    private JwtConfig jwtProperties;
    private ReactiveStringRedisTemplate redisTemplate;
    private DefaultJwtTokenValidator validator;
    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = mock(SecurityProperties.class);
        jwtProperties = mock(JwtConfig.class);
        redisTemplate = mock(ReactiveStringRedisTemplate.class);

        when(securityProperties.getJwt()).thenReturn(jwtProperties);
        when(jwtProperties.isBlacklistEnabled()).thenReturn(true);
        when(jwtProperties.getSecret()).thenReturn(TEST_SECRET);
        when(jwtProperties.getIssuer()).thenReturn(TEST_ISSUER);

        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        // 直接构造：构造器只含 securityProperties + redisTemplate，
        // enhancedBlacklistService 保持 null → 强制走 legacy Redis 分支
        validator = new DefaultJwtTokenValidator(securityProperties, redisTemplate);
    }

    @Nested
    @DisplayName("legacy Redis 分支降级")
    class LegacyBranchTests {

        @Test
        @DisplayName("BLK-010: 存储不可用时降级放行，且短路窗口内不再查询存储")
        void degradesAndShortCircuits() {
            when(redisTemplate.hasKey(anyString()))
                .thenReturn(Mono.error(new IllegalStateException("Unable to connect to Redis")));
            String token = newToken();

            StepVerifier.create(validator.isTokenBlacklisted(token)).expectNext(false).verifyComplete();
            StepVerifier.create(validator.isTokenBlacklisted(token)).expectNext(false).verifyComplete();

            // 原实现每个请求打两条 ERROR 且各付一次连接超时
            verify(redisTemplate, times(1)).hasKey(anyString());
        }

        @Test
        @DisplayName("BLK-011: 存储可用时返回真实黑名单判定")
        void returnsRealVerdictWhenStorageAvailable() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

            StepVerifier.create(validator.isTokenBlacklisted(newToken()))
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        @DisplayName("BLK-012: 黑名单关闭时不查询存储")
        void skipsWhenBlacklistDisabled() {
            when(jwtProperties.isBlacklistEnabled()).thenReturn(false);

            StepVerifier.create(validator.isTokenBlacklisted(newToken()))
                .expectNext(false)
                .verifyComplete();
            verifyNoInteractions(redisTemplate);
        }
    }

    private String newToken() {
        Date now = new Date();
        return Jwts.builder()
            .subject("admin")
            .issuer(TEST_ISSUER)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + Duration.ofMinutes(30).toMillis()))
            .id(UUID.randomUUID().toString())
            .claim("roles", List.of("ADMIN"))
            .signWith(testKey)
            .compact();
    }
}

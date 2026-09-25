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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 黑名单存储不可用时 fail-closed 可选行为测试（issue #118）—— DefaultJwtTokenValidator legacy 分支.
 *
 * <p>默认 {@code blacklistFailClosedWhenUnavailable=false} 时行为与历史完全一致（放行）；
 * 置为 true 时，存储不可用/降级短路的放行路径改为按“在黑名单”拒绝。
 * 覆盖短路窗口与 onErrorResume 两条降级路径。
 * EnhancedJwtBlacklistService 的等价回退见同名测试（service 包）。</p>
 */
@DisplayName("黑名单 fail-closed 可选行为测试（issue #118）")
class BlacklistFailClosedWhenUnavailableTest {

    private static final String TEST_SECRET = "test-secret-key-for-jwt-validation-min-32-chars";
    private static final String TEST_ISSUER = "jairouter-test";

    private JwtConfig jwtConfig;
    private SecurityProperties securityProperties;
    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setSecret(TEST_SECRET);
        jwtConfig.setIssuer(TEST_ISSUER);
        jwtConfig.setExpirationMinutes(30L);
        jwtConfig.setBlacklistEnabled(true);
        jwtConfig.setBlacklistFailClosedWhenUnavailable(false);

        securityProperties = new SecurityProperties();
        securityProperties.setJwt(jwtConfig);

        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
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

    /** 手写假 Redis：可注入 hasKey 失败/存在并统计调用次数（避免 Mockito）。 */
    static final class FakeFailingRedisTemplate extends ReactiveStringRedisTemplate {
        private final AtomicInteger hasKeyCalls = new AtomicInteger();
        private volatile boolean failing = true;
        private volatile boolean exists = false;

        FakeFailingRedisTemplate() {
            super(new LettuceConnectionFactory());
        }

        @Override
        public Mono<Boolean> hasKey(final String key) {
            hasKeyCalls.incrementAndGet();
            if (failing) {
                return Mono.error(new IllegalStateException("Unable to connect to Redis"));
            }
            return Mono.just(exists);
        }

        void setFailing(final boolean failing) {
            this.failing = failing;
        }

        void setExists(final boolean exists) {
            this.exists = exists;
        }

        int hasKeyCalls() {
            return hasKeyCalls.get();
        }
    }

    @Nested
    @DisplayName("DefaultJwtTokenValidator legacy Redis 分支")
    class ValidatorLegacyBranchTests {

        @ParameterizedTest(name = "failClosed={0} → onErrorResume 首次失败 + 短路窗口二次判定")
        @ValueSource(booleans = {false, true})
        @DisplayName("存储失败与短路窗口均按 flag 决定放行/拒绝")
        void storageFailureAndShortCircuitHonourFlag(final boolean failClosed) {
            jwtConfig.setBlacklistFailClosedWhenUnavailable(failClosed);
            FakeFailingRedisTemplate redis = new FakeFailingRedisTemplate();
            DefaultJwtTokenValidator validator =
                    new DefaultJwtTokenValidator(securityProperties, redis);
            String token = newToken();

            // 第 1 次：onErrorResume 路径（存储失败）
            StepVerifier.create(validator.isTokenBlacklisted(token))
                .expectNext(failClosed)
                .verifyComplete();
            // 第 2 次：降级短路窗口路径（不再查询存储）
            StepVerifier.create(validator.isTokenBlacklisted(token))
                .expectNext(failClosed)
                .verifyComplete();

            assertEquals(1, redis.hasKeyCalls(), "短路窗口内不应再次查询 Redis");
        }

        @Test
        @DisplayName("flag=false（默认）：存储失败 → 放行，与历史行为一致")
        void defaultFlagStillAllowsOnStorageFailure() {
            FakeFailingRedisTemplate redis = new FakeFailingRedisTemplate();
            DefaultJwtTokenValidator validator =
                    new DefaultJwtTokenValidator(securityProperties, redis);

            StepVerifier.create(validator.isTokenBlacklisted(newToken()))
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        @DisplayName("flag=true：存储失败 → 拒绝（视为已列入黑名单）")
        void failClosedDeniesOnStorageFailure() {
            jwtConfig.setBlacklistFailClosedWhenUnavailable(true);
            FakeFailingRedisTemplate redis = new FakeFailingRedisTemplate();
            DefaultJwtTokenValidator validator =
                    new DefaultJwtTokenValidator(securityProperties, redis);

            StepVerifier.create(validator.isTokenBlacklisted(newToken()))
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        @DisplayName("flag=true：validateToken 在存储不可用时整体拒绝")
        void failClosedRejectsAuthentication() {
            jwtConfig.setBlacklistFailClosedWhenUnavailable(true);
            FakeFailingRedisTemplate redis = new FakeFailingRedisTemplate();
            DefaultJwtTokenValidator validator =
                    new DefaultJwtTokenValidator(securityProperties, redis);

            StepVerifier.create(validator.validateToken(newToken()))
                .expectError()
                .verify();
        }
    }
}

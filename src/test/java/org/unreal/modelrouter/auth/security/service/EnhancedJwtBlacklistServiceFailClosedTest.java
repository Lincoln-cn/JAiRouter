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

package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EnhancedJwtBlacklistService 黑名单 fail-closed 可选行为测试（issue #118）.
 *
 * <p>覆盖 {@code handleStorageUnavailable} 与降级短路窗口两条放行路径：
 * flag=false 保持历史放行语义；flag=true 时按“在黑名单”拒绝。本地缓存命中优先。</p>
 */
@DisplayName("EnhancedJwtBlacklistService fail-closed 测试（issue #118）")
class EnhancedJwtBlacklistServiceFailClosedTest {

    private JwtConfig jwtConfig;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setBlacklistEnabled(true);
        jwtConfig.setBlacklistFailClosedWhenUnavailable(false);
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

    @ParameterizedTest(name = "failClosed={0} → onErrorResume 首次失败 + 短路窗口二次判定")
    @ValueSource(booleans = {false, true})
    @DisplayName("存储失败与短路窗口均按 flag 决定放行/拒绝")
    void storageFailureAndShortCircuitHonourFlag(final boolean failClosed) {
        jwtConfig.setBlacklistFailClosedWhenUnavailable(failClosed);
        FakeFailingRedisTemplate redis = new FakeFailingRedisTemplate();
        EnhancedJwtBlacklistService service = new EnhancedJwtBlacklistService(redis);
        service.setJwtConfig(jwtConfig);

        // 第 1 次：handleStorageUnavailable（本地缓存未命中）
        StepVerifier.create(service.isBlacklisted("token-1"))
            .expectNext(failClosed)
            .verifyComplete();
        // 第 2 次：降级短路窗口路径
        StepVerifier.create(service.isBlacklisted("token-2"))
            .expectNext(failClosed)
            .verifyComplete();

        assertEquals(1, redis.hasKeyCalls(), "短路窗口内不应再次查询 Redis");
    }

    @Test
    @DisplayName("flag=false（默认/未注入配置）：存储失败 → 放行，与历史行为一致")
    void defaultFlagStillAllowsOnStorageFailure() {
        FakeFailingRedisTemplate redis = new FakeFailingRedisTemplate();
        // 不注入 jwtConfig → 默认 false
        EnhancedJwtBlacklistService service = new EnhancedJwtBlacklistService(redis);

        StepVerifier.create(service.isBlacklisted("token-1"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    @DisplayName("flag=true：本地缓存命中仍优先拦截，不受 fail-closed 影响")
    void localCacheStillBlocksWhenFailClosed() {
        jwtConfig.setBlacklistFailClosedWhenUnavailable(true);
        FakeFailingRedisTemplate redis = new FakeFailingRedisTemplate();
        EnhancedJwtBlacklistService service = new EnhancedJwtBlacklistService(redis);
        service.setJwtConfig(jwtConfig);

        // 存储可用且命中 → 写入本地缓存
        redis.setFailing(false);
        redis.setExists(true);
        StepVerifier.create(service.isBlacklisted("token-1"))
            .expectNext(true)
            .verifyComplete();

        // 存储转为不可用后，本地缓存仍应拦截
        redis.setFailing(true);
        StepVerifier.create(service.isBlacklisted("token-1"))
            .expectNext(true)
            .verifyComplete();
    }
}

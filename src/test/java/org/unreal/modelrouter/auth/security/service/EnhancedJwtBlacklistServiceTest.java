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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EnhancedJwtBlacklistService 降级行为测试（issue #76）.
 *
 * <p>存储不可达时原实现每个请求打两条 WARN、各付一次连接超时，且降级不透明。
 * 这里锁定修复后的行为：只告警一次、短路窗口内不再查询存储、存储恢复后自动重试。</p>
 */
@DisplayName("EnhancedJwtBlacklistService 降级行为测试（issue #76）")
class EnhancedJwtBlacklistServiceTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private EnhancedJwtBlacklistService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        service = new EnhancedJwtBlacklistService(redisTemplate);
    }

    @Nested
    @DisplayName("存储不可用降级")
    class DegradationTests {

        @Test
        @DisplayName("BLK-001: 存储不可用时降级放行，且短路窗口内不再查询存储")
        void degradesAndShortCircuits() {
            when(redisTemplate.hasKey(anyString()))
                .thenReturn(Mono.error(new IllegalStateException("Unable to connect to Redis")));

            StepVerifier.create(service.isBlacklisted("token-1")).expectNext(false).verifyComplete();
            StepVerifier.create(service.isBlacklisted("token-2")).expectNext(false).verifyComplete();

            // 第二次不再触发连接尝试（原实现每次都查、每次都超时并各打两条 WARN）
            verify(redisTemplate, times(1)).hasKey(anyString());
        }

        @Test
        @DisplayName("BLK-002: 本地缓存命中的令牌不依赖存储，降级期间仍被拦截")
        void locallyCachedTokenStillBlocked() {
            // 存储可用时命中主键 → 写入本地缓存
            when(redisTemplate.hasKey("jwt:blacklist:token-1")).thenReturn(Mono.just(true));
            StepVerifier.create(service.isBlacklisted("token-1")).expectNext(true).verifyComplete();

            // 存储转为不可用后，本地缓存仍应判定为黑名单
            reset(redisTemplate);
            when(redisTemplate.hasKey(anyString()))
                .thenReturn(Mono.error(new IllegalStateException("Unable to connect to Redis")));
            StepVerifier.create(service.isBlacklisted("token-1")).expectNext(true).verifyComplete();
        }

        @Test
        @DisplayName("BLK-003: 短路窗口到期后重新查询存储并返回真实判定（恢复）")
        void retriesAfterDegradationWindow() {
            service.setDegradedRetryIntervalMillis(0L);

            when(redisTemplate.hasKey(anyString()))
                .thenReturn(Mono.error(new IllegalStateException("Unable to connect to Redis")));
            StepVerifier.create(service.isBlacklisted("token-1")).expectNext(false).verifyComplete();

            // 存储恢复：应重新查询并返回真实结果（且降级状态被清除）
            reset(redisTemplate);
            when(redisTemplate.hasKey("jwt:blacklist:token-1")).thenReturn(Mono.just(true));
            StepVerifier.create(service.isBlacklisted("token-1")).expectNext(true).verifyComplete();
            verify(redisTemplate, times(1)).hasKey(anyString());
        }

        @Test
        @DisplayName("BLK-004: 存储可用时正常返回（未降级）")
        void normalPathReturnsRedisVerdict() {
            when(redisTemplate.hasKey("jwt:blacklist:token-1")).thenReturn(Mono.just(false));
            when(redisTemplate.hasKey("jwt:blacklist:backup:token-1")).thenReturn(Mono.just(false));

            StepVerifier.create(service.isBlacklisted("token-1")).expectNext(false).verifyComplete();
            verify(redisTemplate, times(2)).hasKey(anyString());
        }
    }
}

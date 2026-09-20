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

package org.unreal.modelrouter.auth.security.checker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JwtBlacklistStorageChecker 启动检查测试（issue #76）.
 *
 * <p>检查只做事后告警：黑名单关闭时不探测；存储不可达/无客户端/探测超时都不得抛异常、
 * 不得影响启动。</p>
 */
@DisplayName("JwtBlacklistStorageChecker 启动检查测试（issue #76）")
class JwtBlacklistStorageCheckerTest {

    @Test
    @DisplayName("BLK-020: 黑名单关闭时不探测存储")
    void skipsProbeWhenBlacklistDisabled() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);

        checker(false, redisTemplate).checkBlacklistStorage();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("BLK-021: 存储不可达时只告警不抛异常")
    void warnsWithoutThrowingWhenStorageUnreachable() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(Mono.error(new IllegalStateException("Unable to connect to Redis")));

        assertDoesNotThrow(() -> checker(true, redisTemplate).checkBlacklistStorage());
    }

    @Test
    @DisplayName("BLK-022: 无 Redis 客户端时只告警不抛异常")
    void warnsWithoutThrowingWhenNoRedisClient() {
        assertDoesNotThrow(() -> checker(true, null).checkBlacklistStorage());
    }

    @Test
    @DisplayName("BLK-023: 存储可用时不抛异常")
    void passesWhenStorageAvailable() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        assertDoesNotThrow(() -> checker(true, redisTemplate).checkBlacklistStorage());
    }

    @SuppressWarnings("unchecked")
    private JwtBlacklistStorageChecker checker(final boolean blacklistEnabled,
                                               final ReactiveStringRedisTemplate redisTemplate) {
        SecurityProperties securityProperties = mock(SecurityProperties.class);
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setBlacklistEnabled(blacklistEnabled);
        when(securityProperties.getJwt()).thenReturn(jwtConfig);

        ObjectProvider<ReactiveStringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);

        return new JwtBlacklistStorageChecker(securityProperties, provider);
    }
}

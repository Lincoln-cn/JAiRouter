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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;

import java.time.Duration;

/**
 * JWT 黑名单存储可用性启动检查（issue #76）.
 *
 * <p>黑名单开启但存储不可达时，校验会降级为"仅依据本地缓存判定"——即<b>撤销/登出的令牌
 * 在存储恢复前不会被拦截</b>。该降级是既有的可用性优先取舍，但必须让运维在<b>启动阶段</b>
 * 就能看见，而不是等到第一个请求失败、或靠每请求告警刷屏才发现。</p>
 *
 * <p>本检查仅在黑名单开启（{@code jairouter.security.jwt.blacklist-enabled=true}）时执行；
 * 探测失败只告警，既不影响启动，也不改变任何校验行为。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.1
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class JwtBlacklistStorageChecker {

    /** 探测超时：与鉴权链路的短超时同数量级，避免拖慢启动。 */
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(800);

    /** 探测键：仅用于探测存储是否可读，不写入任何数据。 */
    private static final String PROBE_KEY = "jwt:blacklist:__startup_probe__";

    private final SecurityProperties securityProperties;
    private final ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider;

    public JwtBlacklistStorageChecker(
            final SecurityProperties securityProperties,
            final ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider) {
        this.securityProperties = securityProperties;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 应用启动后探测黑名单存储可用性，不可用时给出一次明确的降级告警.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkBlacklistStorage() {
        if (!securityProperties.getJwt().isBlacklistEnabled()) {
            return;
        }

        ReactiveStringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("JWT 黑名单已开启，但运行环境没有 Redis 客户端：黑名单校验将降级为"
                    + "仅依据本地缓存判定，撤销/登出的令牌可能不会被拦截");
            return;
        }

        try {
            Boolean exists = redisTemplate.hasKey(PROBE_KEY)
                    .blockOptional(PROBE_TIMEOUT)
                    .orElse(null);
            if (exists == null) {
                log.warn("JWT 黑名单存储探测在 {} ms 内未返回：校验可能降级为仅依据本地缓存判定，"
                        + "撤销/登出的令牌可能不会被拦截", PROBE_TIMEOUT.toMillis());
            } else {
                log.info("JWT 黑名单存储可用性检查通过");
            }
        } catch (Exception ex) {
            log.warn("JWT 黑名单已开启，但黑名单存储不可达：校验将降级为仅依据本地缓存判定，"
                    + "撤销/登出的令牌在存储恢复前不会被拦截。error={}", ex.getMessage());
        }
    }
}

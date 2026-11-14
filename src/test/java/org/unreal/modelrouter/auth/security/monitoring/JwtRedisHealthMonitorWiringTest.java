package org.unreal.modelrouter.auth.security.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.auth.security.cache.JwtBlacklistCacheManager;
import org.unreal.modelrouter.auth.security.config.RedisJwtCacheConfiguration;
import org.unreal.modelrouter.config.health.JwtRedisHealthProperties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link JwtRedisHealthMonitor} 的启用条件必须覆盖它的全部依赖（issue #1052）.
 *
 * <p>本类依赖三样东西，分别由两个不同的条件键提供：</p>
 * <ul>
 *   <li>{@code jairouter.security.jwt.persistence.redis.enabled} →
 *       {@code ReactiveRedisTemplate} 与 {@link RedisJwtCacheConfiguration.RedisJwtHealthChecker}；</li>
 *   <li>{@code jairouter.security.jwt.blacklist.redis.enabled} → {@link JwtBlacklistCacheManager}。</li>
 * </ul>
 *
 * <p>修复前只声明了前者，于是 {@code application-staging.yml} 那种「开 persistence 不开黑名单」的
 * 组合会让上下文启动失败：{@code No qualifying bean of type 'JwtBlacklistCacheManager' available}。
 * 本类用 {@link ApplicationContextRunner} 覆盖四种开关组合，其中「只开 persistence」正是回归用例。</p>
 */
@DisplayName("JWT Redis 健康指示器的启用条件（issue #1052）")
class JwtRedisHealthMonitorWiringTest {

    private static final String PERSISTENCE_REDIS =
            "jairouter.security.jwt.persistence.redis.enabled";

    private static final String BLACKLIST_REDIS =
            "jairouter.security.jwt.blacklist.redis.enabled";

    /**
     * 被测上下文：注册指示器本身，并以 mock 提供它的三个构造函数依赖.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtRedisHealthMonitor.class)
            .withBean(JwtRedisHealthProperties.class, JwtRedisHealthProperties::new)
            .withBean("reactiveStringRedisTemplate", ReactiveRedisTemplate.class,
                    () -> Mockito.mock(ReactiveRedisTemplate.class))
            .withBean(JwtBlacklistCacheManager.class,
                    () -> Mockito.mock(JwtBlacklistCacheManager.class))
            .withBean(RedisJwtCacheConfiguration.RedisJwtHealthChecker.class,
                    () -> Mockito.mock(RedisJwtCacheConfiguration.RedisJwtHealthChecker.class));

    @Test
    @DisplayName("两个开关都打开：指示器正常创建")
    void bothSwitchesOn_createsIndicator() {
        runner.withPropertyValues(
                        PERSISTENCE_REDIS + "=true",
                        BLACKLIST_REDIS + "=true")
                .run(context -> {
                    assertNull(context.getStartupFailure(),
                            "两个开关都开时上下文不应启动失败: " + context.getStartupFailure());
                    assertNotNull(context.getBean(JwtRedisHealthMonitor.class),
                            "依赖齐全时指示器应创建");
                });
    }

    @Test
    @DisplayName("只开 persistence.redis：指示器不创建（修复前会创建并因缺 Bean 启动失败）")
    void onlyPersistenceRedis_doesNotCreateIndicator() {
        runner.withPropertyValues(PERSISTENCE_REDIS + "=true")
                .run(context -> {
                    assertNull(context.getStartupFailure(),
                            "只开 persistence.redis 时不得启动失败: " + context.getStartupFailure());
                    assertFalse(context.containsBean("jwtRedisHealthIndicator"),
                            "缺少 JwtBlacklistCacheManager 时不应创建指示器");
                });
    }

    @Test
    @DisplayName("只开 blacklist.redis：指示器不创建（缺 redisJwtHealthChecker）")
    void onlyBlacklistRedis_doesNotCreateIndicator() {
        runner.withPropertyValues(BLACKLIST_REDIS + "=true")
                .run(context -> {
                    assertNull(context.getStartupFailure(),
                            "只开 blacklist.redis 时不得启动失败: " + context.getStartupFailure());
                    assertFalse(context.containsBean("jwtRedisHealthIndicator"),
                            "缺少健康检查器时不应创建指示器");
                });
    }

    @Test
    @DisplayName("两个开关都关闭：指示器不创建")
    void bothSwitchesOff_doesNotCreateIndicator() {
        runner.run(context -> {
            assertNull(context.getStartupFailure(),
                    "默认配置下不得启动失败: " + context.getStartupFailure());
            assertFalse(context.containsBean("jwtRedisHealthIndicator"),
                    "默认配置下不应创建指示器");
        });
    }
}

package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * v3.5.2 PR-2: {@link EndUserRateLimitConfiguration} 装配测试。
 *
 * <p>覆盖三条装配判定：默认关闭时上下文里没有相关 Bean（零行为变更）、开启且无 Redis 时回落
 * 本地计数、开启且声明分布式且存在 Redis 连接工厂时用 Redis 计数。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
@DisplayName("终端用户限流装配")
class EndUserRateLimitConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(EndUserRateLimitProperties.class, EndUserRateLimitConfiguration.class);

    @Test
    @DisplayName("默认关闭：不注册计数器与服务，其余组件不受影响")
    void disabledByDefault_registersNothing() {
        runner.run(context -> {
            assertFalse(context.containsBean("endUserRateLimitCounter"));
            assertFalse(context.containsBean("endUserRateLimitService"));
            assertTrue(context.getBeansOfType(EndUserRateLimitCounter.class).isEmpty());
        });
    }

    @Test
    @DisplayName("开启且未要求分布式：装配本地计数器与服务")
    void enabledWithoutDistributed_usesLocalCounter() {
        runner.withPropertyValues(
                        "jairouter.ratelimit.end-user.enabled=true",
                        "jairouter.ratelimit.end-user.requests-per-minute=30")
                .run(context -> {
                    final EndUserRateLimitCounter counter =
                            context.getBean(EndUserRateLimitCounter.class);
                    assertInstanceOf(LocalEndUserRateLimitCounter.class, counter);
                    assertInstanceOf(EndUserRateLimitService.class,
                            context.getBean(EndUserRateLimitService.class));
                    final EndUserRateLimitProperties properties =
                            context.getBean(EndUserRateLimitProperties.class);
                    assertEquals(30, properties.effectiveRequestsPerMinute());
                });
    }

    @Test
    @DisplayName("开启 + 声明分布式但无 Redis 连接工厂：回落本地计数器而非启动失败")
    void distributedWithoutRedis_fallsBackToLocal() {
        runner.withPropertyValues(
                        "jairouter.ratelimit.end-user.enabled=true",
                        "jairouter.ratelimit.distributed.enabled=true")
                .run(context -> {
                    assertInstanceOf(LocalEndUserRateLimitCounter.class,
                            context.getBean(EndUserRateLimitCounter.class));
                    assertTrue(context.getBeansOfType(ReactiveRedisTemplate.class).isEmpty());
                });
    }

    @Test
    @DisplayName("开启 + 分布式 + 有 Redis 连接工厂：装配 Redis 计数器（不探测连接）")
    void distributedWithRedis_usesRedisCounter() {
        final ReactiveRedisConnectionFactory connectionFactory = mock(ReactiveRedisConnectionFactory.class);
        runner.withBean(ReactiveRedisConnectionFactory.class, () -> connectionFactory)
                .withPropertyValues(
                        "jairouter.ratelimit.end-user.enabled=true",
                        "jairouter.ratelimit.distributed.enabled=true",
                        "jairouter.ratelimit.distributed.timeout-ms=80")
                .run(context -> assertInstanceOf(RedisEndUserRateLimitCounter.class,
                        context.getBean(EndUserRateLimitCounter.class)));
    }
}

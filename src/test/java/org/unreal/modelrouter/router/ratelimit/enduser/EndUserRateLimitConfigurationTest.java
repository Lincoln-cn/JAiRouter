package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * v3.5.2 PR-2: {@link EndUserRateLimitConfiguration} 装配测试。
 *
 * <p>覆盖三条装配判定：默认关闭时仍注册 Bean 但判定直接跳过（v3.8.6 起改为恒装配，
 * 以便管理页面热开）、无 Redis 时回落本地计数、声明分布式且存在 Redis 连接工厂时用 Redis 计数。</p>
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
    @DisplayName("默认关闭：仍注册计数器与服务，但判定直接跳过（供页面热开）")
    void disabledByDefault_stillRegistersButDoesNotJudge() {
        runner.run(context -> {
            assertTrue(context.containsBean("endUserRateLimitCounter"));
            assertTrue(context.containsBean("endUserRateLimitService"));

            final EndUserRateLimitProperties properties =
                    context.getBean(EndUserRateLimitProperties.class);
            assertFalse(properties.isEnabled());

            final EndUserRateLimitService service = context.getBean(EndUserRateLimitService.class);
            assertTrue(service.evaluate("key-1", null, "user-1", ServiceType.chat, false).isEmpty(),
                    "关闭时每次判定都应直接跳过：热路径行为与「Bean 不存在」等价");
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

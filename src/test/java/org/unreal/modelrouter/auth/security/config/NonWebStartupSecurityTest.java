package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 非 Web 上下文中 WebFlux 安全配置应被跳过（issue #195）。
 *
 * <p>背景：迁移 Job 用 {@code --spring.main.web-application-type=none} 启动。该模式下 Spring Boot
 * 建的是**非 Web** 应用上下文，{@code ServerHttpSecurity} 不再自动装配；而 {@code SecurityConfiguration}
 * 此前只标了 {@code @ConditionalOnProperty}、缺 {@code @ConditionalOnWebApplication}，于是它仍要装配
 * {@code securityWebFilterChain(ServerHttpSecurity)} ⇒ 上下文刷新失败 ⇒
 * {@code CompatibilitySchemaMigrator}（{@code ApplicationRunner}）**从未执行**。
 *
 * <p>本测试用的 {@code ApplicationContextRunner} 默认建的非 Web 上下文，与
 * {@code web-application-type=none} 的形态一致。若把 {@code @ConditionalOnWebApplication} 拿掉，
 * 本测试会因上下文启动失败而变红（报缺少构造器依赖）。
 *
 * <p>说明：真实进程级路径（非 Web 启动 + 迁移执行 + 跑完自退）由实跑覆盖，见 PR 说明；
 * 本测试只守「该条件注解在位」这一条，不替代进程级验证。
 */
@DisplayName("非 Web 上下文中 WebFlux 安全配置应被跳过（issue #195）")
class NonWebStartupSecurityTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfiguration.class)
            // 必须显式打开这个开关：否则类会被它自己的 @ConditionalOnProperty 跳过，
            // 测试就变成"无论有没有 @ConditionalOnWebApplication 都通过"的假绿。
            .withPropertyValues("jairouter.security.enabled=true");

    @Test
    @DisplayName("非 Web 上下文中 SecurityConfiguration 应被条件跳过，不创建安全过滤器链")
    void securityConfigurationSkippedInNonWebContext() {
        runner.run(context -> {
            assertNull(context.getStartupFailure(),
                    () -> "非 Web 上下文中 SecurityConfiguration 应被条件跳过，但上下文启动失败：" + context.getStartupFailure());

            assertEquals(0, context.getBeanNamesForType(SecurityWebFilterChain.class).length,
                    "非 Web 上下文中不应创建 SecurityWebFilterChain");
        });
    }
}

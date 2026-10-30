package org.unreal.modelrouter.router.checker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 健康检查配置（v3.5.4，#650/#651）。
 *
 * <p>一半是默认值与钳制，一半是「真实 yml 能不能绑定」的守卫：配置项写错了名字时，
 * Spring 默认会静默忽略，运维只能看到「设了没效果」。这里用严格绑定把未识别的键逼出来。</p>
 */
class HealthCheckPropertiesTest {

    @Test
    @DisplayName("HCP-001: 默认值")
    void defaults() {
        final HealthCheckProperties properties = new HealthCheckProperties();

        assertAll(
                () -> assertEquals(HealthCheckProperties.Mode.HTTP, properties.getMode(),
                        "#833：默认改为 HTTP 探活，否则进程僵死仍判健康"),
                () -> assertEquals(30_000L, properties.getIntervalMs()),
                () -> assertEquals(5_000, properties.getTimeoutMs()),
                () -> assertEquals(4, properties.getParallelism()),
                () -> assertEquals("/v1/models", properties.getHttpPath(),
                        "#833：默认探活路径是 OpenAI 兼容上游的通用端点"));
    }

    @Test
    @DisplayName("HCP-002: 越界值被钳制而不是报错")
    void clampsOutOfRangeValues() {
        final HealthCheckProperties properties = new HealthCheckProperties();

        properties.setIntervalMs(10L);
        assertEquals(1_000L, properties.effectiveIntervalMs());

        properties.setTimeoutMs(10);
        assertEquals(200, properties.effectiveTimeoutMs());
        properties.setTimeoutMs(120_000);
        assertEquals(60_000, properties.effectiveTimeoutMs());

        properties.setParallelism(0);
        assertEquals(1, properties.effectiveParallelism());
        properties.setParallelism(99);
        assertEquals(16, properties.effectiveParallelism());
    }

    @Test
    @DisplayName("HCP-003: config/router/health-check.yml 的每个键都能绑定（严格模式）")
    void shippedYamlBindsStrictly() throws IOException {
        final List<org.springframework.core.env.PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("health-check",
                        new ClassPathResource("config/router/health-check.yml"));
        assertTrue(!sources.isEmpty(), "health-check.yml 应能被解析");

        final MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        final StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                propertySources.iterator().next());

        final HealthCheckProperties bound = new Binder(ConfigurationPropertySources.get(environment))
                .bind("jairouter.health-check",
                        Bindable.of(HealthCheckProperties.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT))
                .orElseThrow(() -> new AssertionError("jairouter.health-check 未绑定成功"));

        assertEquals(HealthCheckProperties.Mode.HTTP, bound.getMode());
        assertEquals(30_000L, bound.getIntervalMs());
        assertEquals(5_000, bound.getTimeoutMs());
        assertEquals(4, bound.getParallelism());
        assertEquals("/v1/models", bound.getHttpPath());
    }
}

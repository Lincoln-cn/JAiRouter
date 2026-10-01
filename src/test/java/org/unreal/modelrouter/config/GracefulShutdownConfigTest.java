package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优雅停机配置绑定测试。
 *
 * <p>覆盖两层：
 * <ol>
 *   <li>{@code config/common/server.yml} 中存在 {@code server.shutdown=graceful}
 *       与 {@code spring.lifecycle.timeout-per-shutdown-phase}；</li>
 *   <li>通过 Spring Boot {@link Binder} 从真实 YAML 解析并绑定上述属性（值/类型正确）。</li>
 * </ol>
 *
 * <p>未覆盖：Netty 运行时在途请求/SSE 不被切断的端到端行为（需要真实 WebFlux 上下文，
 * 本仓测试基座无 {@code @SpringBootTest} 全量上下文）。Netty 语义说明见 PR。
 */
@DisplayName("优雅停机配置绑定测试")
class GracefulShutdownConfigTest {

    private static final String SERVER_YML = "config/common/server.yml";

    private static Binder loadServerYmlBinder() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("server.yml", new ClassPathResource(SERVER_YML));
        assertFalse(sources.isEmpty(), "config/common/server.yml 应能被 YamlPropertySourceLoader 解析");

        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        return new Binder(ConfigurationPropertySources.from(propertySources));
    }

    @Test
    @DisplayName("server.shutdown 应绑定为 graceful（非默认 immediate）")
    void testServerShutdownBoundToGraceful() throws Exception {
        Binder binder = loadServerYmlBinder();

        String shutdown = binder.bind("server.shutdown", String.class).orElse(null);
        assertEquals("graceful", shutdown,
                "server.shutdown 必须为 graceful，否则重启会直接切断在途 SSE 流式响应");
    }

    @Test
    @DisplayName("spring.lifecycle.timeout-per-shutdown-phase 应绑定为 30s")
    void testShutdownPhaseTimeoutBoundTo30Seconds() throws Exception {
        Binder binder = loadServerYmlBinder();

        Duration timeout = binder.bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).orElse(null);
        assertNotNull(timeout, "spring.lifecycle.timeout-per-shutdown-phase 必须显式配置");
        assertEquals(Duration.ofSeconds(30), timeout);
        assertTrue(timeout.compareTo(Duration.ZERO) > 0, "停机等待时间必须为正");
    }

    @Test
    @DisplayName("server.port 仍为 8080，与 Dockerfile EXPOSE / compose 映射一致")
    void testServerPortRemains8080() throws Exception {
        Binder binder = loadServerYmlBinder();

        Integer port = binder.bind("server.port", Integer.class).orElse(null);
        assertEquals(8080, port, "容器内监听端口必须是 8080，compose 才能映射 9900:8080");
    }

    @Test
    @DisplayName("停机超时应可被 Bindable.of(Duration.class) 正常读取（回归 Duration 转换）")
    void testTimeoutBindableAsDuration() throws Exception {
        Binder binder = loadServerYmlBinder();

        Duration timeout = binder.bind("spring.lifecycle.timeout-per-shutdown-phase",
                Bindable.of(Duration.class))
                .orElseThrow(() -> new AssertionError("应能绑定 spring.lifecycle.timeout-per-shutdown-phase"));
        assertTrue(timeout.getSeconds() >= 1, "停机等待时间至少 1 秒");
    }
}

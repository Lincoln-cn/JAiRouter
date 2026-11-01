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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HikariCP 连接池配置绑定测试（#945）。
 *
 * <p>在 #945 之前，连接池大小从未在配置面上出现过：用的是 Spring Boot 的隐式默认值（10），
 * 运维看不到、也不知道它是多少。现在 {@code config/config-service/core.yml} 里显式写出，
 * 本测试守住「它确实还在、值没被悄悄改掉」。</p>
 *
 * <p>阈值（10 / 2 / 30000 / 1800000）来自 2026-11-01 的实测结论：把池从 10 调到 32
 * 并不提升吞吐（384.7 → 368.6 RPS）、p99 反而变差，所以默认值保持 10，不作为容量旋钮。
 * 方法与数字见 {@code docs/zh/troubleshooting/performance.md}。</p>
 *
 * <p>未覆盖：真实连接池在负载下的行为（需要起服务 + 压测，不是单测能做到的事）。</p>
 */
@DisplayName("HikariCP 连接池配置绑定测试")
class HikariPoolSettingsTest {

    private static final String CORE_YML = "config/config-service/core.yml";

    private static Binder loadCoreYmlBinder() throws Exception {
        final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        final List<PropertySource<?>> sources = loader.load("core.yml", new ClassPathResource(CORE_YML));
        assertFalse(sources.isEmpty(), CORE_YML + " 应能被 YamlPropertySourceLoader 解析");

        final MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        return new Binder(ConfigurationPropertySources.from(propertySources));
    }

    @Test
    @DisplayName("连接池参数应显式声明且与实测结论一致")
    void poolSettingsAreExplicit() throws Exception {
        final HikariSettings settings = loadCoreYmlBinder()
                .bind("spring.datasource.hikari", Bindable.of(HikariSettings.class))
                .orElseThrow(() -> new AssertionError(
                        "spring.datasource.hikari 未在 " + CORE_YML + " 中声明 —— 连接池会退回隐形默认值"));

        assertEquals(10, settings.getMaximumPoolSize(),
                "maximum-pool-size 应显式写为 10；调大它并不能提升吞吐（见 #945 实测）");
        assertEquals(2, settings.getMinimumIdle(),
                "minimum-idle 不应等于 maximum-pool-size，否则空闲期也会占满连接配额");
        assertEquals(30000, settings.getConnectionTimeout(),
                "connection-timeout 应为 30 秒，与 HikariCP 默认一致");
        assertEquals(1800000, settings.getMaxLifetime(),
                "max-lifetime 应为 30 分钟，短于 PostgreSQL 默认的 idle_session_timeout 之外的中断间隔");
    }

    @Test
    @DisplayName("反空转：同一份 YAML 确实被解析（url 可读）")
    void scannerActuallyReadsTheFile() throws Exception {
        final String url = loadCoreYmlBinder().bind("spring.datasource.url", String.class).orElse(null);
        assertNotNull(url, "spring.datasource.url 应能从 " + CORE_YML + " 读到，否则上面的断言是空转");
        assertTrue(url.contains("jdbc:h2"), "默认数据源应是 H2：" + url);
    }

    /**
     * {@code spring.datasource.hikari.*} 的绑定形状。
     */
    static class HikariSettings {

        private int maximumPoolSize;
        private int minimumIdle;
        private long connectionTimeout;
        private long maxLifetime;

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(final int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(final int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(final long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(final long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }
    }
}

package org.unreal.modelrouter.persistence.jpa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源占位符绑定测试
 *
 * <p>验证 {@code core.yml} 中 {@code ${DATABASE_URL:...}} 等占位符：
 * <ul>
 *   <li>未设环境变量时取默认值（含 {@code ;} {@code :} 等特殊字符）</li>
 *   <li>设置环境变量后可覆盖</li>
 *   <li>YAML 解析不被 URL 中的 {@code :} {@code ;} 干扰</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@DisplayName("数据源占位符绑定测试")
class DatasourcePlaceholderBindingTest {

    private static final String EXPECTED_DEFAULT_URL =
            "jdbc:h2:file:./data/jairouter;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE";

    /**
     * 从 core.yml 加载属性源并绑定 DataSourceProperties
     */
    private DataSourceProperties bindProperties(Map<String, Object> envOverrides) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();

        // 注入环境变量模拟
        if (!envOverrides.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("test-env", envOverrides));
        }

        // 加载 core.yml
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        var propertySources = loader.load("core.yml", new ClassPathResource("config/config-service/core.yml"));
        for (var ps : propertySources) {
            environment.getPropertySources().addLast(ps);
        }

        Binder binder = Binder.get(environment);
        return binder.bind("spring.datasource", Bindable.of(DataSourceProperties.class))
                .orElseGet(DataSourceProperties::new);
    }

    @Test
    @DisplayName("未设环境变量时，URL/username/password 取默认值")
    void defaults_whenNoEnvVars() throws Exception {
        DataSourceProperties props = bindProperties(Map.of());

        assertEquals(EXPECTED_DEFAULT_URL, props.getUrl(),
                "默认 URL 应逐字等于原硬编码值（含 ; 与 : 特殊字符）");
        assertEquals("sa", props.getUsername(), "默认 username 应为 sa");
        // password 默认为空字符串
        assertTrue(props.getPassword() == null || props.getPassword().isEmpty(),
                "默认 password 应为空");
    }

    @Test
    @DisplayName("设置 DATABASE_URL 环境变量后可覆盖默认值")
    void override_databaseUrl() throws Exception {
        String customUrl = "jdbc:postgresql://localhost:5432/jairouter";
        DataSourceProperties props = bindProperties(Map.of("DATABASE_URL", customUrl));

        assertEquals(customUrl, props.getUrl(), "环境变量 DATABASE_URL 应覆盖默认 URL");
    }

    @Test
    @DisplayName("设置 DATABASE_USERNAME / DATABASE_PASSWORD 后可覆盖")
    void override_usernamePassword() throws Exception {
        DataSourceProperties props = bindProperties(Map.of(
                "DATABASE_USERNAME", "jairouter",
                "DATABASE_PASSWORD", "secret-pw"));

        assertEquals("jairouter", props.getUsername(), "环境变量 DATABASE_USERNAME 应覆盖默认值");
        assertEquals("secret-pw", props.getPassword(), "环境变量 DATABASE_PASSWORD 应覆盖默认值");
    }

    @Test
    @DisplayName("driver-class-name 未配置时为 null（Spring 按 URL 自动推断）")
    void driverClassName_notHardcoded() throws Exception {
        DataSourceProperties props = bindProperties(Map.of());

        // 本 PR 移除了硬编码的 driver-class-name，验证它确实不在配置中
        assertNull(props.getDriverClassName(),
                "driver-class-name 不应在配置中硬编码，由 Spring 按 URL 推断");
    }

    @Test
    @DisplayName("YAML 中含 : 和 ; 的占位符默认值解析正确")
    void yamlPlaceholder_withSpecialChars_parsesCorrectly() throws Exception {
        // 验证 YAML 解析不被 jdbc:h2:file:...;DB_CLOSE_DELAY=-1;... 中的特殊字符干扰
        DataSourceProperties props = bindProperties(Map.of());

        String url = props.getUrl();
        assertNotNull(url, "URL 不应为 null");
        assertTrue(url.startsWith("jdbc:h2:"), "URL 应以 jdbc:h2: 开头，实际: " + url);
        assertTrue(url.contains("DB_CLOSE_DELAY=-1"), "URL 应包含 DB_CLOSE_DELAY=-1");
        assertTrue(url.contains("MODE=MySQL"), "URL 应包含 MODE=MySQL");
        assertTrue(url.contains("DATABASE_TO_UPPER=FALSE"), "URL 应包含 DATABASE_TO_UPPER=FALSE");
    }
}

package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫：不带 profile 启动（默认 profile）时 API Key 认证必须处于开启状态。
 *
 * <p>历史缺陷：{@code config/security/security-base.yml} 在 {@code api-key} 块里写死
 * {@code enabled: false}，而它在 {@code application.yml} 中后于
 * {@code config/auth/api-key.yml} 导入，按 {@code spring.config.import} 的「后导入者优先」
 * 语义把 {@code enabled: true} 覆盖掉 —— 结果是 {@code java -jar} 默认启动后所有
 * {@code X-API-Key} 请求都返回 {@code 401 AUTH_MISSING}，而 dev/prod/staging 因为显式写了
 * {@code enabled: true} 不受影响，问题因此长期藏在默认路径里。
 *
 * <p>本测试不读源码文本，而是让 Spring 自己按 {@code application.yml} 的导入顺序解析一遍，
 * 断言最终生效值；另有两条反向守卫防止测试空转：
 * <ul>
 *   <li>{@link #securityBaseMustNotDeclareApiKeySwitch()}：基础模板不得再声明该键；</li>
 *   <li>{@link #laterImportStillWins()}：同一个解析手法下，后导入的 {@code false} 确实能覆盖
 *       {@code true}（把旧缺陷还原到临时文件上必须变红）。</li>
 * </ul>
 */
class ApiKeyEnabledResolutionTest {

    private static final String API_KEY_ENABLED = "jairouter.security.api-key.enabled";

    @Test
    @DisplayName("按 application.yml 的顺序导入全部 classpath 配置后，api-key.enabled 生效值为 true")
    void defaultProfileKeepsApiKeyEnabled() throws IOException {
        List<String> imports = classpathImports();
        assertTrue(imports.size() > 20, "导入列表看起来不像真实配置: " + imports.size());
        assertTrue(imports.contains("classpath:config/auth/api-key.yml"), "缺少 api-key.yml 导入: " + imports);

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.import=" + String.join(",", imports))
                .run(context -> assertEquals("true", context.getEnvironment().getProperty(API_KEY_ENABLED),
                        "默认 profile 下 API Key 认证被关闭了 —— 后导入的配置覆盖了 "
                                + "config/auth/api-key.yml 的 enabled: true，java -jar 会让所有 X-API-Key 请求 401"));
    }

    @Test
    @DisplayName("安全基础模板不再声明 api-key 开关（防止旧写法被加回来）")
    void securityBaseMustNotDeclareApiKeySwitch() {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources;
        try {
            sources = loader.load("security-base", new ClassPathResource("config/security/security-base.yml"));
        } catch (IOException ex) {
            throw new IllegalStateException("config/security/security-base.yml 读取失败", ex);
        }
        assertFalse(sources.isEmpty(), "config/security/security-base.yml 未被加载");
        for (PropertySource<?> source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                assertFalse(API_KEY_ENABLED.equals(name),
                        "security-base.yml 又声明了 " + API_KEY_ENABLED
                                + "：它后于 api-key.yml 导入，会静默覆盖认证开关");
            }
        }
    }

    @Test
    @DisplayName("该断言有检测力：后导入的 enabled: false 会被解析为 false")
    void laterImportStillWins(@TempDir final Path tmp) throws IOException {
        Path apiKey = tmp.resolve("api-key.yml");
        Path base = tmp.resolve("security-base.yml");
        Files.writeString(apiKey, "jairouter:\n  security:\n    api-key:\n      enabled: true\n");
        Files.writeString(base, "jairouter:\n  security:\n    api-key:\n      enabled: false\n");

        String imports = "spring.config.import=" + fileUrl(apiKey) + "," + fileUrl(base);

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(imports)
                .run(context -> assertEquals("false", context.getEnvironment().getProperty(API_KEY_ENABLED),
                        "解析语义若变成「先导入者优先」，本类的主断言就失去检测力"));
    }

    private static String fileUrl(final Path path) {
        return "file:" + path.toAbsolutePath().toString().replace('\\', '/');
    }

    @SuppressWarnings("unchecked")
    private static List<String> classpathImports() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));
        assertFalse(sources.isEmpty(), "application.yml 未被加载");

        List<String> imports = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (!name.startsWith("spring.config.import[")) {
                    continue;
                }
                Object value = enumerable.getProperty(name);
                if (value != null && value.toString().startsWith("classpath:")) {
                    imports.add(value.toString());
                }
            }
        }
        return imports;
    }
}

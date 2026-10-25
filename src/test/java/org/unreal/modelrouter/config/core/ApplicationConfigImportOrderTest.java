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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #634 的行为守卫：{@code spring.config.import} 里「后导入者优先」，
 * 因此外部挂载配置必须排在 classpath 之后，才能真正覆盖同名键。
 */
class ApplicationConfigImportOrderTest {

    private static final String FILE_PREFIX = "optional:file:";

    @Test
    @DisplayName("application.yml 的外部导入必须排在所有 classpath 导入之后")
    void externalImportsComeLast() throws IOException {
        List<String> imports = declaredImports();
        assertTrue(imports.size() > 20, "导入列表看起来不像真实配置: " + imports.size());

        int lastClasspath = -1;
        for (int i = 0; i < imports.size(); i++) {
            if (imports.get(i).startsWith("classpath:")) {
                lastClasspath = i;
            }
        }
        assertTrue(lastClasspath >= 0, "应当存在 classpath 导入");

        for (int i = 0; i < imports.size(); i++) {
            if (imports.get(i).startsWith(FILE_PREFIX)) {
                assertTrue(i > lastClasspath,
                        "外部导入 '" + imports.get(i) + "' 排在第 " + i + " 位，必须先于不了任何 classpath 导入"
                                + "（最后一条 classpath 在 " + lastClasspath + "）——否则挂载配置会被静默覆盖");
            }
        }

        assertTrue(imports.contains("optional:file:/app/config/application.yml"),
                "外部 application.yml 必须在导入列表里: " + imports);
        assertTrue(imports.contains("optional:file:/app/config/auth/jwt.yml"));
        // 外部挂载的 router/services.yml 也必须在列（Docker/k8s 的挂载点）
        assertTrue(imports.stream().anyMatch(entry -> entry.contains("/app/config/router/services.yml")));
    }

    @Test
    @DisplayName("导入列表里后声明的文件确实覆盖先声明的同名键（语义实证）")
    void laterImportWins(@TempDir final Path tmp) throws IOException {
        Path first = tmp.resolve("first.yml");
        Path second = tmp.resolve("second.yml");
        Files.writeString(first, "probe:\n  value: from-first\n");
        Files.writeString(second, "probe:\n  value: from-second\n");

        String imports = "spring.config.import=" + fileUrl(first) + "," + fileUrl(second);

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(imports)
                .run(context -> assertEquals("from-second", context.getEnvironment().getProperty("probe.value"),
                        "后导入者优先：这就是外部挂载必须排在 classpath 之后的原因"));
    }

    private static String fileUrl(final Path path) {
        return "file:" + path.toAbsolutePath().toString().replace('\\', '/');
    }

    @SuppressWarnings("unchecked")
    private static List<String> declaredImports() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));
        assertTrue(!sources.isEmpty(), "application.yml 未被加载");

        List<String> imports = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (name.startsWith("spring.config.import[")) {
                    Object value = enumerable.getProperty(name);
                    if (value != null) {
                        imports.add(value.toString());
                    }
                }
            }
        }
        return imports;
    }
}

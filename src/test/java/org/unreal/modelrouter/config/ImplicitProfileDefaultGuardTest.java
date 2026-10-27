package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫测试：随包发布的基础配置不得把 dev 设为默认激活的 profile。
 *
 * <p>背景（#666）：{@code src/main/resources/config/common/server.yml} 曾写着
 * {@code spring.profiles.active: dev}，而该文件会被 {@code application.yml} 的
 * {@code spring.config.import} 无条件导入。结果是任何未显式指定 profile 的启动
 * （{@code java -jar}、IDE 直接运行、{@code mvnw spring-boot:run}）都按 dev 运行，
 * 连带启用内置测试 API Key、文档里的固定管理员口令与 DEBUG 日志。
 *
 * <p>本测试把「基础配置里不出现 {@code active: dev}」钉成可执行约束；同时用
 * {@link #devProfileStillCarriesTheDevConveniences()} 做正向对照，防止「文件读不到
 * 就等于通过」的静默失效。
 */
@DisplayName("#666 隐式 dev 默认守卫测试")
class ImplicitProfileDefaultGuardTest {

    private static final Path MAIN_RESOURCES = Paths.get("src/main/resources");
    private static final Path DEV_PROFILE_CONFIG = MAIN_RESOURCES.resolve("application-dev.yml");
    private static final String SERVER_CONFIG = "config/common/server.yml";

    @Test
    @DisplayName("随包基础配置不得默认激活 dev profile")
    void shippedConfigsDoNotActivateDevByDefault() throws IOException {
        List<Path> shipped = shippedConfigs();

        // 反空转：扫描面必须真的覆盖到随包配置，否则「找不到 active: dev」没有意义
        assertTrue(shipped.size() >= 20, "随包配置扫描面过小，疑似枚举逻辑失效：" + shipped.size());
        assertTrue(containsFile(shipped, SERVER_CONFIG), "扫描面必须包含 " + SERVER_CONFIG);
        assertTrue(containsFile(shipped, "application.yml"), "扫描面必须包含 application.yml");
        assertTrue(containsFile(shipped, "application-prod.yml"), "扫描面必须包含 application-prod.yml");

        for (Path path : shipped) {
            for (String line : effectiveLines(path)) {
                assertFalse(line.matches("active:\\s*dev"),
                        path + " 把 dev 设成了默认激活 profile —— 未显式指定 profile 的启动会连带启用内置测试凭据（#666）");
            }
        }
    }

    @Test
    @DisplayName("server.yml 不再携带 profiles.active 键")
    void serverConfigHasNoProfilesActiveKey() throws IOException {
        Path server = MAIN_RESOURCES.resolve(SERVER_CONFIG);
        assertTrue(Files.exists(server), "基础配置 " + SERVER_CONFIG + " 应存在");

        List<String> lines = effectiveLines(server);
        // 反空转：确认读到的确实是 server.yml（而不是空文件或别的东西）
        assertTrue(lines.contains("shutdown: graceful"), "未读到 server.yml 的有效内容，疑似路径或解析失效");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("port:")), "未读到 server.port");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            assertFalse(line.matches("active:\\s*(dev|staging|prod|test)"),
                    SERVER_CONFIG + " 第 " + (i + 1) + " 个有效行仍设置默认 profile：" + line);
        }
    }

    @Test
    @DisplayName("基础配置里的 spring.profiles.active 只能是占位符引用")
    void springProfilesActiveIsOnlyReferencedAsPlaceholder() throws IOException {
        int references = 0;
        for (Path path : shippedConfigs()) {
            for (String line : effectiveLines(path)) {
                if (!line.contains("spring.profiles.active")) {
                    continue;
                }
                references++;
                assertTrue(line.matches(".*\\$\\{spring\\.profiles\\.active[^}]*}.*"),
                        path + " 以硬编码方式设置 spring.profiles.active：" + line
                                + "；只允许 ${spring.profiles.active:...} 形式的读取");
            }
        }
        assertTrue(references >= 1, "未在随包配置中找到任何 spring.profiles.active 引用，疑似扫描面失效");
    }

    @Test
    @DisplayName("dev profile 仍保留本地开发便利（正向对照）")
    void devProfileStillCarriesTheDevConveniences() throws IOException {
        assertTrue(Files.exists(DEV_PROFILE_CONFIG), "application-dev.yml 应存在");
        List<String> devLines = effectiveLines(DEV_PROFILE_CONFIG);

        assertTrue(devLines.stream().anyMatch(l -> l.contains("DEV_JWT_SECRET")),
                "dev profile 应仍提供固定的开发 JWT 密钥兜底，否则本地启动体验退化");
        assertTrue(devLines.stream().anyMatch(l -> l.startsWith("org.unreal.modelrouter: DEBUG")),
                "dev profile 应仍把应用日志开到 DEBUG，否则本地排查体验退化");
    }

    private static boolean containsFile(List<Path> paths, String suffix) {
        return paths.stream().anyMatch(p -> p.toString().replace('\\', '/').endsWith(suffix));
    }

    /**
     * 「随包配置」= {@code classpath:config/**}{@code /*.yml} 全部 + {@code application*.yml} 中除
     * {@code application-dev.yml} 者（dev 是本地开发专用，允许携带测试凭据）。
     */
    private static List<Path> shippedConfigs() throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(MAIN_RESOURCES.resolve("config"))) {
            result.addAll(stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml"))
                    .sorted()
                    .toList());
        }
        try (Stream<Path> stream = Files.list(MAIN_RESOURCES)) {
            result.addAll(stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("application") && name.endsWith(".yml")
                                && !name.equals("application-dev.yml");
                    })
                    .sorted()
                    .toList());
        }
        return result;
    }

    /**
     * 只保留 YAML 有效行：去掉空行与整行注释，并去掉缩进。
     * 注释里提到 {@code active: dev} 属于历史说明，不构成配置生效。
     */
    private static List<String> effectiveLines(Path path) throws IOException {
        List<String> effective = new ArrayList<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = raw.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            effective.add(trimmed);
        }
        return effective;
    }
}

/*
 * Copyright 2024 JAiRouter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #677 守卫测试：Dockerfile 导出的每个环境变量都必须有「读它的地方」。
 *
 * <p>缺陷背景：镜像里 {@code ENV JWT_ALGORITHM} / {@code ENV JWT_EXPIRATION_MINUTES} /
 * {@code ENV REDIS_TIMEOUT} / {@code ENV ADMIN_API_KEY} / {@code ENV USER_API_KEY} 曾经都只导出、
 * 无人读取（随包配置里既没有 {@code ${NAME:…}} 占位符，属性前缀也对不上）⇒ 运维设置了却毫无效果，
 * 日志也不会提示失败。</p>
 *
 * <p>本测试把「导出」与「读取」绑在一起，三种合法形态：</p>
 * <ol>
 *   <li>yml 占位符：{@code ${NAME:默认值}}（例如 {@code REDIS_HOST} / {@code JWT_SECRET}）；</li>
 *   <li>Spring 宽松绑定：名字能映射到真实属性（本仓统一用 {@code JAIROUTER_*} 前缀，另加
 *       {@code SPRING_PROFILES_ACTIVE} / {@code SERVER_PORT}）；</li>
 *   <li>容器自身的 shell 变量：{@code JAVA_OPTS}（由 {@code ENTRYPOINT} 展开）。</li>
 * </ol>
 *
 * <p>不在上述范围的名字一旦出现在 Dockerfile 里，本测试立即变红 —— 要么在随包配置里补占位符，
 * 要么把这个 ENV 删掉，不允许「导出了但没人读」的中间态。</p>
 *
 * <p><b>注意：</b>{@code target/classes} 里可能有已删源文件的残留副本，<b>先 {@code mvn clean}</b>
 * 再看本测试的结果（资源插件只拷贝、不删除）。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#677 Dockerfile 导出的环境变量必须有绑定")
class DockerfileEnvBindingGuardTest {

    /** 仓库根：surefire 的工作目录就是模块根。 */
    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();

    private static final Path DOCKERFILE = REPO_ROOT.resolve("Dockerfile");

    private static final Path RESOURCES = REPO_ROOT.resolve(Paths.get("src", "main", "resources"));

    /** 由 Spring 宽松绑定或容器 shell 消费的名字（不需要 yml 占位符）。 */
    private static final Set<String> RELAXED_OR_SHELL = Set.of(
            "JAVA_OPTS",
            "SPRING_PROFILES_ACTIVE",
            "SERVER_PORT");

    /** 宽松绑定前缀：{@code JAIROUTER_SECURITY_JWT_ENABLED} → {@code jairouter.security.jwt.enabled}。 */
    private static final String RELAXED_PREFIX = "JAIROUTER_";

    private static final Pattern ENV_LINE = Pattern.compile("^ENV\\s+([A-Za-z_][A-Za-z0-9_]*)=", Pattern.MULTILINE);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)");

    @Test
    @DisplayName("DOCKERFILE-677-001 每个 ENV 要么有 yml 占位符，要么走宽松绑定，要么是 shell 变量")
    void everyExportedEnvNameIsConsumed() throws IOException {
        final Set<String> exported = exportedEnvNames();
        final Set<String> placeholders = ymlPlaceholderNames();

        final List<String> orphans = exported.stream()
                .filter(name -> !RELAXED_OR_SHELL.contains(name))
                .filter(name -> !name.startsWith(RELAXED_PREFIX))
                .filter(name -> !placeholders.contains(name))
                .sorted()
                .collect(Collectors.toList());

        assertThat(orphans)
                .as("这些 ENV 在随包配置里找不到任何 ${NAME:…} 占位符，也没有 JAIROUTER_ 前缀/宽松绑定映射，"
                        + "设置了也不会生效（要么补占位符，要么删掉 ENV）")
                .isEmpty();
    }

    @Test
    @DisplayName("DOCKERFILE-677-002 反空转：解析器必须真的读到 ENV 与占位符")
    void scannersActuallyDetectThings() throws IOException {
        final Set<String> exported = exportedEnvNames();
        final Set<String> placeholders = ymlPlaceholderNames();

        assertThat(exported)
                .as("Dockerfile 里应能解析出多个 ENV")
                .hasSizeGreaterThanOrEqualTo(8)
                .contains("JWT_ALGORITHM", "JWT_EXPIRATION_MINUTES", "REDIS_HOST", "SPRING_PROFILES_ACTIVE");
        assertThat(placeholders)
                .as("随包 yml 里应能解析出多个 ${…} 占位符")
                .contains("JWT_SECRET", "REDIS_HOST", "JWT_EXPIRATION_MINUTES");
        assertThat(Files.exists(DOCKERFILE)).as("仓库根应存在 Dockerfile").isTrue();
    }

    @Test
    @DisplayName("DOCKERFILE-677-003 JWT 两个旋钮必须在随包 jwt.yml 里可覆盖")
    void jwtKnobsAreOverridableFromShippedConfig() throws IOException {
        final String jwtYml = Files.readString(RESOURCES.resolve(Paths.get("config", "auth", "jwt.yml")),
                StandardCharsets.UTF_8);

        assertThat(jwtYml)
                .as("镜像导出 JWT_ALGORITHM / JWT_EXPIRATION_MINUTES，随包配置必须真的读它们（#677）")
                .contains("${JWT_ALGORITHM:")
                .contains("${JWT_EXPIRATION_MINUTES:");
    }

    private Set<String> exportedEnvNames() throws IOException {
        assertThat(Files.exists(DOCKERFILE)).as("仓库根应存在 Dockerfile").isTrue();
        final String dockerfile = Files.readString(DOCKERFILE, StandardCharsets.UTF_8);
        final Set<String> names = new LinkedHashSet<>();
        final Matcher matcher = ENV_LINE.matcher(dockerfile);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private Set<String> ymlPlaceholderNames() throws IOException {
        assertThat(Files.isDirectory(RESOURCES)).as("随包配置目录应存在").isTrue();
        final Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (final Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                final Matcher matcher = PLACEHOLDER.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }
}

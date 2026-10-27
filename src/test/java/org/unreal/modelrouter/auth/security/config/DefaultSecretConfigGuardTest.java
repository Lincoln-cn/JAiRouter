package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 随包配置不得再携带“公开可用的默认凭据”的守卫测试（issue #664）。
 *
 * <p>被守护的性质（三条，缺一不可）：</p>
 * <ol>
 *   <li><b>基础配置不再回落到公开字面量</b>：{@code config/auth/jwt.yml} 的 JWT 密钥与
 *       admin 口令都改为 {@code ${random.uuid}} 兜底；随包配置（除 {@code application-dev.yml}）
 *       里不得再出现历史公开值 {@code ThisIsADefaultSecretKeyForDevOnly12345678} 与
 *       {@code ChangeMeOnFirstStartup123456}。</li>
 *   <li><b>仍是“可启动”的兜底</b>：占位符必须真能解析成值（写 {@code ${random.uuid}} 而不是
 *       留空），否则非 prod 环境会因为缺环境变量而起不来 —— 这是本次改动的关键权衡，
 *       必须有断言守着，不能只靠注释。</li>
 *   <li><b>dev profile 的便利不变</b>：{@code application-dev.yml} 仍提供本地开发用的固定口令，
 *       否则 e2e / 截图脚本 / 文档里的“默认登录”会集体失效（正向对照，防“找不到就等于通过”）。</li>
 * </ol>
 */
@DisplayName("默认凭据配置守卫（#664）")
class DefaultSecretConfigGuardTest {

    /** 历史公开 JWT 密钥（曾写在基础配置里，可被任何人用来伪造令牌）。 */
    private static final String LEGACY_PUBLIC_JWT_SECRET = "ThisIsADefaultSecretKeyForDevOnly12345678";

    /** 历史公开管理员口令（曾写在基础配置里，可被任何人用来登录）。 */
    private static final String LEGACY_PUBLIC_ADMIN_PASSWORD = "ChangeMeOnFirstStartup123456";

    private static final String RESOURCES = "src/main/resources";

    private static final String JWT_CONFIG = RESOURCES + "/config/auth/jwt.yml";

    private static final String DEV_PROFILE_CONFIG = RESOURCES + "/application-dev.yml";

    /**
     * 裸 {@code ADMIN_PASSWORD}（不算 {@code INITIAL_ADMIN_PASSWORD} 的子串）。
     *
     * <p>#674：管理员口令只认 {@code INITIAL_ADMIN_PASSWORD}，旧的 {@code ADMIN_PASSWORD}
     * 别名会让门闩以为口令已设置、而绑定实际用的是随机生成值 ⇒ 必须确保它不再出现在随包配置里。</p>
     */
    private static final Pattern BARE_ADMIN_PASSWORD = Pattern.compile("(?<!INITIAL_)ADMIN_PASSWORD");

    /** 已移除的 JWT 密钥兼容变量名（#674）。 */
    private static final List<String> REMOVED_JWT_SECRET_ALIASES = List.of(
            "DEV_JWT_SECRET", "STAGING_JWT_SECRET", "PROD_JWT_SECRET");

    @Test
    @DisplayName("随包配置（除 dev）不得再出现公开 JWT 密钥与公开管理员口令")
    void shippedConfigHasNoPublicCredentialLiterals() throws IOException {
        final List<Path> shipped = shippedConfigs();
        assertTrue(shipped.size() >= 20,
                "反空转：随包配置至少应有 20 个 yml，实际 " + shipped.size());

        for (final Path file : shipped) {
            final String text = effectiveLines(file);
            assertFalse(text.contains(LEGACY_PUBLIC_JWT_SECRET),
                    file + " 仍含公开 JWT 密钥字面量（#664）");
            assertFalse(text.contains(LEGACY_PUBLIC_ADMIN_PASSWORD),
                    file + " 仍含公开管理员口令字面量（#664）");
        }
    }

    @Test
    @DisplayName("基础配置的密钥与口令兜底必须随机生成，且不能留空")
    void baseConfigFallsBackToRandomValues() throws IOException {
        final String jwtConfig = effectiveLines(Path.of(JWT_CONFIG));

        assertTrue(jwtConfig.contains("secret: \"${JWT_SECRET:${random.uuid}}\""),
                "jwt.yml 的 secret 必须是 ${JWT_SECRET:${random.uuid}}：缺环境变量时随机生成"
                        + "（留空会让非 prod 环境起不来，写公开字面量会让任何人伪造令牌）");
        assertTrue(jwtConfig.contains("password: \"{noop}${INITIAL_ADMIN_PASSWORD:${random.uuid}}\""),
                "jwt.yml 的 admin password 必须是 ${INITIAL_ADMIN_PASSWORD:${random.uuid}}");
        assertNotEquals(-1, jwtConfig.indexOf("${random.uuid}"),
                "sanity：占位符必须真的写在有效行里");
    }

    @Test
    @DisplayName("dev profile 仍提供本地开发用的固定凭据（正向对照）")
    void devProfileKeepsLocalConvenience() throws IOException {
        final String dev = effectiveLines(Path.of(DEV_PROFILE_CONFIG));

        assertTrue(dev.contains(LEGACY_PUBLIC_ADMIN_PASSWORD),
                "application-dev.yml 必须仍含本地开发用的固定管理员口令，否则 e2e/截图脚本失效");
        assertTrue(dev.contains("dev-secret-key-for-testing-only-32-characters-minimum"),
                "application-dev.yml 必须仍提供 dev 的 JWT 密钥兜底字面量（#674 后不再有别名）");
    }

    @Test
    @DisplayName("扫描面覆盖基础配置与环境配置，且不包含 dev profile")
    void scanSurfaceCoversBaseConfig() throws IOException {
        final List<Path> shipped = shippedConfigs();
        final List<String> names = shipped.stream().map(p -> p.toString().replace('\\', '/')).toList();

        assertTrue(names.contains("src/main/resources/application.yml"),
                "扫描面必须包含 application.yml");
        assertTrue(names.contains("src/main/resources/application-prod.yml"),
                "扫描面必须包含 application-prod.yml");
        assertTrue(names.contains("src/main/resources/config/auth/jwt.yml"),
                "扫描面必须包含 config/auth/jwt.yml");
        assertFalse(names.contains("src/main/resources/application-dev.yml"),
                "dev profile 是本地开发便利面，必须排除在扫描面之外");
    }

    @Test
    @DisplayName("随包配置里不得再出现已移除的凭据别名（#674）")
    void removedCredentialAliasesAreGone() throws IOException {
        final List<Path> all = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(Path.of(RESOURCES))) {
            tree.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .forEach(all::add);
        }
        assertTrue(all.size() >= 20,
                "扫描面过小（" + all.size() + " 个 yml），疑似扫描逻辑失效");
        for (final Path file : all) {
            final String text = effectiveLines(file);
            for (final String alias : REMOVED_JWT_SECRET_ALIASES) {
                assertFalse(text.contains(alias),
                        file + " 仍含已移除的兼容变量名 " + alias + "（#674：只认 JWT_SECRET）");
            }
            for (final String line : text.split("\n")) {
                assertFalse(BARE_ADMIN_PASSWORD.matcher(line).find(),
                        file + " 仍含裸 ADMIN_PASSWORD（#674：只认 INITIAL_ADMIN_PASSWORD）");
            }
        }
    }

    /**
     * 随包配置 = {@code src/main/resources/config/**} 全部 yml + {@code src/main/resources} 下
     * 除 {@code application-dev.yml} 之外的 {@code application*.yml}。
     */
    private static List<Path> shippedConfigs() throws IOException {
        final List<Path> result = new ArrayList<>();
        try (Stream<Path> configTree = Files.walk(Path.of(RESOURCES, "config"))) {
            configTree.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .forEach(result::add);
        }
        try (Stream<Path> profiles = Files.list(Path.of(RESOURCES))) {
            profiles.filter(Files::isRegularFile)
                    .filter(p -> {
                        final String name = p.getFileName().toString();
                        return name.startsWith("application") && name.endsWith(".yml")
                                && !name.equals("application-dev.yml");
                    })
                    .sorted()
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * 只保留 YAML 有效行（丢掉空行与整行注释）。
     *
     * <p>必须先做这一步：注释里解释历史缺陷时会提到被禁的字面量，直接扫原文会假红。</p>
     */
    private static String effectiveLines(final Path file) {
        try {
            final StringBuilder sb = new StringBuilder();
            for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                final String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    sb.append(trimmed).append('\n');
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #676 守卫：{@code jairouter.security.jwt.persistence} 与 {@code ...jwt.blacklist} 只能由
 * <em>一个</em> 随包 yml 定义。
 *
 * <p>背景：这两棵子树曾在 {@code config/auth/jwt.yml} 与 {@code config/security/persistence-base.yml}
 * 各写一份且取值不同。{@code application.yml} 先导入前者、后导入后者，按
 * {@code spring.config.import} 的「后导入者优先」语义，前者的值被整段静默覆盖 ——
 * 读者按模块化配置去改 {@code jwt.yml}，改的是一个不生效的文件。</p>
 *
 * <p>断言写成「定义者集合恰好等于 {@code [config/security/persistence-base.yml]}」而不是
 * 「包含于」，因为两个方向都要守：多一个文件定义（重新复制一份）会红，
 * 唯一那份被删掉（没人再定义这些键）也会红。</p>
 */
@DisplayName("#676 JWT 持久化配置单一定义守卫测试")
class JwtPersistenceConfigSingleSourceGuardTest {

    /** 允许且仅允许的定义位置（相对 {@code src/main/resources}，统一使用 / 分隔）。 */
    private static final String OWNER = "config/security/persistence-base.yml";

    /** profile 文件里允许出现的持久化覆盖键：只允许「开关」，不允许重写清理策略等实质取值。 */
    private static final List<String> PROFILE_OVERRIDE_KEYS = List.of("enabled", "redis");

    @Test
    @DisplayName("jwt.persistence 的基础配置只在 persistence-base.yml 定义")
    void persistenceTreeHasSingleDefinitionInBaseConfig() {
        assertEquals(List.of(OWNER), filesDefining("persistence", false));
    }

    @Test
    @DisplayName("jwt.blacklist 的基础配置只在 persistence-base.yml 定义")
    void blacklistTreeHasSingleDefinitionInBaseConfig() {
        assertEquals(List.of(OWNER), filesDefining("blacklist", false));
    }

    @Test
    @DisplayName("profile 文件只能覆盖开关，不得整段复制持久化配置")
    void profileOverridesStickToSwitches() {
        final List<String> offenders = new ArrayList<>();
        for (Path file : shippedYamlFiles()) {
            if (!isProfileFile(file)) {
                continue;
            }
            final Map<String, Object> jwt = jwtSection(load(file));
            if (jwt == null) {
                continue;
            }
            final Map<String, Object> persistence = asMap(jwt.get("persistence"));
            if (persistence == null || persistence.isEmpty()) {
                continue;
            }
            for (String key : persistence.keySet()) {
                if (!PROFILE_OVERRIDE_KEYS.contains(key)) {
                    offenders.add(relativeName(file) + " 覆盖了 jwt.persistence." + key);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "profile 文件只允许覆盖 " + PROFILE_OVERRIDE_KEYS + "，实际： " + offenders);
    }

    @Test
    @DisplayName("持久化的生效值仍是每天 02:00、保留 30 天、批量 1000")
    void effectivePersistenceValuesArePinned() {
        final Path owner = Path.of("src/main/resources").resolve(OWNER);
        assertTrue(Files.exists(owner), "找不到 " + OWNER);

        final Map<String, Object> jwt = jwtSection(load(owner));
        assertNotNull(jwt, OWNER + " 必须提供 jairouter.security.jwt");
        final Map<String, Object> persistence = asMap(jwt.get("persistence"));
        assertNotNull(persistence, OWNER + " 必须提供 jwt.persistence");

        assertEquals(Boolean.FALSE, persistence.get("enabled"), "随包生效的 persistence.enabled 是 false");
        final Map<String, Object> cleanup = asMap(persistence.get("cleanup"));
        assertNotNull(cleanup, "persistence.cleanup 必须存在");
        assertEquals("0 0 2 * * ?", cleanup.get("schedule"));
        assertEquals(30, cleanup.get("retention-days"));
        assertEquals(1000, cleanup.get("batch-size"));
    }

    @Test
    @DisplayName("扫描面覆盖整棵随包配置（反空转）")
    void scanSurfaceCoversShippedConfig() {
        final List<Path> files = shippedYamlFiles();
        assertTrue(files.size() >= 20, "应扫描到至少 20 个随包 yml，实际 " + files.size());
        final List<String> relative = new ArrayList<>();
        files.forEach(file -> relative.add(relativeName(file)));
        assertTrue(relative.contains(OWNER), "扫描面必须包含 " + OWNER);
        assertTrue(relative.contains("application.yml"), "扫描面必须包含 application.yml");
        assertTrue(relative.contains("application-prod.yml"), "扫描面必须包含 application-prod.yml");
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> filesDefining(final String section, final boolean includeProfiles) {
        final List<String> hits = new ArrayList<>();
        for (Path file : shippedYamlFiles()) {
            if (!includeProfiles && isProfileFile(file)) {
                continue;
            }
            final Map<String, Object> jwt = jwtSection(load(file));
            if (jwt == null) {
                continue;
            }
            final Map<String, Object> block = asMap(jwt.get(section));
            if (block != null && !block.isEmpty()) {
                hits.add(relativeName(file));
            }
        }
        hits.sort(String::compareTo);
        return hits;
    }

    private static List<Path> shippedYamlFiles() {
        final Path resources = Path.of("src/main/resources");
        final List<Path> files;
        try (Stream<Path> walk = Files.walk(resources)) {
            files = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("无法枚举 " + resources, ex);
        }
        return files;
    }

    private static boolean isProfileFile(final Path file) {
        final String name = file.getFileName().toString();
        return name.startsWith("application-") && name.endsWith(".yml");
    }

    private static String relativeName(final Path file) {
        return Path.of("src/main/resources").relativize(file).toString().replace('\\', '/');
    }

    private static Map<String, Object> load(final Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            final Object loaded = new Yaml().load(in);
            final Map<String, Object> root = asMap(loaded);
            return root == null ? Map.of() : root;
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取 " + file, ex);
        }
    }

    private static Map<String, Object> jwtSection(final Map<String, Object> root) {
        Map<String, Object> node = root;
        for (final String key : new String[] {"jairouter", "security", "jwt"}) {
            node = asMap(node.get(key));
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}

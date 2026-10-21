package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataMaskingScope} 单元测试（issue #560 可配置化 A 档）。
 *
 * <p>核心约束是「默认行为逐字不变」：未配置 scope 时，判定结果必须与历史
 * {@link ExcludedPathsConfig#isDataMaskExcluded(String)} 完全一致。</p>
 */
@DisplayName("DataMaskingScope 生效范围")
class DataMaskingScopeTest {

    private static SanitizationConfig.ResponseSanitization response() {
        return new SanitizationConfig().getResponse();
    }

    @Test
    @DisplayName("未配置 patterns 时回落到内置排除集合，且判定与历史实现一致")
    void defaultsShouldEqualLegacyExclusion() {
        final DataMaskingScope scope = DataMaskingScope.from(response());

        assertTrue(scope.isUsingBuiltInDefaults());
        assertEquals(DataMaskingScope.MODE_EXCLUDE, scope.getMode());
        assertNull(scope.warning());

        final String[] excluded = {
                "/api/security/sanitization/config",
                "/api/v1/models",
                "/admin",
                "/admin/dashboard",
                "/actuator/health",
                "/actuator",
                "/health",
                "/metrics",
                "/swagger-ui/index.html",
                "/v3/api-docs",
                "/favicon.ico",
                "/.well-known/security.txt"
        };
        for (final String path : excluded) {
            assertFalse(scope.shouldSanitize(path), path + " 应保持排除");
            assertEquals(ExcludedPathsConfig.isDataMaskExcluded(path), !scope.shouldSanitize(path),
                    path + " 与历史判定不一致");
        }

        // 与历史实现的唯一有意差异：`/api/**` 现在也命中 `/api` 本身。AntPathMatcher 不把
        // `/api/**` 匹配到 `/api`，这里显式补了基路径；`/api` 只可能是管理台基地址，排除比脱敏安全。
        assertFalse(scope.shouldSanitize("/api"));

        final String[] included = {
                "/v1/chat/completions",
                "/v1/models",
                "/custom/api/echo"
        };
        for (final String path : included) {
            assertTrue(scope.shouldSanitize(path), path + " 应进入脱敏");
            assertEquals(ExcludedPathsConfig.isDataMaskExcluded(path), !scope.shouldSanitize(path),
                    path + " 与历史判定不一致");
        }
    }

    @Test
    @DisplayName("exclude + 自定义 patterns：自定义集合整体替换内置集合")
    void customExcludePatternsReplaceDefaults() {
        final SanitizationConfig.ResponseSanitization config = response();
        config.getScope().setPatterns(List.of("/v1/**", "/health"));

        final DataMaskingScope scope = DataMaskingScope.from(config);

        assertFalse(scope.isUsingBuiltInDefaults());
        assertFalse(scope.shouldSanitize("/v1/chat/completions"));
        assertFalse(scope.shouldSanitize("/health"));
        assertTrue(scope.shouldSanitize("/api/security/sanitization/config"));
        assertEquals("/v1/**", scope.matchedPattern("/v1/chat/completions"));
    }

    @Test
    @DisplayName("include + patterns：只有命中才脱敏")
    void includeModeOnlyMatchesListedPaths() {
        final SanitizationConfig.ResponseSanitization config = response();
        config.getScope().setMode("INCLUDE");
        config.getScope().setPatterns(List.of("/v1/**"));

        final DataMaskingScope scope = DataMaskingScope.from(config);

        assertEquals(DataMaskingScope.MODE_INCLUDE, scope.getMode());
        assertTrue(scope.shouldSanitize("/v1/chat/completions"));
        assertTrue(scope.shouldSanitize("/v1"));
        assertFalse(scope.shouldSanitize("/api/security/sanitization/config"));
        assertFalse(scope.shouldSanitize("/health"));
    }

    @Test
    @DisplayName("include + 空 patterns：一条都不脱敏，并给出显式告警")
    void includeWithoutPatternsMasksNothing() {
        final SanitizationConfig.ResponseSanitization config = response();
        config.getScope().setMode("include");

        final DataMaskingScope scope = DataMaskingScope.from(config);

        assertFalse(scope.isUsingBuiltInDefaults());
        assertFalse(scope.shouldSanitize("/v1/chat/completions"));
        assertNotNull(scope.warning());
        assertTrue(scope.warning().contains("include"));
    }

    @Test
    @DisplayName("无通配符的 pattern 按前缀匹配，带通配符的按 Ant 匹配")
    void prefixAndAntMatching() {
        final SanitizationConfig.ResponseSanitization config = response();
        config.getScope().setPatterns(List.of("/api/", "/v1/*/completions"));

        final DataMaskingScope scope = DataMaskingScope.from(config);

        assertTrue(scope.shouldSanitize("/api"));
        assertFalse(scope.shouldSanitize("/api/anything"));
        assertFalse(scope.shouldSanitize("/v1/chat/completions"));
        assertTrue(scope.shouldSanitize("/v1/chat/other"));
        assertTrue(scope.shouldSanitize(null));
        assertTrue(scope.shouldSanitize(""));
    }

    @Test
    @DisplayName("describe() 回显 mode/patterns/来源，且 warning 不为 null 值")
    void describeShouldBeMachineReadable() {
        final SanitizationConfig.ResponseSanitization config = response();
        config.getScope().setMode("include");
        config.getScope().setPatterns(List.of("/v1/**"));

        final java.util.Map<String, Object> described = DataMaskingScope.from(config).describe();

        assertEquals("include", described.get("mode"));
        assertEquals(List.of("/v1/**"), described.get("patterns"));
        assertEquals(false, described.get("usingBuiltInDefaults"));
        assertEquals("", described.get("warning"));
    }
}

package org.unreal.modelrouter.auth.security.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「permitAll 加了、认证排除清单没加」的一致性守卫（issue #1174）。
 *
 * <p><b>为什么需要这条守卫</b>：{@code SecurityConfiguration} 里声明的 {@code permitAll()}
 * 只管<b>授权</b>；而认证是在 {@code SpringSecurityAuthenticationFilter} 里、整条安全链之前
 * 完成的，它用 {@link ExcludedPathsConfig#isAuthExcluded} 判断是否跳过。两条清单一不一致，
 * 匿名端点就会在 filter 处被写成 {@code 401 AUTH_MISSING}，压根走不到 {@code permitAll}。</p>
 *
 * <p>本轮（SLA 接入）就是这样漏的：单测直接调 service/controller，不经过 filter，一路绿灯；
 * 真机 `POST /api/auth/sla/bootstrap` 才暴露 401。因此这条守卫读 {@code SecurityConfiguration}
 * 源码，把「字面量路径 + permitAll」的 {@code /api/} 端点与排除清单逐条比对。</p>
 *
 * <p><b>为什么只查 {@code /api/} 前缀</b>：swagger / admin 静态资源 / actuator 的匿名性由
 * {@code AnonymousEndpointPaths} 与 {@code apiDocsAuthExcluded} 另行决定，混进来会误报。</p>
 */
class AnonymousEndpointConsistencyTest {

    private static final Pattern PERMIT_ALL_API_PATTERN = Pattern.compile(
            "pathMatchers\\(\\s*(?:org\\.springframework\\.http\\.)?HttpMethod\\.\\w+\\s*,\\s*\"(/api/[^\"]+)\"\\s*\\)"
                    + "\\s*\\.permitAll\\(\\)");

    private static final Path SECURITY_CONFIGURATION = Path.of(
            "src", "main", "java", "org", "unreal", "modelrouter", "auth", "security", "config",
            "SecurityConfiguration.java");

    @Test
    @DisplayName("SecurityConfiguration 里为 /api 路径声明的 permitAll 端点，都必须在认证排除清单里")
    void permittedApiEndpointsAreAlsoAuthExcluded() throws IOException {
        final Set<String> permittedPaths = permittedApiPaths();

        // 自证：解析失效时「找不到就等于通过」会静默成立，所以先锁住已知的 4 条。
        assertTrue(permittedPaths.size() >= 4,
                "应至少解析出 4 条匿名 /api 端点（jwt/login、jwt/validate、sla/bootstrap、sla/refresh），"
                        + "实际 " + permittedPaths + " —— 解析可能失效，先修本测试的匹配规则");

        final Set<String> missing = new LinkedHashSet<>();
        for (final String path : permittedPaths) {
            if (!ExcludedPathsConfig.isAuthExcluded(path)) {
                missing.add(path);
            }
        }

        assertTrue(missing.isEmpty(),
                "以下端点在 SecurityConfiguration 里 permitAll，却没进 ExcludedPathsConfig.AUTH_EXCLUDED_PATHS："
                        + missing
                        + "。认证 filter 会先一步写 401 AUTH_MISSING，permitAll 根本不会被评估 —— "
                        + "两条清单必须同时登记。");
    }

    @Test
    @DisplayName("SLA 接入的两条端点确实匿名可过认证 filter")
    void slaEndpointsAreAnonymouslyReachable() {
        assertTrue(ExcludedPathsConfig.isAuthExcluded("/api/auth/sla/bootstrap"),
                "POST /api/auth/sla/bootstrap 必须免认证：调用方手上只有一次性接入令牌");
        assertTrue(ExcludedPathsConfig.isAuthExcluded("/api/auth/sla/refresh"),
                "POST /api/auth/sla/refresh 必须免认证：调用方手上只有刷新凭据");
        // 反向：管理端不能被连带放开
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/api/security/sla/accounts"),
                "SLA 账号管理端必须走正常认证 + 权限码，不能因为前缀相近被误排除");
    }

    private Set<String> permittedApiPaths() throws IOException {
        assertTrue(Files.exists(SECURITY_CONFIGURATION),
                "找不到 SecurityConfiguration.java，测试需在项目根目录执行: " + SECURITY_CONFIGURATION.toAbsolutePath());
        final String source = Files.readString(SECURITY_CONFIGURATION, StandardCharsets.UTF_8);
        final Set<String> paths = new LinkedHashSet<>();
        final Matcher matcher = PERMIT_ALL_API_PATTERN.matcher(source);
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return paths;
    }
}

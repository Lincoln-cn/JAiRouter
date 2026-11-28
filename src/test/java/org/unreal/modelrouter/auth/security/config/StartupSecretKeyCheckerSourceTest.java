package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动门闩读取来源（issue #1247）。
 *
 * <p>缺陷形态：门闩用 {@code System.getenv("JWT_SECRET")} 读密钥，而 yaml 里的
 * {@code ${JWT_SECRET:…}} 是由 Spring 的 {@code Environment} 展开的 —— 后者还包含系统属性与
 * 命令行参数（{@code -DJWT_SECRET=…}、{@code --JWT_SECRET=…}），两者优先级都高于环境变量。
 * 于是用 {@code -D} 传了强密钥时，门闩读到 null，生产环境直接拒绝启动。</p>
 *
 * <p>这里锁住「解析顺序」这件事本身；真正的启动行为由 {@code StartupSecurityGateTest} 覆盖。</p>
 */
@DisplayName("启动门闩的密钥读取来源（issue #1247）")
class StartupSecretKeyCheckerSourceTest {

    /** 测试用变量名：避免命中 CI 上真实存在的 JWT_SECRET，让「回落到环境变量」的分支可判定 */
    private static final String PROBE_ENV = "JAIR_CHECKER_PROBE_VALUE";
    private static final String PROBE_PROPERTY = "jairouter.security.probe.secret";

    @Nested
    @DisplayName("解析顺序：Environment → 配置文件 → 系统环境变量")
    class ResolutionOrder {

        @Test
        @DisplayName("Environment 里有值就优先用它（并去掉首尾空白）")
        void environmentValueWins() {
            final Environment environment = mock(Environment.class);
            when(environment.getProperty(PROBE_ENV)).thenReturn("  env-secret  ");

            assertEquals("env-secret",
                    StartupSecretKeyChecker.resolveConfiguredValue(environment, PROBE_ENV, PROBE_PROPERTY));
        }

        @Test
        @DisplayName("Environment 没这个变量时，回落到配置文件里直接写死的值")
        void fallsBackToConfiguredProperty() {
            final Environment environment = mock(Environment.class);
            when(environment.getProperty(PROBE_ENV)).thenReturn(null);
            when(environment.getProperty(PROBE_PROPERTY)).thenReturn("configured-secret");

            assertEquals("configured-secret",
                    StartupSecretKeyChecker.resolveConfiguredValue(environment, PROBE_ENV, PROBE_PROPERTY));
        }

        @Test
        @DisplayName("配置值是 ${random.uuid} 的产物时不算「已配置」")
        void randomGeneratedPropertyIsNotConfigured() {
            final Environment environment = mock(Environment.class);
            when(environment.getProperty(PROBE_ENV)).thenReturn(null);
            when(environment.getProperty(PROBE_PROPERTY))
                    .thenReturn("6f1a0a2e-1c1e-4b3a-9d2f-2b4c5d6e7f80");

            // 未被当成配置值 ⇒ 落到 System.getenv(PROBE_ENV)；PROBE_ENV 不存在 ⇒ null
            assertNull(StartupSecretKeyChecker.resolveConfiguredValue(environment, PROBE_ENV, PROBE_PROPERTY),
                    "随机生成的占位值不能当成「运维已配置密钥」");
        }

        @Test
        @DisplayName("Environment 为 null 时退化为只读环境变量")
        void nullEnvironmentFallsBackToSystemEnv() {
            assertNull(StartupSecretKeyChecker.resolveConfiguredValue(null, PROBE_ENV, PROBE_PROPERTY));
        }

        @Test
        @DisplayName("三处都没有时返回 null（调用方据此提示「未设置」）")
        void nothingConfiguredReturnsNull() {
            final Environment environment = mock(Environment.class);
            when(environment.getProperty(PROBE_ENV)).thenReturn("");
            when(environment.getProperty(PROBE_PROPERTY)).thenReturn("   ");

            assertNull(StartupSecretKeyChecker.resolveConfiguredValue(environment, PROBE_ENV, PROBE_PROPERTY));
        }
    }

    @Nested
    @DisplayName("源码守卫：密钥读取不得回退到只读环境变量")
    class SourceGuard {

        @Test
        @DisplayName("checkJwtSecrets / checkAdminPassword 不再直接 System.getenv 读密钥")
        void checkerNoLongerReadsSecretsDirectlyFromEnv() throws IOException {
            final String source = Files.readString(
                    Path.of("src/main/java/org/unreal/modelrouter/auth/security/config/StartupSecretKeyChecker.java"),
                    StandardCharsets.UTF_8);

            assertFalse(source.contains("System.getenv(\"JWT_SECRET\")"),
                    "JWT 密钥必须经 resolveConfiguredValue 读，否则 -D/-- 传的值会被忽略（#1247）");
            assertFalse(source.contains("System.getenv(\"INITIAL_ADMIN_PASSWORD\")"),
                    "管理员口令同理（#1247）");
            assertTrue(source.contains("resolveConfiguredValue"),
                    "解析方法应被真实调用，而不是只留一个未被使用的辅助方法");
        }
    }
}

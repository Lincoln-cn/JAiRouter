package org.unreal.modelrouter.config.core.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveConfigValidator 测试
 * 验证敏感配置硬编码检测逻辑
 */
@DisplayName("SensitiveConfigValidator 硬编码检测测试")
class SensitiveConfigValidatorTest {

    @Test
    @DisplayName("检测硬编码占位符")
    void testIsHardcodedSecret_placeholder() {
        SensitiveConfigValidator validator = new SensitiveConfigValidator();
        
        // 测试占位符模式（应检测为硬编码）
        assertTrue(validator.isHardcodedSecret("your-secret-key"), "your-* 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("your-api-token"), "your-* 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("placeholder-secret"), "placeholder 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("example-key"), "example 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("test-secret"), "test 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("demo-password"), "demo 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("changeme"), "changeme 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("secret"), "secret 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("password"), "password 应检测为硬编码");
    }

    @Test
    @DisplayName("检测合法密钥")
    void testIsHardcodedSecret_validSecret() {
        SensitiveConfigValidator validator = new SensitiveConfigValidator();
        
        // 测试合法密钥（不应被检测为硬编码）
        assertFalse(validator.isHardcodedSecret("aBc123XyZ789SecretKeyForProduction"), "合法密钥不应被检测");
        assertFalse(validator.isHardcodedSecret("sk-proj-abc123def456ghi789"), "合法密钥不应被检测");
        assertFalse(validator.isHardcodedSecret(null), "null 不应被检测");
        assertFalse(validator.isHardcodedSecret(""), "空字符串不应被检测");
        assertFalse(validator.isHardcodedSecret("MyRealProductionKey2024!"), "合法密钥不应被检测");
    }

    @Test
    @DisplayName("检测大小写不敏感")
    void testIsHardcodedSecret_caseInsensitive() {
        SensitiveConfigValidator validator = new SensitiveConfigValidator();
        
        // 测试大小写混合（都应检测为硬编码）
        assertTrue(validator.isHardcodedSecret("YOUR-SECRET"), "YOUR-* 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("Your-Key"), "Your-* 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("TEST-Key"), "TEST-* 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("Demo-Token"), "Demo-* 应检测为硬编码");
        assertTrue(validator.isHardcodedSecret("EXAMPLE"), "EXAMPLE 应检测为硬编码");
    }

    @Test
    @DisplayName("检测包含关键字的字符串")
    void testIsHardcodedSecret_containsKeyword() {
        SensitiveConfigValidator validator = new SensitiveConfigValidator();
        
        // 测试包含关键字（应检测为硬编码）
        assertTrue(validator.isHardcodedSecret("my-test-secret-key"), "包含 test 应检测");
        assertTrue(validator.isHardcodedSecret("placeholder-for-production"), "包含 placeholder 应检测");
        assertTrue(validator.isHardcodedSecret("this-is-an-example"), "包含 example 应检测");
        assertTrue(validator.isHardcodedSecret("demo-token-123"), "包含 demo 应检测");
    }

    @Test
    @DisplayName("检测边界情况")
    void testIsHardcodedSecret_edgeCases() {
        SensitiveConfigValidator validator = new SensitiveConfigValidator();
        
        // 边界情况：包含关键字（应检测为硬编码）
        assertTrue(validator.isHardcodedSecret("testing123Secret"), "包含 test 应检测");
        assertFalse(validator.isHardcodedSecret("prod-key-abc"), "prod-key 不包含关键字");
        assertFalse(validator.isHardcodedSecret("development-password"), "development-password 不等于 password");
        
        // 特殊情况：只有关键字（equals 检测）
        assertTrue(validator.isHardcodedSecret("SECRET"), "SECRET equals secret");
        assertTrue(validator.isHardcodedSecret("PASSWORD"), "PASSWORD equals password");
        assertFalse(validator.isHardcodedSecret("my-password"), "my-password 不等于 password");
    }

    @Test
    @DisplayName("缺 INITIAL_ADMIN_PASSWORD 的告警必须描述真实行为（issue #314 / #664）")
    void missingAdminPasswordWarningMatchesRealBehavior() throws Exception {
        String warning = SensitiveConfigValidator.missingAdminPasswordWarning();

        // ① 基础配置的回落值必须是随机占位符 —— 任何公开字面量都会随发布包一起发出去（#664）
        String fallback = adminFallbackFromJwtYml();
        assertEquals("${random.uuid}", fallback,
                "config/auth/jwt.yml 的回落值必须是 ${random.uuid}，不能再是公开字面量");

        // ② 非生产环境首次初始化会随机生成并打印口令，但只在「创建账号」的那一次（#802）：
        //    库已有账号时既不再生成也不再打印，文案不能让人以为每次重启都能拿到可用口令。
        assertTrue(warning.contains("随机"), "告警应说明非生产环境随机生成；实际文案：" + warning);
        assertTrue(warning.contains("启动日志"), "告警应指向启动日志里的那行口令；实际文案：" + warning);
        assertTrue(warning.contains("首次") || warning.contains("创建账号"),
                "告警必须限定为「首次初始化 / 创建账号的那一次」；实际文案：" + warning);
        assertTrue(warning.contains("已有账号") || warning.contains("不会再打印"),
                "告警必须说明重启后不再打印可用口令；实际文案：" + warning);

        // ③ 生产仍必须 fail-fast，文案不能让运维以为生产也能起来
        assertTrue(warning.contains("生产") && warning.contains("启动失败"),
                "告警必须说明生产 profile 会启动失败；实际文案：" + warning);

        // ④ 还要指出去哪生成强密码 —— 旧文案想表达的就是这件事，只是说反了
        assertTrue(warning.contains("--generate-password"), "应指向唯一真实的生成器");
    }

    /** 从真实 yml 里读出 {@code ${INITIAL_ADMIN_PASSWORD:...}} 的回落值（允许嵌套 {@code ${...}}） */
    private static String adminFallbackFromJwtYml() throws Exception {
        try (InputStream in = new ClassPathResource("config/auth/jwt.yml").getInputStream()) {
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\\$\\{INITIAL_ADMIN_PASSWORD:(.*)}").matcher(yml);
            assertTrue(matcher.find(), "config/auth/jwt.yml 里应存在 ${INITIAL_ADMIN_PASSWORD:...} 的回落写法");
            return matcher.group(1);
        }
    }
}
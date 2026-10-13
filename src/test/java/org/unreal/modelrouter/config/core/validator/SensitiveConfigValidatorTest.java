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
    @DisplayName("缺 INITIAL_ADMIN_PASSWORD 的告警必须描述真实行为（issue #314）")
    void missingAdminPasswordWarningMatchesRealBehavior() throws Exception {
        String warning = SensitiveConfigValidator.missingAdminPasswordWarning();

        // ① 不得再声称「会生成随机密码」—— 本仓不存在任何启动期随机密码生成
        assertFalse(warning.contains("生成随机密码"),
                "不存在启动期随机密码生成，告警不得再这么写");

        // ② 必须写出真实的回落值，且该值与 config/auth/jwt.yml 的回落写法一致（交叉核对，不是自证）
        String fallback = adminFallbackFromJwtYml();
        assertEquals(SensitiveConfigValidator.FACTORY_DEFAULT_ADMIN_PASSWORD, fallback,
                "Java 常量与 config/auth/jwt.yml 的回落值必须一致");
        assertTrue(warning.contains(fallback),
                "告警必须写出真实的回落值 " + fallback + "，运维才能判断风险；实际文案：" + warning);

        // ③ 还要指出去哪生成强密码 —— 旧文案想表达的就是这件事，只是说反了
        assertTrue(warning.contains("--generate-password"), "应指向唯一真实的生成器");
    }

    /** 从真实 yml 里读出 {@code ${INITIAL_ADMIN_PASSWORD:...}} 的回落值 */
    private static String adminFallbackFromJwtYml() throws Exception {
        try (InputStream in = new ClassPathResource("config/auth/jwt.yml").getInputStream()) {
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\\$\\{INITIAL_ADMIN_PASSWORD:([^}]*)}").matcher(yml);
            assertTrue(matcher.find(), "config/auth/jwt.yml 里应存在 ${INITIAL_ADMIN_PASSWORD:...} 的回落写法");
            return matcher.group(1);
        }
    }
}
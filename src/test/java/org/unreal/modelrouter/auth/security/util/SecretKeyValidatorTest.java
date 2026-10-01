package org.unreal.modelrouter.auth.security.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator.StrengthLevel;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密钥验证器测试类
 */
@DisplayName("密钥验证器测试")
class SecretKeyValidatorTest {

    @Test
    @DisplayName("验证 JWT 密钥 - 空密钥")
    void testValidateJwtSecret_Empty() {
        ValidationResult result = SecretKeyValidator.validateJwtSecret(null);
        assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel());
        assertFalse(result.isPassed());
        
        result = SecretKeyValidator.validateJwtSecret("");
        assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel());
        assertFalse(result.isPassed());
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 常见弱密钥")
    void testValidateJwtSecret_WeakPatterns() {
        String[] weakSecrets = {
            "secret",
            "mysecretkey",
            "password123",
            "your-test-key",
            "change-me-now",
            "default_key",
            "test_key_123",
            "demo-key-abc",
            "ChangeMeOnFirstStartup123456",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "12345678901234567890"
        };

        for (String secret : weakSecrets) {
            ValidationResult result = SecretKeyValidator.validateJwtSecret(secret);
            assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel(),
                "密钥 '" + secret + "' 应被识别为非常弱");
            assertFalse(result.isPassed());
        }
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 默认密钥")
    void testValidateJwtSecret_DefaultKey() {
        ValidationResult result = SecretKeyValidator.validateJwtSecret("ChangeMeOnFirstStartup123456");
        assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel());
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("常见弱密钥"));
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 长度不足")
    void testValidateJwtSecret_ShortLength() {
        // 生成一个不是常见弱密钥但长度不足的密钥
        String shortKey = "ShortKey123!@#abc"; // 小于 32 字节
        ValidationResult result = SecretKeyValidator.validateJwtSecret(shortKey);
        // 长度不足且可能是常见弱密钥模式
        assertTrue(result.getStrengthLevel() == StrengthLevel.WEAK || 
                   result.getStrengthLevel() == StrengthLevel.VERY_WEAK,
                   "长度不足的密钥应被识别为 WEAK 或 VERY_WEAK");
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("密钥") || result.getMessage().contains("常见"));
    }

    /** 32 字节固定密钥（Base64），无弱模式子串，断言精确等级用 */
    private static final String FIXED_MEDIUM_KEY =
        "MDEyMzQ1Njc4OWFiY2RlZkZFRENCQTk4NzY1NDMyMTA=";

    /** 48 字节固定密钥（Base64），无弱模式子串，断言精确等级用 */
    private static final String FIXED_STRONG_KEY =
        "MDEyMzQ1Njc4OWFiY2RlZkZFRENCQTk4NzY1NDMyMTBaYVF4U3dFZENmUnZUZ0Ju";

    /** 64 字节固定密钥（Base64），无弱模式子串，断言精确等级用 */
    private static final String FIXED_VERY_STRONG_KEY =
        "MDEyMzQ1Njc4OWFiY2RlZkZFRENCQTk4NzY1NDMyMTBaYVF4U3dFZENmUnZUZ0JuSGpLbE1uUHFSc1R1VndYeQ==";

    /**
     * issue #169 回归：48 字节随机 Base64 密钥中偶然出现子串 "key"，
     * 不得再被误判为 VERY_WEAK。
     */
    private static final String REGRESSION_FALSE_POSITIVE_KEY =
        "RotI3eHmszJEd0XQM9fw+pmlrmGHpLtLgm7U8c28qOWgAupDURlkeynY7UEoSg8a";

    @Test
    @DisplayName("验证 JWT 密钥 - 中等强度")
    void testValidateJwtSecret_Medium() {
        // 32-47 字节的有效 Base64 密钥（固定值，避免随机误判导致用例不稳）
        ValidationResult result = SecretKeyValidator.validateJwtSecret(FIXED_MEDIUM_KEY);
        assertEquals(StrengthLevel.MEDIUM, result.getStrengthLevel());
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 强密钥")
    void testValidateJwtSecret_Strong() {
        // 48-63 字节
        ValidationResult result = SecretKeyValidator.validateJwtSecret(FIXED_STRONG_KEY);
        assertEquals(StrengthLevel.STRONG, result.getStrengthLevel());
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 非常强密钥")
    void testValidateJwtSecret_VeryStrong() {
        // 64 字节以上
        ValidationResult result = SecretKeyValidator.validateJwtSecret(FIXED_VERY_STRONG_KEY);
        assertEquals(StrengthLevel.VERY_STRONG, result.getStrengthLevel());
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证 JWT 密钥 - issue#169 回归：随机强密钥含 key 子串不得误判")
    void testValidateJwtSecret_RandomStrongKeyWithWeakSubstring_NotFalsePositive() {
        ValidationResult result = SecretKeyValidator.validateJwtSecret(REGRESSION_FALSE_POSITIVE_KEY);
        assertEquals(StrengthLevel.STRONG, result.getStrengthLevel(),
            "48 字节随机 Base64 密钥（含偶然子串 key）应判 STRONG，不得误判为 VERY_WEAK");
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 随机生成密钥不得误判为 VERY_WEAK（仅断言等级无关属性）")
    void testValidateJwtSecret_RandomGeneratedKeys_NeverVeryWeakFalsePositive() {
        for (int i = 0; i < 200; i++) {
            String key = SecretKeyGenerator.generateBase64Key(48);
            ValidationResult result = SecretKeyValidator.validateJwtSecret(key);
            // 随机 48 字节密钥长度必达 STRONG，此处只断言“不会因短模式被降为 VERY_WEAK”
            assertNotEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel(),
                "随机生成的 48 字节密钥不得被误判为 VERY_WEAK: " + key);
            assertTrue(result.isPassed(), "随机生成的 48 字节密钥应通过校验: " + key);
        }
    }

    @Test
    @DisplayName("验证密码 - 空密码")
    void testValidatePassword_Empty() {
        ValidationResult result = SecretKeyValidator.validatePassword(null);
        assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel());
        assertFalse(result.isPassed());
        
        result = SecretKeyValidator.validatePassword("");
        assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel());
        assertFalse(result.isPassed());
    }

    @Test
    @DisplayName("验证密码 - 常见弱密码")
    void testValidatePassword_WeakPatterns() {
        String[] weakPasswords = {
            "password",
            "admin123",
            "12345678",
            "qwerty",
            "abc123",
            "password123",
            "default",
            "test",
            "guest",
            "ChangeMeOnFirstStartup123456"  // 默认密码
        };
        
        for (String password : weakPasswords) {
            ValidationResult result = SecretKeyValidator.validatePassword(password);
            assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel(), 
                "密码 '" + password + "' 应被识别为非常弱");
            assertFalse(result.isPassed());
        }
    }

    @Test
    @DisplayName("验证密码 - 过于简单")
    void testValidatePassword_TooSimple() {
        String simplePassword = "abc";  // 太短且无多样性
        ValidationResult result = SecretKeyValidator.validatePassword(simplePassword);
        assertEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel());
        assertFalse(result.isPassed());
    }

    @Test
    @DisplayName("验证密码 - 弱密码")
    void testValidatePassword_Weak() {
        String weakPassword = "simple123";  // 长度不足且多样性不够
        ValidationResult result = SecretKeyValidator.validatePassword(weakPassword);
        assertTrue(result.getStrengthLevel() == StrengthLevel.WEAK || 
                   result.getStrengthLevel() == StrengthLevel.VERY_WEAK);
        assertFalse(result.isPassed());
    }

    @Test
    @DisplayName("验证密码 - 中等强度")
    void testValidatePassword_Medium() {
        // 12-15 字符，有一定多样性
        String mediumPassword = "Medium123";  // 9 字符，可能不够
        ValidationResult result = SecretKeyValidator.validatePassword(mediumPassword);
        // 根据评分系统，可能是 WEAK 或 MEDIUM
        assertTrue(result.getStrengthLevel() == StrengthLevel.MEDIUM || 
                   result.getStrengthLevel() == StrengthLevel.WEAK);
    }

    @Test
    @DisplayName("验证密码 - 强密码")
    void testValidatePassword_Strong() {
        // 16+ 字符，包含大小写、数字、特殊字符
        String strongPassword = "Str0ng!Pass#2026";
        ValidationResult result = SecretKeyValidator.validatePassword(strongPassword);
        assertTrue(result.getStrengthLevel() == StrengthLevel.STRONG || 
                   result.getStrengthLevel() == StrengthLevel.VERY_STRONG);
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证密码 - 非常强密码")
    void testValidatePassword_VeryStrong() {
        // 20+ 字符，高多样性
        String veryStrongPassword = "V3ry$tr0ng!P@ssw0rd#2026";
        ValidationResult result = SecretKeyValidator.validatePassword(veryStrongPassword);
        // 根据评分系统，可能是 STRONG 或 VERY_STRONG
        assertTrue(result.getStrengthLevel() == StrengthLevel.STRONG || 
                   result.getStrengthLevel() == StrengthLevel.VERY_STRONG,
                   "强密码应被识别为 STRONG 或 VERY_STRONG");
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证密码 - 生成的随机密码必须通过校验（#171）")
    void testValidatePassword_Generated() {
        // 修复前：generateAlphanumericKey(20) 的产物有 2.99%（1/33）被判 WEAK——整串不含数字时
        // 字符多样性只拿 2 分、总分 5 分落 WEAK，而 StartupSecretKeyChecker 对 WEAK 同样拒绝启动；
        // 另有 1/41667 概率因偶然含 "test" 被 contains 误判为 VERY_WEAK。
        // 实测（各 100 万次）修复后拒绝率降至 1/1000000，故此处直接断言必须通过。
        for (int i = 0; i < 20; i++) {
            final String generatedPassword = SecretKeyGenerator.generateAlphanumericKey(20);
            final ValidationResult result = SecretKeyValidator.validatePassword(generatedPassword);
            assertTrue(result.isPassed(),
                    "生成的 20 位随机密码必须通过校验，实际为: " + result.getStrengthLevel()
                            + "（密码: " + generatedPassword + "）");
        }
    }

    @Test
    @DisplayName("验证密码 - 随机密码中的偶然弱子串不得误判（#171 实测样本回归）")
    void testValidatePassword_IncidentalWeakSubstring() {
        // 以下样本取自修复前的 100 万次实测：均为机器随机生成、字符级熵 ≥ 3.75 的 20 字符串，
        // 仅因偶然包含大小写不敏感的 "test" 而被老实现判为 VERY_WEAK（进而阻止生产启动）。
        // 修复后要求弱模式在密码中占主导（≥ 1/2）才算命中，这些样本必须判定通过。
        final String[] falsePositives = {
            "tESTaaeh9XHdJme382qL",   // 熵 4.1219
            "WTestDm69XROvjJU7nHz",   // 熵 4.3219
            "qpDnnE10ewrHLTeSTq9i",   // 熵 3.9219
        };
        for (String password : falsePositives) {
            final ValidationResult result = SecretKeyValidator.validatePassword(password);
            assertNotEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel(),
                    "随机密码中的偶然弱子串不得触发误判: " + password);
            assertTrue(result.isPassed(), "应通过校验: " + password);
        }
    }

    @Test
    @DisplayName("验证密码 - 不含数字的长随机密码不得因缺一类字符被判 WEAK（#171 实测样本回归）")
    void testValidatePassword_GeneratedWithoutDigits() {
        // 机器生成 20 字符字母数字串时整串不含数字的概率约 3%（16 字符约 6%），修复前仅因
        // 恰好缺少一个字符类别就落到 WEAK。这两个样本取自修复前的实测 WEAK 集合，且不含任何弱模式，
        // 修复后必须判定通过。
        final String[] noDigitPasswords = {
            "NrwpqMJhkovoloMfxLlA",
            "udicGIZugqljHuRCbDzD",
        };
        for (String password : noDigitPasswords) {
            final ValidationResult result = SecretKeyValidator.validatePassword(password);
            assertTrue(result.isPassed(),
                    "不含数字的长随机密码不应被判 WEAK: " + password
                            + "，实际: " + result.getStrengthLevel());
        }
    }

    @Test
    @DisplayName("获取最小密钥长度")
    void testGetMinKeyLength() {
        assertEquals(32, SecretKeyValidator.getMinKeyLength());
    }

    @Test
    @DisplayName("获取最小密码长度")
    void testGetMinPasswordLength() {
        assertEquals(12, SecretKeyValidator.getMinPasswordLength());
    }

    @Test
    @DisplayName("验证结果消息")
    void testValidationResult_Message() {
        ValidationResult weakResult = SecretKeyValidator.validateJwtSecret("weak");
        assertNotNull(weakResult.getMessage());
        assertFalse(weakResult.getMessage().isEmpty());

        ValidationResult strongResult = SecretKeyValidator.validateJwtSecret(FIXED_VERY_STRONG_KEY);
        assertNotNull(strongResult.getMessage());
        assertTrue(strongResult.isPassed());
    }

    @Test
    @DisplayName("验证结果 - 成功和失败工厂方法")
    void testValidationResult_FactoryMethods() {
        ValidationResult success = ValidationResult.success(StrengthLevel.STRONG);
        assertTrue(success.isPassed());
        assertEquals(StrengthLevel.STRONG, success.getStrengthLevel());
        
        ValidationResult failure = ValidationResult.failure(StrengthLevel.WEAK, "测试失败原因");
        assertFalse(failure.isPassed());
        assertEquals(StrengthLevel.WEAK, failure.getStrengthLevel());
        assertEquals("测试失败原因", failure.getMessage());
    }
}

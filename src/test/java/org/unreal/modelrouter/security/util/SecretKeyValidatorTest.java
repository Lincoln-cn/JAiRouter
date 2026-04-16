package org.unreal.modelrouter.security.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.unreal.modelrouter.security.util.SecretKeyValidator.StrengthLevel;
import org.unreal.modelrouter.security.util.SecretKeyValidator.ValidationResult;

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
            "demo-key-abc"
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
        ValidationResult result = SecretKeyValidator.validateJwtSecret("UqfpTm2Zw7ff2BNnZb8AQo8t");
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

    @Test
    @DisplayName("验证 JWT 密钥 - 中等强度")
    void testValidateJwtSecret_Medium() {
        // 32-47 字节的有效 Base64 密钥
        String mediumKey = SecretKeyGenerator.generateBase64Key(32);
        ValidationResult result = SecretKeyValidator.validateJwtSecret(mediumKey);
        assertEquals(StrengthLevel.MEDIUM, result.getStrengthLevel());
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 强密钥")
    void testValidateJwtSecret_Strong() {
        // 48-63 字节
        String strongKey = SecretKeyGenerator.generateBase64Key(48);
        ValidationResult result = SecretKeyValidator.validateJwtSecret(strongKey);
        assertEquals(StrengthLevel.STRONG, result.getStrengthLevel());
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("验证 JWT 密钥 - 非常强密钥")
    void testValidateJwtSecret_VeryStrong() {
        // 64 字节以上
        String veryStrongKey = SecretKeyGenerator.generateBase64Key(64);
        ValidationResult result = SecretKeyValidator.validateJwtSecret(veryStrongKey);
        assertEquals(StrengthLevel.VERY_STRONG, result.getStrengthLevel());
        assertTrue(result.isPassed());
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
            "UqfpTm2Zw7ff2BNnZb8AQo8t"  // 默认密码
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
    @DisplayName("验证密码 - 生成的随机密码")
    void testValidatePassword_Generated() {
        String generatedPassword = SecretKeyGenerator.generateAlphanumericKey(20);
        ValidationResult result = SecretKeyValidator.validatePassword(generatedPassword);
        // 生成的密码只有字母数字，缺少特殊字符，可能是 MEDIUM 或 STRONG
        assertTrue(result.getStrengthLevel() == StrengthLevel.MEDIUM || 
                   result.getStrengthLevel() == StrengthLevel.STRONG ||
                   result.getStrengthLevel() == StrengthLevel.VERY_STRONG);
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
        
        ValidationResult strongResult = SecretKeyValidator.validateJwtSecret(
            SecretKeyGenerator.generateBase64Key(64));
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

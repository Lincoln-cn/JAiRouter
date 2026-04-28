package org.unreal.modelrouter.auth.security.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密钥生成工具测试类
 */
@DisplayName("密钥生成工具测试")
class SecretKeyGeneratorTest {

    @Test
    @DisplayName("生成 Base64 密钥 - 默认长度")
    void testGenerateBase64Key_DefaultLength() {
        String key = SecretKeyGenerator.generateBase64Key();
        
        assertNotNull(key, "生成的密钥不应为空");
        assertTrue(key.length() >= 44, "Base64 编码的 32 字节密钥长度应至少为 44 字符");
        assertTrue(SecretKeyGenerator.isValidBase64Key(key), "应生成有效的 Base64 密钥");
    }

    @Test
    @DisplayName("生成 Base64 密钥 - 指定长度")
    void testGenerateBase64Key_CustomLength() {
        String key64 = SecretKeyGenerator.generateBase64Key(64);
        
        assertNotNull(key64);
        // Base64 编码 64 字节，长度约为 88 字符（包含填充）
        assertTrue(key64.length() >= 84, "64 字节密钥的 Base64 编码长度应至少为 84 字符");
    }

    @Test
    @DisplayName("生成 Base64 密钥 - 长度不足应抛出异常")
    void testGenerateBase64Key_TooShort() {
        assertThrows(IllegalArgumentException.class, () -> {
            SecretKeyGenerator.generateBase64Key(16); // 小于最小 32 字节
        }, "密钥长度小于 32 字节时应抛出异常");
    }

    @Test
    @DisplayName("生成十六进制密钥")
    void testGenerateHexKey() {
        String hexKey = SecretKeyGenerator.generateHexKey(32);
        
        assertNotNull(hexKey);
        assertEquals(64, hexKey.length(), "32 字节的十六进制密钥应为 64 字符");
        assertTrue(hexKey.matches("[0-9a-f]+"), "十六进制密钥应只包含小写十六进制字符");
    }

    @Test
    @DisplayName("生成字母数字密钥")
    void testGenerateAlphanumericKey() {
        String alphaNumKey = SecretKeyGenerator.generateAlphanumericKey(32);
        
        assertNotNull(alphaNumKey);
        assertEquals(32, alphaNumKey.length(), "密钥长度应与指定长度一致");
        assertTrue(alphaNumKey.matches("[A-Za-z0-9]+"), "字母数字密钥应只包含字母和数字");
    }

    @Test
    @DisplayName("生成字母数字密钥 - 长度不足应抛出异常")
    void testGenerateAlphanumericKey_TooShort() {
        assertThrows(IllegalArgumentException.class, () -> {
            SecretKeyGenerator.generateAlphanumericKey(8); // 小于最小 16 字符
        }, "密钥长度小于 16 字符时应抛出异常");
    }

    @Test
    @DisplayName("生成 API Token - 默认格式")
    void testGenerateApiToken_Default() {
        String token = SecretKeyGenerator.generateApiToken();
        
        assertNotNull(token);
        assertTrue(token.startsWith("gpustack_"), "默认 API Token 应以 gpustack_ 开头");
        
        // 格式：gpustack_<16 位>_<16 位>
        String[] parts = token.split("_");
        assertEquals(3, parts.length, "API Token 应包含 3 个部分（前缀 +2 段随机）");
        assertEquals("gpustack", parts[0]);
        assertEquals(16, parts[1].length(), "第一段随机数应为 16 字符");
        assertEquals(16, parts[2].length(), "第二段随机数应为 16 字符");
    }

    @Test
    @DisplayName("生成 API Token - 自定义前缀")
    void testGenerateApiToken_CustomPrefix() {
        String token = SecretKeyGenerator.generateApiToken("myapp");
        
        assertNotNull(token);
        assertTrue(token.startsWith("myapp_"), "API Token 应以自定义前缀开头");
        
        String[] parts = token.split("_");
        assertEquals(3, parts.length);
        assertEquals("myapp", parts[0]);
    }

    @Test
    @DisplayName("生成格式化密钥")
    void testGenerateFormattedKey() {
        String key = SecretKeyGenerator.generateFormattedKey("test", 16, 3);
        
        assertNotNull(key);
        assertTrue(key.startsWith("test_"), "格式化密钥应包含前缀");
        
        String[] parts = key.split("_");
        assertEquals(4, parts.length, "格式化密钥应包含前缀 +3 段随机数");
        assertEquals("test", parts[0]);
        assertEquals(16, parts[1].length(), "每段随机数长度应与指定一致");
    }

    @Test
    @DisplayName("验证密钥长度")
    void testValidateKeyLength() {
        assertTrue(SecretKeyGenerator.validateKeyLength("abcdefghijklmnopqrstuvwxyz123456", 32));
        assertFalse(SecretKeyGenerator.validateKeyLength("short", 32));
        assertFalse(SecretKeyGenerator.validateKeyLength(null, 32));
        assertFalse(SecretKeyGenerator.validateKeyLength("", 32));
    }

    @Test
    @DisplayName("验证 Base64 密钥 - 有效密钥")
    void testIsValidBase64Key_Valid() {
        String validKey = SecretKeyGenerator.generateBase64Key();
        assertTrue(SecretKeyGenerator.isValidBase64Key(validKey));
    }

    @Test
    @DisplayName("验证 Base64 密钥 - 无效密钥")
    void testIsValidBase64Key_Invalid() {
        assertFalse(SecretKeyGenerator.isValidBase64Key("not-a-valid-base64-key!@#$"));
        assertFalse(SecretKeyGenerator.isValidBase64Key(null));
        assertFalse(SecretKeyGenerator.isValidBase64Key(""));
        assertFalse(SecretKeyGenerator.isValidBase64Key("dG9vc2hvcnQ")); // 太短
    }

    @Test
    @DisplayName("获取最小密钥长度")
    void testGetMinKeyLength() {
        assertEquals(32, SecretKeyGenerator.getMinKeyLength(), "最小密钥长度应为 32 字节");
    }

    @Test
    @DisplayName("获取推荐密钥长度")
    void testGetRecommendedKeyLength() {
        assertEquals(64, SecretKeyGenerator.getRecommendedKeyLength(), "推荐密钥长度应为 64 字节");
    }

    @Test
    @DisplayName("生成的密钥应具有随机性")
    void testKeyRandomness() {
        String key1 = SecretKeyGenerator.generateBase64Key();
        String key2 = SecretKeyGenerator.generateBase64Key();
        
        assertNotEquals(key1, key2, "两次生成的密钥应该不同");
    }

    @Test
    @DisplayName("生成的 API Token 应具有随机性")
    void testApiTokenRandomness() {
        String token1 = SecretKeyGenerator.generateApiToken();
        String token2 = SecretKeyGenerator.generateApiToken();
        
        assertNotEquals(token1, token2, "两次生成的 API Token 应该不同");
    }
}

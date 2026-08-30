package org.unreal.modelrouter.monitor.callhistory.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecordContentCipher 单元测试
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
@DisplayName("RecordContentCipher 测试")
class RecordContentCipherTest {

    private RecordContentCipher cipher;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey key = keyGen.generateKey();
        cipher = new RecordContentCipher(key);
    }

    @Nested
    @DisplayName("加密/解密往返测试")
    class RoundTripTests {

        @Test
        @DisplayName("正常字符串加密解密往返")
        void testEncryptDecryptRoundTrip() {
            String plaintext = "{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}";
            String encrypted = cipher.encrypt(plaintext);

            assertNotNull(encrypted);
            assertNotEquals(plaintext, encrypted);

            String decrypted = cipher.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("中文内容加密解密往返")
        void testEncryptDecryptChineseContent() {
            String plaintext = "你好世界，这是一个测试消息。包含特殊字符：!@#$%^&*()";
            String encrypted = cipher.encrypt(plaintext);
            String decrypted = cipher.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("长文本加密解密往返")
        void testEncryptDecryptLongContent() {
            String plaintext = "A".repeat(10000);
            String encrypted = cipher.encrypt(plaintext);
            String decrypted = cipher.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("空字符串内容加密解密往返")
        void testEncryptDecryptEmptyString() {
            String plaintext = "";
            String encrypted = cipher.encrypt(plaintext);
            assertNull(encrypted, "空字符串应返回 null");

            String decrypted = cipher.decrypt("");
            assertNull(decrypted, "空密文应返回 null");
        }
    }

    @Nested
    @DisplayName("IV 随机性测试")
    class IVRandomnessTests {

        @Test
        @DisplayName("每次加密产生不同的密文（不同 IV）")
        void testDifferentIVEncryptions() {
            String plaintext = "Same content encrypted twice";
            String encrypted1 = cipher.encrypt(plaintext);
            String encrypted2 = cipher.encrypt(plaintext);

            // 同一明文加密两次应产生不同密文（因为 IV 不同）
            assertNotEquals(encrypted1, encrypted2,
                    "相同明文多次加密应产生不同密文（IV 不同）");

            // 但两次都能正确解密
            assertEquals(plaintext, cipher.decrypt(encrypted1));
            assertEquals(plaintext, cipher.decrypt(encrypted2));
        }
    }

    @Nested
    @DisplayName("篡改检测测试")
    class TamperDetectionTests {

        @Test
        @DisplayName("篡改密文应抛出异常")
        void testTamperedCiphertextThrows() {
            String plaintext = "Sensitive data to protect";
            String encrypted = cipher.encrypt(plaintext);

            // 篡改 Base64 编码的密文
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            // 修改密文中间的一个字节
            if (decoded.length > 14) {
                decoded[14] ^= 0xFF;
            }
            String tampered = Base64.getEncoder().encodeToString(decoded);

            assertThrows(RuntimeException.class, () -> cipher.decrypt(tampered),
                    "篡改的密文解密时应抛出异常");
        }

        @Test
        @DisplayName("截断密文应抛出异常")
        void testTruncatedCiphertextThrows() {
            String plaintext = "Some data";
            String encrypted = cipher.encrypt(plaintext);

            // 截断密文
            String truncated = encrypted.substring(0, encrypted.length() / 2);

            assertThrows(Exception.class, () -> cipher.decrypt(truncated),
                    "截断的密文解密时应抛出异常");
        }
    }

    @Nested
    @DisplayName("null/空输入处理测试")
    class NullHandlingTests {

        @Test
        @DisplayName("加密 null 返回 null")
        void testEncryptNull() {
            assertNull(cipher.encrypt(null));
        }

        @Test
        @DisplayName("解密 null 返回 null")
        void testDecryptNull() {
            assertNull(cipher.decrypt(null));
        }
    }

    @Nested
    @DisplayName("密钥来源测试")
    class KeySourceTests {

        @Test
        @DisplayName("auto 模式 - 自动生成并持久化密钥文件")
        void testAutoKeyGeneration(@TempDir Path tempDir) {
            RecordContentCipher autoCipher = new RecordContentCipher("auto", tempDir);
            Path keyFile = tempDir.resolve("call-history.key");

            assertTrue(Files.exists(keyFile), "密钥文件应已创建");

            String plaintext = "Test with auto-generated key";
            String encrypted = autoCipher.encrypt(plaintext);
            String decrypted = autoCipher.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("auto 模式 - 从已有密钥文件加载")
        void testAutoKeyReload(@TempDir Path tempDir) throws Exception {
            // 第一次：生成密钥
            RecordContentCipher cipher1 = new RecordContentCipher("auto", tempDir);
            String encrypted = cipher1.encrypt("test data");

            // 第二次：从文件加载
            RecordContentCipher cipher2 = new RecordContentCipher("auto", tempDir);
            String decrypted = cipher2.decrypt(encrypted);
            assertEquals("test data", decrypted, "重新加载的密钥应能解密之前加密的数据");
        }

        @Test
        @DisplayName("env 模式 - 使用环境变量中的密钥")
        void testEnvKeySourceFallsBackWhenEnvNotSet() {
            // 当环境变量未设置时应回退到 auto 模式（使用 temp dir）
            assertDoesNotThrow(() -> {
                RecordContentCipher envCipher = new RecordContentCipher("env", null);
                // 如果 JAIR_CALL_HISTORY_KEY 环境变量不存在，回退到 auto
                // 这里只验证不抛异常
            });
        }
    }
}

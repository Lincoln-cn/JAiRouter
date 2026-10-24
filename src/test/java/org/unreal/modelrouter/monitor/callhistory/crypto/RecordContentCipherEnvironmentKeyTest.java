package org.unreal.modelrouter.monitor.callhistory.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RecordContentCipher} 的密钥来源接线与只读状态测试。
 *
 * <p>回归背景：3.5.1 之前 {@code @Component} 只有无参构造器被 Spring 使用，
 * {@code jairouter.call-history.encryption-key-source: env} 被静默忽略（总是走 auto 文件密钥），
 * 而配置接口仍原样回显 {@code env}。这里的用例守护「配置 → 实际装配」这条链路，
 * 并守护暴露给控制台的生效值/指纹字段。
 *
 * @author JAiRouter Team
 * @since 3.5.1
 */
@DisplayName("RecordContentCipher 密钥来源接线测试")
class RecordContentCipherEnvironmentKeyTest {

    private static final String ENV_KEY_NAME = "JAIR_CALL_HISTORY_KEY";

    @Test
    @DisplayName("env 模式：环境变量缺失时回退 auto，且 requested/effective 不相等")
    void envRequestedWithoutEnvironmentVariableFallsBackToAuto(@TempDir Path tempDir) {
        RecordContentCipher cipher = new RecordContentCipher("env", tempDir);

        assertEquals("env", cipher.keySourceRequested(), "请求来源应保留配置值，便于控制台对照");
        if (System.getenv(ENV_KEY_NAME) == null) {
            assertEquals("auto", cipher.keySourceEffective(), "环境变量缺失时必须如实标记为回退 auto");
            assertFalse(cipher.environmentKeyPresent());
            assertNotEquals(cipher.keySourceRequested(), cipher.keySourceEffective());
        } else {
            assertEquals("env", cipher.keySourceEffective(), "环境变量存在时应真的使用 env 密钥");
            assertTrue(cipher.environmentKeyPresent());
        }
    }

    @Test
    @DisplayName("Spring 装配构造器：按 CallHistoryProperties 的 encryptionKeySource 选择来源")
    void springConstructorHonorsConfiguredKeySource(@TempDir Path tempDir) {
        CallHistoryProperties properties = new CallHistoryProperties();
        properties.setEncryptionKeySource("env");

        RecordContentCipher cipher = new RecordContentCipher(properties);

        assertEquals("env", cipher.keySourceRequested(),
                "Spring 构造器必须把配置里的 encryption-key-source 传下去，而不是写死 auto");
        assertEquals(System.getenv(ENV_KEY_NAME) == null ? "auto" : "env", cipher.keySourceEffective());
    }

    @Test
    @DisplayName("配置为 auto 或空白时按 auto 处理")
    void autoOrBlankSourceNormalizesToAuto(@TempDir Path tempDir) {
        assertEquals("auto", new RecordContentCipher("auto", tempDir).keySourceRequested());
        assertEquals("auto", new RecordContentCipher("  ", tempDir).keySourceRequested());
        assertEquals("auto", new RecordContentCipher((String) null, tempDir).keySourceRequested());
    }

    @Test
    @DisplayName("直接传入密钥时来源标记为 explicit，且加解密可用")
    void explicitKeyConstructorStillWorks() {
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        RecordContentCipher cipher = new RecordContentCipher(key);

        assertEquals("explicit", cipher.keySourceRequested());
        assertEquals("explicit", cipher.keySourceEffective());
        assertFalse(cipher.environmentKeyPresent());
        assertEquals("明文内容", cipher.decrypt(cipher.encrypt("明文内容")));
    }

    @Test
    @DisplayName("密钥指纹：8 位十六进制、同密钥相同、不同密钥不同，且不含密钥原文")
    void keyFingerprintIsStableAndNonRevealing() {
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        SecretKeySpec otherKey = new SecretKeySpec("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8), "AES");

        String fingerprint = new RecordContentCipher(key).keyFingerprint();
        String sameFingerprint = new RecordContentCipher(key).keyFingerprint();
        String otherFingerprint = new RecordContentCipher(otherKey).keyFingerprint();

        assertNotNull(fingerprint);
        assertTrue(fingerprint.matches("[0-9a-f]{8}"), "指纹应为 8 位小写十六进制，实际: " + fingerprint);
        assertEquals(fingerprint, sameFingerprint);
        assertNotEquals(fingerprint, otherFingerprint);
        assertFalse("0123456789abcdef0123456789abcdef".contains(fingerprint), "指纹不得是密钥原文的片段");
    }

    @Test
    @DisplayName("算法描述固定为 AES-256-GCM")
    void algorithmLabelIsStable() {
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        assertEquals("AES-256-GCM", new RecordContentCipher(key).algorithm());
    }
}

package org.unreal.modelrouter.auth.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #880：未配置固定密钥时必须让人看得出「重启就解不开」，解密失败也要说清原因。
 */
@DisplayName("#880 配置加密的临时密钥")
class ConfigurationEncryptionServiceEphemeralKeyTest {

    @Test
    @DisplayName("880-001 未配置密钥时标记为临时密钥，且同进程内可以往返加解密")
    void ephemeralKeyRoundTripsInSameProcess() {
        final ConfigurationEncryptionService service = new ConfigurationEncryptionService("");

        assertTrue(service.isUsingEphemeralKey());
        final String cipher = service.encrypt("super-secret-value");
        assertEquals("super-secret-value", service.decrypt(cipher));
    }

    @Test
    @DisplayName("880-002 重启后（新的临时密钥）解不开旧密文，异常文案点名要配置的键")
    void restartCannotDecryptWithNewEphemeralKey() {
        final ConfigurationEncryptionService beforeRestart = new ConfigurationEncryptionService("");
        final String cipher = beforeRestart.encrypt("jwt-secret-of-yesterday");

        final ConfigurationEncryptionService afterRestart = new ConfigurationEncryptionService("");
        final var error = assertThrows(
                ConfigurationEncryptionService.ConfigurationEncryptionException.class,
                () -> afterRestart.decrypt(cipher));

        assertTrue(error.getMessage().contains("jairouter.security.encryption.key"),
                "异常文案应点名要配置的键，实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("临时加密密钥"), "实际: " + error.getMessage());
    }

    @Test
    @DisplayName("880-003 配置了固定密钥后跨实例可解密，且不再标记为临时密钥")
    void configuredKeyIsStableAcrossInstances() {
        final byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        final String base64Key = Base64.getEncoder().encodeToString(key);

        final ConfigurationEncryptionService first = new ConfigurationEncryptionService(base64Key);
        final ConfigurationEncryptionService second = new ConfigurationEncryptionService(base64Key);

        assertFalse(first.isUsingEphemeralKey());
        assertEquals("shared-secret", second.decrypt(first.encrypt("shared-secret")));
    }
}

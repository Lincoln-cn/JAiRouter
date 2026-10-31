package org.unreal.modelrouter.auth.security.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator.StrengthLevel;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator.ValidationResult;

/**
 * #879：弱密钥判定要说明命中哪条规则；长口令里的短片段不应被当成弱密钥。
 */
@DisplayName("#879 JWT 密钥强度的判定文案与短片段误判")
class SecretKeyValidatorWeakPatternMessageTest {

    @Test
    @DisplayName("879-001 命中弱片段时，文案点名该片段与占比")
    void messageNamesTheMatchedPattern() {
        final ValidationResult result = SecretKeyValidator.validateJwtSecret(
                "ProdSmoke#2026-10-31-abcdefghijklmnop");

        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("abcdef"),
                "文案必须点名命中的片段，实际: " + result.getMessage());
        assertTrue(result.getMessage().contains("%"),
                "文案应给出占比，便于判断改哪一段，实际: " + result.getMessage());
        assertTrue(result.getMessage().contains("openssl rand"),
                "文案应给出可执行的替代做法，实际: " + result.getMessage());
    }

    @Test
    @DisplayName("879-002 低熵长口令里只出现 key 这类短片段且不占主导时，不再误判为非常弱")
    void shortPatternInLongSecretIsNotVeryWeak() {
        // 该口令熵不高（走「人择密钥」分支），里面出现了 4 次 key，但每个 key 只占 3/35 ≈ 9%
        final ValidationResult result = SecretKeyValidator.validateJwtSecret(
                "prodkey-prodkey-prodkey-prodkey-pro");

        assertNotEquals(StrengthLevel.VERY_WEAK, result.getStrengthLevel(),
                "短片段不占主导时不应判非常弱，实际: " + result.getMessage());
    }

    @Test
    @DisplayName("879-003 真正的人择弱密钥（mysecretkey）仍然判非常弱，而且点名 secret")
    void genuinelyWeakSecretStillFlagged() {
        final ValidationResult result = SecretKeyValidator.validateJwtSecret("mysecretkey");

        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("secret"), "实际: " + result.getMessage());
    }

    @Test
    @DisplayName("879-004 出厂默认密钥的文案直接给出该密钥自身")
    void defaultKeyMessageShowsTheKey() {
        final ValidationResult result = SecretKeyValidator.validateJwtSecret("ChangeMeOnFirstStartup123456");

        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("ChangeMeOnFirstStartup123456"),
                "默认密钥应被原样点名，实际: " + result.getMessage());
    }
}

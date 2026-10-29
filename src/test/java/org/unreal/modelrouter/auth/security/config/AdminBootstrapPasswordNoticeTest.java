package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdminBootstrapPasswordNotice} 的单元测试（issue #802）。
 *
 * <p>守护的核心不变量：**只有「本次真的创建了账户」时才允许打印明文口令**。
 * 之前的实现由 {@code StartupSecretKeyChecker} 无条件打印当次 YAML 里的随机口令，
 * 而 {@code AccountManager} 库内优先，于是重启后打印出来的口令一定登录失败。</p>
 */
class AdminBootstrapPasswordNoticeTest {

    @Test
    @DisplayName("首次创建账户且未设环境变量：打印 YAML 里的 {noop} 明文口令")
    void announcesPlaintextOnFirstCreation() {
        final Optional<String> announced =
                AdminBootstrapPasswordNotice.plaintextToAnnounce(true, false, "{noop}First-Boot#2026");

        assertEquals(Optional.of("First-Boot#2026"), announced);
    }

    @Test
    @DisplayName("回归：库中已有账户（本次未创建）时不再打印任何口令")
    void doesNotAnnounceWhenAccountAlreadyExisted() {
        final Optional<String> announced =
                AdminBootstrapPasswordNotice.plaintextToAnnounce(false, false, "{noop}RandomUuidThatIsNotInDb");

        assertTrue(announced.isEmpty(),
                "重启时库里的口令来自首次初始化，本次随机口令不生效，打印它就是误导");
    }

    @Test
    @DisplayName("显式设置 INITIAL_ADMIN_PASSWORD 时由运维自己掌握口令，不再打印")
    void doesNotAnnounceWhenEnvVarProvided() {
        assertTrue(AdminBootstrapPasswordNotice.plaintextToAnnounce(true, true, "{noop}whatever").isEmpty());
    }

    @Test
    @DisplayName("已编码口令（如 bcrypt）不可还原，不打印")
    void doesNotAnnounceEncodedPasswords() {
        assertTrue(AdminBootstrapPasswordNotice
                .plaintextToAnnounce(true, false, "$2a$10$abcdefghijklmnopqrstuv").isEmpty());
        assertTrue(AdminBootstrapPasswordNotice.plaintextToAnnounce(true, false, null).isEmpty());
    }

    @Test
    @DisplayName("{noop} 后为空白时不打印空口令")
    void doesNotAnnounceEmptyPlaintext() {
        assertTrue(AdminBootstrapPasswordNotice.plaintextToAnnounce(true, false, "{noop}").isEmpty());
    }

    @Test
    @DisplayName("已初始化提示明确说明本次没有可用的新口令")
    void alreadyInitializedHintExplainsNoNewPassword() {
        final String hint = AdminBootstrapPasswordNotice.alreadyInitializedHint(3L);

        assertTrue(hint.contains("3"), "提示里要带账号数量，方便运维判断库状态");
        assertTrue(hint.contains("不会") && hint.contains("口令"),
                "提示必须说清「本次不再生成口令」，而不是只记一行 skipping");
        assertFalse(hint.contains("{noop}"), "提示里不得出现任何口令形态的内容");
    }
}

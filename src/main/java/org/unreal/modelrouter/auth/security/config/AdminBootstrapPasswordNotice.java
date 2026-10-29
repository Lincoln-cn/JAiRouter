package org.unreal.modelrouter.auth.security.config;

import java.util.Optional;

/**
 * 首次启动管理员口令的「该不该打印」判定（issue #802）。
 *
 * <p>背景：{@code config/auth/jwt.yml} 在缺 {@code INITIAL_ADMIN_PASSWORD} 时用
 * {@code ${random.uuid}} 生成管理员口令，而 {@code JpaDatabaseInitializer} 只在
 * {@code jwt_accounts} 表为空时把 YAML 账户写库、{@code AccountManager} 又库内优先。
 * 于是「库已有账户」的重启场景里，本次随机口令根本不会生效 —— 早期实现仍然把它打印成
 * 「管理员口令」，让用户拿着一个必然失败的密码去登录。
 *
 * <p>把判定抽成纯函数的原因：这段逻辑要在没有数据库的单元测试里被钉住，
 * 而真正的打印只发生在「确实创建了账户」的那一刻（{@code JpaDatabaseInitializer}）。
 */
public final class AdminBootstrapPasswordNotice {

    private static final String NOOP_PREFIX = "{noop}";

    private AdminBootstrapPasswordNotice() {
    }

    /**
     * 计算本次启动应当打印的明文管理员口令。
     *
     * @param createdNow 本次启动是否真的把该账户写进了数据库
     * @param envVarSet  是否显式设置了 {@code INITIAL_ADMIN_PASSWORD}
     * @param password   YAML 中配置的口令（可能是 {@code {noop}} 明文或已编码）
     * @return 需要打印的明文口令；空表示不该打印
     */
    public static Optional<String> plaintextToAnnounce(final boolean createdNow,
                                               final boolean envVarSet,
                                               final String password) {
        if (!createdNow || envVarSet) {
            return Optional.empty();
        }
        if (password == null || !password.startsWith(NOOP_PREFIX)) {
            return Optional.empty();
        }
        final String plaintext = password.substring(NOOP_PREFIX.length());
        return plaintext.isEmpty() ? Optional.empty() : Optional.of(plaintext);
    }

    /**
     * 账户已存在时的提示：不再打印随机口令，明确告诉运维这次没有生成新口令。
     *
     * @param accountCount 库中已有账户数
     * @return 固定文案
     */
    public static String alreadyInitializedHint(final long accountCount) {
        return "检测到数据库已有 " + accountCount + " 个 JWT 账号，本次不再生成随机管理员口令"
                + "（日志里不会再出现可用口令）：请使用首次启动时打印的口令，"
                + "或在控制台重置；两者都不可用时需清理数据目录后重新初始化。";
    }
}

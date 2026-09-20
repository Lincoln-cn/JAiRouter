package org.unreal.modelrouter.auth.security.config;

import java.util.Locale;

/**
 * 生产环境启动安全门闩（R4-P1）。
 *
 * <p>密钥/密码检查失败时在生产必须 fail-fast，禁止只打日志后带着默认密钥上线。
 * 逃生舱：{@code JAIRouter_SKIP_AUTH_WARNING=true}（需显式设置）。</p>
 */
public final class StartupSecurityGate {

    /** 出厂默认管理员密码（与 jwt.yml / SecretKeyValidator 一致） */
    public static final String DEFAULT_ADMIN_PASSWORD = "ChangeMeOnFirstStartup123456";

    /** 出厂默认 JWT 密钥（与 config/auth/jwt.yml 一致） */
    public static final String DEFAULT_JWT_SECRET = "ThisIsADefaultSecretKeyForDevOnly12345678";

    /** 显式跳过生产安全检查的环境变量 */
    public static final String SKIP_ENV = "JAIRouter_SKIP_AUTH_WARNING";

    private StartupSecurityGate() {
    }

    /**
     * 是否应在生产 fail-fast。
     */
    public static boolean shouldFailFast(final boolean production,
                                         final boolean checksPassed,
                                         final String skipAuthWarning) {
        return production && !checksPassed && !"true".equalsIgnoreCase(skipAuthWarning);
    }

    /**
     * 执行门闩：该失败时抛 {@link IllegalStateException}。
     */
    public static void enforce(final boolean production,
                               final boolean checksPassed,
                               final String skipAuthWarning) {
        if (shouldFailFast(production, checksPassed, skipAuthWarning)) {
            throw new IllegalStateException(
                    "Production security startup checks failed. "
                            + "Set JWT_SECRET and INITIAL_ADMIN_PASSWORD to strong values, "
                            + "or set " + SKIP_ENV + "=true only for trusted isolated networks.");
        }
    }

    /**
     * 管理员密码是否可接受。
     */
    public static boolean adminPasswordOk(final boolean production, final String password) {
        if (password == null || password.isBlank()) {
            // 生产必须配置；开发仅告警
            return !production;
        }
        if (isDefaultAdminPassword(password)) {
            return !production;
        }
        return true;
    }

    /**
     * JWT 密钥是否可接受（空值/已知默认值在生产失败）。
     */
    public static boolean jwtSecretOk(final boolean production, final String secret) {
        if (secret == null || secret.isBlank()) {
            return !production;
        }
        if (DEFAULT_JWT_SECRET.equals(secret.trim())) {
            return !production;
        }
        return true;
    }

    /**
     * 是否为出厂默认管理员密码。
     */
    public static boolean isDefaultAdminPassword(final String password) {
        return password != null && DEFAULT_ADMIN_PASSWORD.equals(password.trim());
    }

    /**
     * 是否命中已知默认 JWT 密钥（含 dev 文档默认值）。
     */
    public static boolean isDefaultJwtSecret(final String secret) {
        if (secret == null) {
            return false;
        }
        String value = secret.trim();
        return DEFAULT_JWT_SECRET.equals(value)
                || "dev-secret-key-for-testing-only-32-characters-minimum".equals(value)
                || value.toLowerCase(Locale.ROOT).contains("devonly");
    }
}

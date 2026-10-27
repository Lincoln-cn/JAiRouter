package org.unreal.modelrouter.auth.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator.ValidationResult;

import java.util.Arrays;
import java.util.List;

/**
 * 启动时密钥安全检查器
 * 在应用启动时验证 JWT 密钥和密码的强度
 *
 * 启用条件：
 * - jairouter.security.startup-check.enabled=true（默认启用）
 * - 生产环境自动启用
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.startup-check.enabled", havingValue = "true", matchIfMissing = true)
public class StartupSecretKeyChecker implements CommandLineRunner {

    private static final String NOOP_PREFIX = "{noop}";

    private static final String ASCII_ART_BORDER = "\n"
            + "╔══════════════════════════════════════════════════════════════════════════════╗\n"
            + "║                                                                              ║\n"
            + "║                            JAiRouter 安全密钥检查                             ║\n"
            + "║                                                                              ║\n"
            + "╚══════════════════════════════════════════════════════════════════════════════╝\n";

    @Autowired
    private Environment environment;

    @Autowired
    private SecurityProperties securityProperties;

    @Override
    public void run(final String... args) throws Exception {
        log.info(ASCII_ART_BORDER);

        boolean allPassed = true;
        boolean isProduction = isProductionEnvironment();

        // 生产环境检查 JWT 是否启用
        allPassed &= checkJwtEnabledForProduction(isProduction);

        // 检查 JWT 密钥
        allPassed &= checkJwtSecrets();

        // 检查管理员密码
        allPassed &= checkAdminPassword();

        // #664：非生产环境缺 INITIAL_ADMIN_PASSWORD 时，把本次随机生成的口令打印一次，
        // 否则「配置里没有公开口令」会让非 dev/prod 的 profile 变成起得来但登不进。
        reportGeneratedAdminPassword(isProduction);

        if (!allPassed) {
            log.error("\n"
                + "╔══════════════════════════════════════════════════════════════════════════════╗\n"
                + "║  ⚠️  安全警告：检测到弱密钥或弱密码                                          ║\n"
                + "║                                                                              ║\n"
                + "║  在生产环境使用之前，请务必：                                                ║\n"
                + "║  1. 设置强密钥：export JWT_SECRET=\"<随机生成的 32+ 字节密钥>\"                    ║\n"
                + "║  2. 设置强密码：export INITIAL_ADMIN_PASSWORD=\"<复杂密码>\"                     ║\n"
                + "║  3. 启用 JWT 认证：jairouter.security.jwt.enabled=true                       ║\n"
                + "║  4. 使用密钥生成工具：java -jar jairouter.jar --generate-key                 ║\n"
                + "║                                                                              ║\n"
                + "║  密钥生成工具使用说明：                                                      ║\n"
                + "║  --generate-key              生成 Base64 编码的 JWT 密钥                          ║\n"
                + "║  --generate-api-token        生成 API Token                                  ║\n"
                + "║  --generate-password         生成随机密码                                    ║\n"
                + "╚══════════════════════════════════════════════════════════════════════════════╝\n");
        } else {
            log.info("\n"
                + "╔══════════════════════════════════════════════════════════════════════════════╗\n"
                + "║  ✓ 所有密钥和密码检查通过                                                    ║\n"
                + "╚══════════════════════════════════════════════════════════════════════════════╝\n");
        }

        // R4-P1：生产 fail-fast — 检查失败不得带着默认密钥/弱配置继续启动
        StartupSecurityGate.enforce(isProduction, allPassed,
                System.getenv(StartupSecurityGate.SKIP_ENV));
    }

    /**
     * 非生产环境缺 {@code INITIAL_ADMIN_PASSWORD} 时，打印本次启动实际生效的随机管理员口令。
     *
     * <p>背景（#664）：{@code config/auth/jwt.yml} 不再回落到公开文档口令，缺环境变量时用
     * {@code ${random.uuid}} 随机生成（每次启动不同）。若不打印，非 dev/prod 的 profile 会变成
     * “起得来、但不知道用什么口令登录”的死局；这里把用户名与明文口令打印一次，并提示如何固定下来。</p>
     *
     * <p>只在「非生产 + INITIAL_ADMIN_PASSWORD / ADMIN_PASSWORD 都没设」时打印；生产环境由
     * {@link StartupSecurityGate} 直接拒绝启动，不会走到这里。只打印 {@code {noop}} 明文口令，
     * 已编码（如 {@code {bcrypt}}）的口令不还原、不打印。</p>
     */
    private void reportGeneratedAdminPassword(final boolean isProduction) {
        if (isProduction || isEnvSet("INITIAL_ADMIN_PASSWORD") || isEnvSet("ADMIN_PASSWORD")) {
            return;
        }
        final JwtConfig jwt = securityProperties.getJwt();
        final List<JwtAccountProperties> accounts = jwt == null ? null : jwt.getAccounts();
        if (accounts == null || accounts.isEmpty()) {
            return;
        }
        for (final JwtAccountProperties account : accounts) {
            if (!isAdminAccount(account)) {
                continue;
            }
            final String password = account.getPassword();
            if (password == null || !password.startsWith(NOOP_PREFIX)) {
                continue;
            }
            log.warn("=======================================================================");
            log.warn(" 本次启动生成的管理员口令（随机生成、每次启动都不同）");
            log.warn(" 用户名：{}", account.getUsername());
            log.warn(" 密码  ：{}", password.substring(NOOP_PREFIX.length()));
            log.warn(" 要固定下来：设置环境变量 INITIAL_ADMIN_PASSWORD 后重启。");
            log.warn(" 生产环境（prod / production）必须设置，否则启动门闩会拒绝启动。");
            log.warn("=======================================================================");
        }
    }

    private static boolean isAdminAccount(final JwtAccountProperties account) {
        return account != null && account.isEnabled()
                && account.getRoles() != null && account.getRoles().contains("ADMIN");
    }

    private static boolean isEnvSet(final String name) {
        final String value = System.getenv(name);
        return value != null && !value.isEmpty();
    }

    /**
     * 检查是否为生产环境
     */
    private boolean isProductionEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.stream(activeProfiles)
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
    }

    /**
     * 生产环境检查 JWT 是否启用
     */
    private boolean checkJwtEnabledForProduction(final boolean isProduction) {
        if (!isProduction) {
            log.info("当前环境：开发/测试环境，跳过 JWT 启用检查");
            return true;
        }

        log.info("当前环境：生产环境，检查 JWT 认证配置...");

        boolean jwtEnabled = securityProperties.getJwt() != null
                && securityProperties.getJwt().isEnabled();

        if (!jwtEnabled) {
            log.error("\n"
                + "╔══════════════════════════════════════════════════════════════════════════════╗\n"
                + "║  🚨 严重安全警告：生产环境 JWT 认证未启用！                                   ║\n"
                + "║                                                                              ║\n"
                + "║  这意味着所有 API 端点都将没有认证保护，存在极高的安全风险！                  ║\n"
                + "║                                                                              ║\n"
                + "║  请立即采取以下措施之一：                                                    ║\n"
                + "║  1. 启用 JWT 认证：                                                          ║\n"
                + "║     export JWT_SECRET=\"<your-secret-key>\"                                   ║\n"
                + "║     在 application-prod.yml 中设置：                                         ║\n"
                + "║     jairouter.security.jwt.enabled: true                                     ║\n"
                + "║                                                                              ║\n"
                + "║  2. 或配置其他认证方式（如 API Key、OAuth2）                                 ║\n"
                + "║                                                                              ║\n"
                + "║  如果您确定要在无认证情况下运行（如内网环境），请设置：                       ║\n"
                + "║     export " + StartupSecurityGate.SKIP_ENV + "=true"
                + " ".repeat(Math.max(0, 61 - StartupSecurityGate.SKIP_ENV.length())) + "║\n"
                + "╚══════════════════════════════════════════════════════════════════════════════╝\n");

            // 检查是否设置了跳过警告的环境变量。
            // 引用同一个常量（issue #201）：此前这里另写了一个拼写 JAROUTER_SKIP_AUTH_WARNING，
            // 而失败路径读的是 StartupSecurityGate.SKIP_ENV，导致"按提示设置却不生效"。
            String skipWarning = System.getenv(StartupSecurityGate.SKIP_ENV);
            if ("true".equalsIgnoreCase(skipWarning)) {
                log.warn("已设置 {}=true，跳过认证警告", StartupSecurityGate.SKIP_ENV);
                return true;
            }
            return false;
        }

        log.info("✓ 生产环境 JWT 认证已启用");
        return true;
    }

    /**
     * 检查 JWT 密钥配置
     */
    private boolean checkJwtSecrets() {
        log.info("正在检查 JWT 密钥配置...");

        // 检查主 JWT 密钥
        String jwtSecret = System.getenv("DEV_JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            jwtSecret = System.getenv("JWT_SECRET");
        }

        if (jwtSecret == null || jwtSecret.isEmpty()) {
            // #664：基础配置已不再回落到公开默认密钥，缺环境变量时用 ${random.uuid} 随机生成
            // ⇒ 这里的提示必须如实说明「本次启动用的是一个一次性密钥」，而不是「默认配置」。
            log.warn("⚠️  未设置 JWT_SECRET 环境变量：本次启动使用随机生成的一次性 JWT 密钥"
                    + "（重启后旧令牌全部失效；仅限非生产）");
            log.warn("   生产环境必须设置 JWT_SECRET 环境变量！");
            // R4-P1：生产缺密钥 → 失败
            return StartupSecurityGate.jwtSecretOk(isProductionEnvironment(), null);
        }

        // R4-P1：生产拒绝已知默认 JWT 密钥
        if (isProductionEnvironment() && StartupSecurityGate.isDefaultJwtSecret(jwtSecret)) {
            log.error("❌ JWT 密钥仍为出厂/文档默认值，生产环境禁止使用");
            return false;
        }

        ValidationResult result = SecretKeyValidator.validateJwtSecret(jwtSecret);

        switch (result.getStrengthLevel()) {
            case VERY_WEAK:
            case WEAK:
                log.error("❌ JWT 密钥强度：{} - {}", result.getStrengthLevel().getDisplayName(), result.getMessage());
                return false;
            case MEDIUM:
                log.warn("⚠️  JWT 密钥强度：{} - {}", result.getStrengthLevel().getDisplayName(), result.getMessage());
                log.warn("   建议用于生产环境前更换为更强的密钥");
                return true;  // 允许启动，但发出警告
            case STRONG:
            case VERY_STRONG:
                log.info("✓ JWT 密钥强度：{} - 通过检查", result.getStrengthLevel().getDisplayName());
                return true;
            default:
                return true;
        }
    }

    /**
     * 检查管理员密码
     */
    private boolean checkAdminPassword() {
        log.info("正在检查管理员密码配置...");

        String adminPassword = System.getenv("INITIAL_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isEmpty()) {
            adminPassword = System.getenv("ADMIN_PASSWORD");
        }

        if (adminPassword == null || adminPassword.isEmpty()) {
            // #664：非生产环境会为 admin 随机生成一次性口令（稍后由 reportGeneratedAdminPassword 打印）
            log.warn("⚠️  未设置 INITIAL_ADMIN_PASSWORD 环境变量"
                    + "（非生产环境将随机生成一次性口令并打印在下方日志；生产环境必须设置）");
            // R4-P1：生产必须配置管理员密码
            return StartupSecurityGate.adminPasswordOk(isProductionEnvironment(), null);
        }

        // R4-P1：默认出厂密码在生产视为失败
        if (StartupSecurityGate.isDefaultAdminPassword(adminPassword)
                && isProductionEnvironment()) {
            log.error("❌ 管理员密码仍为出厂默认值，生产环境禁止使用");
            return false;
        }

        ValidationResult result = SecretKeyValidator.validatePassword(adminPassword);

        switch (result.getStrengthLevel()) {
            case VERY_WEAK:
            case WEAK:
                log.error("❌ 管理员密码强度：{} - {}", result.getStrengthLevel().getDisplayName(), result.getMessage());
                return false;
            case MEDIUM:
                log.warn("⚠️  管理员密码强度：{} - {}", result.getStrengthLevel().getDisplayName(), result.getMessage());
                return true;
            case STRONG:
            case VERY_STRONG:
                log.info("✓ 管理员密码强度：{} - 通过检查", result.getStrengthLevel().getDisplayName());
                return true;
            default:
                return true;
        }
    }
}
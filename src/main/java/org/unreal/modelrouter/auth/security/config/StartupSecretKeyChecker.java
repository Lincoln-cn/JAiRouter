package org.unreal.modelrouter.auth.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator.ValidationResult;

import java.util.Arrays;

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
                + "║     export JAROUTER_SKIP_AUTH_WARNING=true                                   ║\n"
                + "╚══════════════════════════════════════════════════════════════════════════════╝\n");

            // 检查是否设置了跳过警告的环境变量
            String skipWarning = System.getenv("JAROUTER_SKIP_AUTH_WARNING");
            if ("true".equalsIgnoreCase(skipWarning)) {
                log.warn("已设置 JAROUTER_SKIP_AUTH_WARNING=true，跳过认证警告");
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
            log.warn("⚠️  未设置 JWT_SECRET 环境变量，将使用默认配置（仅限开发环境）");
            log.warn("   生产环境必须设置 JWT_SECRET 环境变量！");
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
            log.warn("⚠️  未设置 INITIAL_ADMIN_PASSWORD 环境变量");
            log.warn("   首次启动建议设置管理员密码，或使用默认密码（仅限开发环境）");
            log.warn("   默认密码将在后续版本中移除");
            return true;  // 不阻止启动，但发出警告
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
package org.unreal.modelrouter.auth.security.checker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 密码安全检查器
 * 在应用启动时检查密码安全配置，对明文密码发出警告
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class PasswordSecurityChecker {

    private final SecurityProperties securityProperties;
    private final Environment environment;
    private final PasswordEncoder passwordEncoder;

    private static final String NOOP_PREFIX = "{noop}";
    private static final String BCrypt_PREFIX = "{bcrypt}";
    private static final String SKIP_WARNING_ENV = "JAROUTER_SKIP_PASSWORD_WARNING";

    @Autowired
    public PasswordSecurityChecker(final SecurityProperties securityProperties,
                                    final Environment environment,
                                    final PasswordEncoder passwordEncoder) {
        this.securityProperties = securityProperties;
        this.environment = environment;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 应用启动后检查密码安全配置
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkPasswordSecurity() {
        if (isSkipWarningEnabled()) {
            log.debug("密码安全警告已跳过");
            return;
        }

        if (!securityProperties.getJwt().isEnabled()) {
            return;
        }

        List<JwtAccountProperties> accounts = securityProperties.getJwt().getAccounts();
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        int plaintextCount = 0;
        int totalAccounts = accounts.size();

        for (JwtAccountProperties account : accounts) {
            String password = account.getPassword();
            if (password != null && password.startsWith(NOOP_PREFIX)) {
                plaintextCount++;
                log.warn("账户 {} 使用明文密码，建议升级为BCrypt加密", account.getUsername());
            }
        }

        if (plaintextCount > 0) {
            logSecurityWarning(plaintextCount, totalAccounts);
        }

        // 生产环境强制检查
        if (isProductionEnvironment() && plaintextCount > 0) {
            logProductionWarning();
        }
    }

    /**
     * 检查是否跳过警告
     */
    private boolean isSkipWarningEnabled() {
        String skipWarning = environment.getProperty(SKIP_WARNING_ENV, "false");
        return "true".equalsIgnoreCase(skipWarning);
    }

    /**
     * 检查是否为生产环境
     */
    private boolean isProductionEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("prod");
    }

    /**
     * 记录安全警告
     */
    private void logSecurityWarning(final int plaintextCount, final int totalAccounts) {
        log.warn("╔══════════════════════════════════════════════════════════════════╗");
        log.warn("║                      密码安全警告                                ║");
        log.warn("║  检测到 {}/{} 个账户使用明文密码                              ║", plaintextCount, totalAccounts);
        log.warn("║  明文密码在配置文件中可见，存在安全风险                         ║");
        log.warn("║                                                                  ║");
        log.warn("║  建议操作：                                                      ║");
        log.warn("║  1. 使用环境变量注入密码，避免硬编码                             ║");
        log.warn("║  2. 使用 BCrypt 加密密码：{bcrypt}$2a$10$...                    ║");
        log.warn("║  3. 运行密码加密工具生成安全的密码哈希                           ║");
        log.warn("║                                                                  ║");
        log.warn("║  跳过警告：export JAROUTER_SKIP_PASSWORD_WARNING=true            ║");
        log.warn("╚══════════════════════════════════════════════════════════════════╝");
    }

    /**
     * 生产环境警告
     */
    private void logProductionWarning() {
        log.error("╔══════════════════════════════════════════════════════════════════╗");
        log.error("║              ⚠️  生产环境密码安全警告 ⚠️                         ║");
        log.error("║  生产环境禁止使用明文密码！                                     ║");
        log.error("║  请立即升级为 BCrypt 加密密码                                   ║");
        log.error("║  或设置环境变量 JAROUTER_SKIP_PASSWORD_WARNING=true 跳过检查    ║");
        log.error("╚══════════════════════════════════════════════════════════════════╝");
    }

    /**
     * 生成BCrypt密码哈希
     * 用于手动生成安全的密码
     *
     * @param plaintextPassword 明文密码
     * @return BCrypt加密后的密码（带{bcrypt}前缀）
     */
    public String generateBcryptPassword(final String plaintextPassword) {
        String encoded = passwordEncoder.encode(plaintextPassword);
        return BCrypt_PREFIX + encoded;
    }
}

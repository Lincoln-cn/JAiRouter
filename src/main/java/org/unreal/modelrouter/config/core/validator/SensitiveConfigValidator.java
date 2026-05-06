package org.unreal.modelrouter.config.core.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感配置校验器
 * 在应用启动时校验必须的环境变量配置
 *
 * <p>v2.8.5 新增：确保敏感配置不被硬编码，强制使用环境变量</p>
 *
 * <h3>必须配置的环境变量：</h3>
 * <ul>
 *   <li>JWT_SECRET - JWT 密钥，长度 ≥32 字符</li>
 *   <li>INITIAL_ADMIN_PASSWORD - 初始管理员密码</li>
 *   <li>GPUSTACK_API_TOKEN - 模型服务 API Token（如使用 GPUStack）</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "jairouter.config.validation.enabled", havingValue = "true", matchIfMissing = true)
public class SensitiveConfigValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(SensitiveConfigValidator.class);

    @Value("${JWT_SECRET:}")
    private String jwtSecret;

    @Value("${INITIAL_ADMIN_PASSWORD:}")
    private String initialAdminPassword;

    @Value("${GPUSTACK_API_TOKEN:}")
    private String gpustackApiToken;

    @Value("${jairouter.config.validation.strict-mode:false}")
    private boolean strictMode;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 校验 JWT_SECRET
        validateJwtSecret(warnings, errors);

        // 校验 INITIAL_ADMIN_PASSWORD
        validateInitialAdminPassword(warnings, errors);

        // 校验 GPUSTACK_API_TOKEN（非必须，但强烈推荐）
        validateGpustackApiToken(warnings);

        // 输出结果
        if (!warnings.isEmpty()) {
            log.warn("====== 配置警告 ======");
            warnings.forEach(w -> log.warn("  ⚠️ {}", w));
            log.warn("建议：参考 .env.example 设置环境变量");
            log.warn("====== 警告结束 ======");
        }

        if (!errors.isEmpty() && strictMode) {
            log.error("====== 配置错误 ======");
            errors.forEach(e -> log.error("  ❌ {}", e));
            log.error("====== 错误结束 ======");
            throw new IllegalStateException("敏感配置校验失败，应用无法启动。请设置必要的环境变量。");
        }
    }

    private void validateJwtSecret(List<String> warnings, List<String> errors) {
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            if (strictMode) {
                errors.add("JWT_SECRET 未设置（必须配置）");
            } else {
                warnings.add("JWT_SECRET 未设置（开发环境可忽略，生产环境必须配置）");
            }
        } else if (jwtSecret.length() < 32) {
            if (strictMode) {
                errors.add("JWT_SECRET 长度过短（必须 ≥32 字符，当前: " + jwtSecret.length() + ")");
            } else {
                warnings.add("JWT_SECRET 长度过短（建议 ≥32 字符，当前: " + jwtSecret.length() + ")");
            }
        } else if (isHardcodedSecret(jwtSecret)) {
            errors.add("JWT_SECRET 看起来是硬编码值（请使用环境变量）");
        } else {
            log.info("✓ JWT_SECRET 配置正确（长度: {} 字符）", jwtSecret.length());
        }
    }

    private void validateInitialAdminPassword(List<String> warnings, List<String> errors) {
        if (initialAdminPassword == null || initialAdminPassword.isEmpty()) {
            if (strictMode) {
                errors.add("INITIAL_ADMIN_PASSWORD 未设置（必须配置）");
            } else {
                warnings.add("INITIAL_ADMIN_PASSWORD 未设置（首次启动将生成随机密码）");
            }
        } else if (initialAdminPassword.length() < 8) {
            warnings.add("INITIAL_ADMIN_PASSWORD 长度过短（建议 ≥8 字符，当前: " + initialAdminPassword.length() + ")");
        } else if (isHardcodedSecret(initialAdminPassword)) {
            warnings.add("INITIAL_ADMIN_PASSWORD 看起来是硬编码值（请使用环境变量）");
        } else {
            log.info("✓ INITIAL_ADMIN_PASSWORD 已配置");
        }
    }

    private void validateGpustackApiToken(List<String> warnings) {
        if (gpustackApiToken == null || gpustackApiToken.isEmpty()) {
            warnings.add("GPUSTACK_API_TOKEN 未设置（模型服务将无法认证）");
        } else if (isHardcodedSecret(gpustackApiToken)) {
            warnings.add("GPUSTACK_API_TOKEN 看起来是硬编码值（请使用环境变量）");
        } else {
            log.info("✓ GPUSTACK_API_TOKEN 已配置");
        }
    }

    /**
     * 检查是否是硬编码的秘密值
     * 检测常见的硬编码模式：占位符、测试值等
     */
    private boolean isHardcodedSecret(String secret) {
        if (secret == null) {
            return false;
        }

        String lowerSecret = secret.toLowerCase();

        // 检测常见占位符
        return lowerSecret.contains("your-") ||
               lowerSecret.contains("placeholder") ||
               lowerSecret.contains("example") ||
               lowerSecret.contains("test") ||
               lowerSecret.contains("demo") ||
               lowerSecret.contains("changeme") ||
               lowerSecret.equals("secret") ||
               lowerSecret.equals("password");
    }
}
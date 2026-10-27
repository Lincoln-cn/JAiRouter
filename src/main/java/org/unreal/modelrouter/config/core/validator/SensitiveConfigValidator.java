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
 *
 * <h3>两个桶的语义（issue #381 起）：</h3>
 * <ul>
 *   <li><b>warnings</b>：一律打印；</li>
 *   <li><b>errors</b>：**一律打印**（打印与阻断解耦），但**只有 {@code strict-mode=true} 才阻断启动</b>
 *       —— 此前 errors 在非严格模式下既不打印也不阻断，错误级发现对运维不可见。</li>
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

        // issue #381：错误桶**一律打印**（打印与阻断解耦）。
        // 此前 errors 只在 strictMode 下才被处理 ⇒ 默认模式（strict-mode=false）里
        // 「JWT_SECRET 看起来是硬编码值」这类错误级发现既不打印也不阻断，运维完全看不到。
        if (!errors.isEmpty()) {
            log.error("====== 配置错误 ======");
            errors.forEach(e -> log.error("  ❌ {}", e));
            log.error("====== 错误结束 ======");
        }

        if (!errors.isEmpty() && strictMode) {
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
                warnings.add(missingAdminPasswordWarning());
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
     * 本仓历史上用过的公开出厂默认管理员口令。
     *
     * <p>基础配置 {@code config/auth/jwt.yml} 已不再用它兜底（issue #664）—— 缺失环境变量时回落为
     * {@code ${random.uuid}}，由 {@code StartupSecretKeyChecker} 把本次生成的一次性口令打印在启动日志里。
     * 该常量保留在此，只用于文案里点名「这个已知值会被门闩拒绝」，避免运维误以为把它设进去就能通过校验。</p>
     */
    static final String FACTORY_DEFAULT_ADMIN_PASSWORD = "ChangeMeOnFirstStartup123456";

    /**
     * 未设置 {@code INITIAL_ADMIN_PASSWORD} 时的告警文案（包内可见：供测试断言，issue #314 / #664）。
     *
     * <p><b>这段文案必须与真实行为一致</b>：非生产 profile 下 JWT 账户口令回落为 {@code ${random.uuid}}，
     * 并由 {@code StartupSecretKeyChecker} 在启动日志里打印一次（重启即换新值）；生产 profile 下
     * {@code StartupSecurityGate} 会因此**直接启动失败**。唯一显式生成器是 CLI
     * {@code --generate-password}（{@code KeyGeneratorCommandLine}）。</p>
     *
     * @return 描述真实后果的告警文案
     */
    static String missingAdminPasswordWarning() {
        return "INITIAL_ADMIN_PASSWORD 未设置（非生产环境会随机生成一次性管理员口令并打印在启动日志里，"
                + "生产 profile 会直接启动失败；已知出厂默认值 " + FACTORY_DEFAULT_ADMIN_PASSWORD
                + " 会被门闩拒绝 —— 请设置该环境变量，或用 --generate-password 生成一个强密码）";
    }

    /**
     * 检查是否是硬编码的秘密值
     * 检测常见的硬编码模式：占位符、测试值等
     * 
     * @param secret 要检查的密钥值
     * @return true 如果是硬编码值，false 如果是合法密钥
     */
    public boolean isHardcodedSecret(String secret) {
        if (secret == null) {
            return false;
        }

        String lowerSecret = secret.toLowerCase();

        // 检测常见占位符
        return lowerSecret.contains("your-")
               || lowerSecret.contains("placeholder")
               || lowerSecret.contains("example")
               || lowerSecret.contains("test")
               || lowerSecret.contains("demo")
               || lowerSecret.contains("changeme")
               || lowerSecret.equals("secret")
               || lowerSecret.equals("password");
    }
}
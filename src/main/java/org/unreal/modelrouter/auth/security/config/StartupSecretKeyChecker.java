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
import java.util.List;
import java.util.regex.Pattern;

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


    /**
     * 已移除的兼容变量名（#674）。这些名字在配置绑定里从来就不生效或已不再绑定，
     * 现在只用于启动时提醒运维「别再设它」。
     */
    static final List<String> REMOVED_ENV_NAMES = List.of(
            "DEV_JWT_SECRET",
            "STAGING_JWT_SECRET",
            "PROD_JWT_SECRET",
            "ADMIN_PASSWORD");

    /**
     * 从一组环境变量名里挑出仍在使用「已移除的兼容名字」的那些。
     *
     * <p>抽成纯函数是为了可单测：{@code System.getenv()} 在测试里不可控。</p>
     *
     * @param envNames 环境变量名集合（生产调用处传 {@code System.getenv().keySet()}）
     * @return 命中的已移除变量名，按 {@link #REMOVED_ENV_NAMES} 的顺序；无命中则为空列表
     */
    static List<String> removedEnvNamesPresent(final java.util.Set<String> envNames) {
        if (envNames == null || envNames.isEmpty()) {
            return List.of();
        }
        return REMOVED_ENV_NAMES.stream().filter(envNames::contains).toList();
    }

    /**
     * {@code ${random.uuid}} 生成值的形态（#1247）。配置项写成 {@code ${JWT_SECRET:${random.uuid}}}
     * 时，没设环境变量就会拿到一个 UUID —— 它看起来像强密钥，但其实是「每次启动都不一样、
     * 重启即让全部令牌失效」的一次性值，不能当成「已配置」。
     */
    private static final Pattern RANDOM_GENERATED_VALUE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** 与 {@code config/auth/jwt.yml} 里 {@code secret: "${JWT_SECRET:${random.uuid}}"} 对应的绑定键 */
    static final String JWT_SECRET_PROPERTY = "jairouter.security.jwt.secret";

    /**
     * 解析某个密钥类变量的「生效值」（issue #1247）。
     *
     * <p>此前这里直接读 {@code System.getenv(NAME)}，而 yaml 里的 {@code ${NAME:…}} 是由 Spring 的
     * {@code Environment} 展开的 —— 后者还包含系统属性与命令行参数（{@code -DNAME=…}、
     * {@code --NAME=…}），优先级都高于环境变量。于是「门闩检查的值」可能与「真正生效的值」是两个
     * 不同的东西：用 {@code -D} 传了强密钥，门闩仍读不到，生产环境直接拒绝启动。</p>
     *
     * <p>解析顺序与 Spring 的占位符解析保持一致：</p>
     * <ol>
     *   <li>{@code environment.getProperty(NAME)} —— 系统属性 / 命令行 / 环境变量三源合一</li>
     *   <li>{@code environment.getProperty(propertyKey)} —— 配置文件里直接写死（而非走占位符）时，
     *       真正生效的是这个属性；值为 {@code ${random.uuid}} 的产物时不算「已配置」</li>
     *   <li>{@code System.getenv(NAME)} —— {@code Environment} 不可用时的兜底</li>
     * </ol>
     *
     * @param environment Spring 环境；为 {@code null} 时退化为只读环境变量
     * @param envName     变量名，如 {@code JWT_SECRET}
     * @param propertyKey 可能被直接写死的配置键；没有这类键时传 {@code null}
     * @return 生效值；三处都没有则返回 {@code null}
     */
    static String resolveConfiguredValue(final Environment environment, final String envName,
                                         final String propertyKey) {
        if (environment != null) {
            final String fromEnvironment = environment.getProperty(envName);
            if (fromEnvironment != null && !fromEnvironment.isBlank()) {
                return fromEnvironment.trim();
            }
            if (propertyKey != null) {
                final String configured = environment.getProperty(propertyKey);
                if (configured != null && !configured.isBlank() && !isRandomGenerated(configured)) {
                    return configured.trim();
                }
            }
        }
        final String fromSystemEnv = System.getenv(envName);
        return (fromSystemEnv == null || fromSystemEnv.isBlank()) ? null : fromSystemEnv.trim();
    }

    private static boolean isRandomGenerated(final String value) {
        return RANDOM_GENERATED_VALUE.matcher(value.trim()).matches();
    }

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

        // #674：已移除的兼容变量名不再生效，必须明确告知，否则运维会以为设置成功
        warnRemovedEnvNames();

        // #802：管理员口令的打印已移到 JpaDatabaseInitializer#initializeJwtAccounts ——
        // 只有它知道「这次是否真的把账号写进了库」。留在这里会误导：库已有账号时，
        // 本次 YAML 里的随机口令并不会生效（AccountManager 库内优先），打印出来必然登不进。

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
     * 提醒仍然设置着已移除兼容变量名的部署（#674）。
     *
     * <p>只 warn 不拦截：这些名字现在一律不生效，若因此缺了真正需要的变量，
     * 会在上面的密钥/口令检查或配置绑定阶段以更具体的原因失败。</p>
     */
    private void warnRemovedEnvNames() {
        final List<String> present = removedEnvNamesPresent(System.getenv().keySet());
        if (present.isEmpty()) {
            return;
        }
        log.warn("⚠️  以下环境变量已不再生效（#674）：{}", present);
        log.warn("    JWT 密钥请用 JWT_SECRET，管理员口令请用 INITIAL_ADMIN_PASSWORD；"
                + "别名不再被读取，删掉它们以免误判。");
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
            // #1247：同上，Environment 覆盖 -D / --NAME 两个源
            String skipWarning = resolveConfiguredValue(environment, StartupSecurityGate.SKIP_ENV, null);
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

        // #674：只读 JWT_SECRET。此前这里先读 DEV_JWT_SECRET 且不分 profile，
        // 而配置绑定读的是 application-{dev,staging,prod}.yml 里的 ${JWT_SECRET:…}
        // ⇒ 两个变量同时存在时，「检查的密钥」与「实际生效的密钥」不是同一个。
        // #1247：读「生效值」而不是只读环境变量 —— ${JWT_SECRET:…} 的展开走的是同一个
        // Environment（系统属性 / 命令行参数 的优先级高于环境变量），只读 getenv 会漏掉它们。
        final String jwtSecret = resolveConfiguredValue(environment, "JWT_SECRET", JWT_SECRET_PROPERTY);

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

        // #674：只读 INITIAL_ADMIN_PASSWORD。ADMIN_PASSWORD 别名已移除——配置绑定
        // （config/auth/jwt.yml 的 password）从来没读过它，只设它会让门闩以为口令已设置，
        // 而实际生效的是随机生成、又不打印的值 ⇒ 控制台登不进去。
        // #1247：同上。accounts 是列表、口令还带 {noop} 前缀，本轮不做「配置文件直写」的识别，
        // 只把来源从 System.getenv 统一到 Environment（仍保留环境变量兜底）。
        final String adminPassword = resolveConfiguredValue(environment, "INITIAL_ADMIN_PASSWORD", null);

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

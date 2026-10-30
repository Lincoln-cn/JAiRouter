package org.unreal.modelrouter.auth.security.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * JWT配置类
 */
@Data
public class JwtConfig {
    /**
     * 是否启用JWT认证
     */
    private boolean enabled = false;

    /**
     * JWT令牌请求头名称
     */
    @NotBlank
    @Size(min = 1, max = 100)
    private String jwtHeader = "X-Jairouter-Token";

    /**
     * JWT签名密钥。
     *
     * <p>issue #821：这里刻意不加 {@code @Size(min = 32)}。带约束的校验会在上下文启动时由
     * Bean Validation 先于 {@link org.unreal.modelrouter.auth.security.config.StartupSecurityGate}
     * （{@code CommandLineRunner}）失败，于是 {@code JAIRouter_SKIP_AUTH_WARNING=true} 这个逃生舱
     * 还没运行就已被绕过 —— 用户明明是「知道自己在做什么、只想先起来」，却拿到一条无法跳过的
     * 通用校验错误。</p>
     *
     * <p>长度/强度判定保留在两处运行期入口：{@code SecurityConfigurationValidator}（配置保存与
     * 校验 API 的报错文案不变）与 {@code StartupSecretKeyChecker}（生产环境 fail-fast，且尊重
     * 逃生舱）。</p>
     */
    private String secret;

    /**
     * 签名算法
     */
    @NotBlank
    @Pattern(regexp = "^(HS256|HS384|HS512|RS256|RS384|RS512)$", message = "不支持的JWT算法")
    private String algorithm = "HS256";

    /**
     * 令牌过期时间（分钟）
     */
    @Min(1)
    @Max(1440)
    private long expirationMinutes = 60;

    /**
     * 刷新令牌过期时间（天）
     */
    @Min(1)
    @Max(30)
    private long refreshExpirationDays = 7;

    /**
     * 令牌发行者
     */
    @NotBlank
    @Size(min = 1, max = 100)
    private String issuer = "jairouter";

    /**
     * 是否启用令牌黑名单
     */
    private boolean blacklistEnabled = true;

    /**
     * 黑名单存储不可用时是否 fail-closed（默认 false，可用性优先，与历史行为一致）。
     *
     * <p>false：存储不可用/降级短路期间放行（返回“不在黑名单”），已撤销/登出的令牌
     * 在存储恢复前不会被拦截——这是既有取舍（issue #76）。true：同一场景下按
     * “在黑名单”处理并拒绝令牌（安全优先）。</p>
     */
    private boolean blacklistFailClosedWhenUnavailable = false;

    /**
     * 预配置的JWT账户列表
     */
    @Valid
    private List<JwtAccountProperties> accounts;
}

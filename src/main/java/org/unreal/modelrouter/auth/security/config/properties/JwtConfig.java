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
     *
     * <p>issue #1064：只接受 HMAC 家族。签发与校验两端一律使用 {@code Keys.hmacShaKeyFor(...)}
     * （见 {@code DefaultJwtTokenValidator#getSigningKey}、{@code AccountManager}），
     * 配成 {@code RS256} 曾既不生效也不报错 —— 运维会误以为已切到非对称密钥。
     * 若要真正支持非对称算法，需先实现 RSA 的公私钥加载与签名/验签分支，再放开这里的正则。</p>
     */
    @NotBlank
    @Pattern(regexp = "^(HS256|HS384|HS512)$", message = "不支持的JWT算法（当前仅支持 HS256/HS384/HS512）")
    private String algorithm = "HS256";

    /**
     * 令牌过期时间（分钟）
     */
    @Min(1)
    @Max(1440)
    private long expirationMinutes = 60;

    /**
     * 刷新令牌过期时间（天）：令牌过期后仍可换出新令牌的时间窗口。
     *
     * <p>issue #1073：这个窗口原本因解析阶段就校验 {@code exp} 而从未生效，
     * 修复后它真的会放行「已过期但未超窗口」的令牌。默认值随之从 7 天收紧到 1 天 ——
     * 窗口越长，泄露出去的过期令牌可被续期的暴露面越大，而「一小时内自动续期失败后
     * 还能补救一天」已覆盖客户端时钟漂移与短暂离线的常见场景。</p>
     */
    @Min(1)
    @Max(30)
    private long refreshExpirationDays = 1;

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

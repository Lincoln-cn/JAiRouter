package org.unreal.modelrouter.security.config.properties;

import jakarta.validation.constraints.*;
import lombok.Data;

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
    private String jwtHeader = "Jairouter_Token";

    /**
     * JWT签名密钥
     */
    @Size(min = 32, message = "JWT密钥长度至少32个字符")
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
}

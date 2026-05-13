package org.unreal.modelrouter.auth.security.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * JWT账户配置属性
 * 映射 application.yml 中的 jairouter.security.jwt.accounts 配置
 */
@Data
public class JwtAccountProperties {

    /**
     * 用户名
     */
    @NotBlank
    @Size(min = 1, max = 100)
    private String username;

    /**
     * 密码
     * 支持两种格式：
     * 1. {noop}明文密码 - 仅用于开发/测试环境，不推荐生产使用
     * 2. {bcrypt}$2a$10$... - BCrypt加密密码，生产环境推荐
     * 
     * 推荐使用环境变量注入：password: "${ADMIN_PASSWORD}"
     * 并使用密码加密工具生成安全的BCrypt哈希
     */
    @NotBlank
    private String password;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 是否启用
     */
    private boolean enabled = true;
}
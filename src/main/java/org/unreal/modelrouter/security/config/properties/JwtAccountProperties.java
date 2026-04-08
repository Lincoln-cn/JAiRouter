package org.unreal.modelrouter.security.config.properties;

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
     * 密码（支持 {noop} 前缀表示明文密码）
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
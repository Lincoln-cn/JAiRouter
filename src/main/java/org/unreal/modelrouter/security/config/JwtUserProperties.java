package org.unreal.modelrouter.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.dto.JwtAccountRequest;
import org.unreal.modelrouter.dto.JwtAccountResponse;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "jairouter.security.jwt")
public class JwtUserProperties {
    
    private boolean enabled = false;
    private List<UserAccount> accounts;
    
    @Data
    public static class UserAccount {
        private String username;
        private String password;
        private List<String> roles;
        private boolean enabled = true;
    }

    /**
     * 将请求对象转换为账户对象
     */
    public JwtUserProperties.UserAccount convertToAccount(JwtAccountRequest request) {
        JwtUserProperties.UserAccount account = new JwtUserProperties.UserAccount();
        account.setUsername(request.getUsername());
        account.setPassword(request.getPassword());
        account.setRoles(request.getRoles());
        account.setEnabled(request.isEnabled());
        return account;
    }

    /**
     * 将账户对象转换为响应对象
     */
    public JwtAccountResponse convertToResponse(JwtUserProperties.UserAccount account) {
        JwtAccountResponse response = new JwtAccountResponse();
        response.setUsername(account.getUsername());
        response.setRoles(account.getRoles());
        response.setEnabled(account.isEnabled());
        // 不返回密码信息
        return response;
    }
}
package org.unreal.modelrouter.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}
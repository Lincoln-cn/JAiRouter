package org.unreal.modelrouter.dto;

import java.util.List;

/**
 * JWT账户响应对象
 */
public class JwtAccountResponse {
    private String username;
    private List<String> roles;
    private boolean enabled;

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(final List<String> roles) {
        this.roles = roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}

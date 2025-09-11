package org.unreal.modelrouter.security.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API Key认证对象
 * 实现Spring Security的Authentication接口
 */
@Getter
@Setter
public class ApiKeyAuthentication implements Authentication {
    
    private final String principal;
    private final String credentials;
    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated = false;
    private Object details;
    
    /**
     * 构造函数 - 用于未认证的请求
     */
    public ApiKeyAuthentication(String apiKey) {
        this.principal = null;
        this.credentials = apiKey;
        this.authorities = List.of();
    }
    
    /**
     * 构造函数 - 用于已认证的请求
     */
    public ApiKeyAuthentication(String keyId, String apiKey, List<String> permissions) {
        this.principal = keyId;
        this.credentials = apiKey;
        this.authorities = permissions.stream()
                .map(permission -> new SimpleGrantedAuthority("ROLE_" + permission.toUpperCase()))
                .collect(Collectors.toList());
    }
    
    @Override
    public String getName() {
        return principal != null ? principal : "anonymous";
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public Object getCredentials() {
        return credentials;
    }
    
    @Override
    public Object getDetails() {
        return details;
    }
    
    @Override
    public Object getPrincipal() {
        return principal;
    }
    
    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }
    
    @Override
    public String toString() {
        return "ApiKeyAuthentication{" +
                "principal='" + principal + '\'' +
                ", authenticated=" + authenticated +
                ", authorities=" + authorities +
                '}';
    }
}
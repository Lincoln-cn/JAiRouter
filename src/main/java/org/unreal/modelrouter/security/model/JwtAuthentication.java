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
 * JWT令牌认证对象
 * 实现Spring Security的Authentication接口
 */
@Getter
@Setter
public class JwtAuthentication implements Authentication {
    
    private final String principal;
    private final String credentials;
    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated = false;
    private Object details;
    
    /**
     * 构造函数 - 用于未认证的请求
     */
    public JwtAuthentication(String token) {
        this.principal = null;
        this.credentials = token;
        this.authorities = List.of();
    }
    
    /**
     * 构造函数 - 用于已认证的请求
     */
    public JwtAuthentication(String subject, String token, List<String> roles) {
        this.principal = subject;
        this.credentials = token;
        this.authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
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
        return "JwtAuthentication{" +
                "principal='" + principal + '\'' +
                ", authenticated=" + authenticated +
                ", authorities=" + authorities +
                '}';
    }
}
package org.unreal.modelrouter.auth.security.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JWT令牌认证对象
 * 实现Spring Security的Authentication接口
 */
@Getter
@Setter
public final class JwtAuthentication implements Authentication {
    
    private final String principal;
    private final String credentials;
    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated = false;
    private Object details;
    
    /**
     * 构造函数 - 用于未认证的请求
     */
    public JwtAuthentication(final String token) {
        this.principal = null;
        this.credentials = token;
        this.authorities = List.of();
    }
    
    /**
     * 构造函数 - 用于已认证的请求（兼容旧调用，无权限码）
     */
    public JwtAuthentication(final String subject, final String token, final List<String> roles) {
        this(subject, token, roles, List.of());
    }

    /**
     * 构造函数 - 用于已认证的请求（v2.9.8 RBAC）
     *
     * <p>角色映射为 {@code ROLE_} 前缀的 authority（与 API-Key 的 ROLE_* 语义一致），
     * 权限码映射为无前缀的 authority（避免与角色 ROLE_* 冲突，供 URL 权限矩阵使用）。
     *
     * @param subject     用户名
     * @param token       原始 JWT
     * @param roles       角色列表（写入 ROLE_ 前缀 authority）
     * @param permissions 权限码列表（写入无前缀 authority）
     */
    public JwtAuthentication(final String subject, final String token,
                             final List<String> roles, final List<String> permissions) {
        this.principal = subject;
        this.credentials = token;
        this.authorities = buildAuthorities(roles, permissions);
    }

    /**
     * 构建 authority 集合：角色加 ROLE_ 前缀，权限码不加前缀
     */
    private static Collection<? extends GrantedAuthority> buildAuthorities(
            final List<String> roles, final List<String> permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            roles.stream()
                    .filter(role -> role != null && !role.trim().isEmpty())
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                    .forEach(authorities::add);
        }
        if (permissions != null) {
            permissions.stream()
                    .filter(permission -> permission != null && !permission.trim().isEmpty())
                    .map(permission -> new SimpleGrantedAuthority(permission.trim()))
                    .forEach(authorities::add);
        }
        return authorities;
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
    public void setAuthenticated(final boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }
    
    @Override
    public String toString() {
        return "JwtAuthentication{" 
        + "principal='" + principal + '\''
        + ", authenticated=" + authenticated
                + ", authorities=" + authorities
                + '}';
    }
}
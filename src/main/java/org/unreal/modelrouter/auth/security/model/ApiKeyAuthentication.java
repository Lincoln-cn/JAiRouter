package org.unreal.modelrouter.auth.security.model;

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
public final class ApiKeyAuthentication implements Authentication {
    
    private final String principal;
    private final String credentials;
    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated = false;
    private Object details;

    /**
     * 发起本次请求的客户端 IP（#693）。
     *
     * <p>由 {@link org.unreal.modelrouter.auth.filter.DefaultAuthenticationConverter} 从请求上下文解析后填入，
     * 供认证链执行 IP 白名单判定与审计；不经过请求上下文直接构造（如单测）时为 {@code null}。</p>
     */
    private String clientIp;

    /** 本次请求的路径，仅用于审计事件（#693）。 */
    private String endpoint;
    
    /**
     * 构造函数 - 用于未认证的请求
     */
    public ApiKeyAuthentication(final String apiKey) {
        this.principal = null;
        this.credentials = apiKey;
        this.authorities = List.of();
    }
    
    /**
     * 构造函数 - 携带请求上下文（#693）。
     *
     * <p>认证链需要客户端 IP 才能执行 IP 白名单判定；此前单参构造只带 Key 值，白名单恒收到 null。</p>
     *
     * @param apiKey   未认证的 API Key 原值
     * @param clientIp 客户端 IP
     * @param endpoint 请求路径
     */
    public ApiKeyAuthentication(final String apiKey, final String clientIp, final String endpoint) {
        this(apiKey);
        this.clientIp = clientIp;
        this.endpoint = endpoint;
    }

    /**
     * 构造函数 - 用于已认证的请求
     */
    public ApiKeyAuthentication(final String keyId, final String apiKey, final List<String> permissions) {
        this.principal = keyId;
        this.credentials = apiKey;
        this.authorities = permissions != null
                ? permissions.stream()
                    .map(permission -> new SimpleGrantedAuthority("ROLE_" + permission.toUpperCase()))
                    .collect(Collectors.toList())
                : List.of();
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
        return "ApiKeyAuthentication{" 
        + "principal='" + principal + '\''
        + ", authenticated=" + authenticated
                + ", authorities=" + authorities
                + '}';
    }
}